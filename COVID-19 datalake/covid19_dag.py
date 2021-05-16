from airflow import DAG
from airflow.operators.dummy_operator import DummyOperator
from airflow.operators.python_operator import PythonOperator
from airflow.contrib.operators.emr_add_steps_operator \
    import EmrAddStepsOperator
from airflow.contrib.operators.emr_create_job_flow_operator \
    import EmrCreateJobFlowOperator
from airflow.contrib.operators.emr_terminate_job_flow_operator \
    import EmrTerminateJobFlowOperator
from airflow.contrib.sensors.emr_step_sensor import EmrStepSensor

from covid19_dag_settings import (default_args, emr_settings,
    spark_step_one_definition)
from covid19_python_operations import (check_data_exists,
    copy_us_data_file, copy_brazil_data_file, stop_airflow_containers)


# Define a DAG
dag = DAG(
    'covid19_dag',
    default_args=default_args,
    max_active_runs=1,
    description='Load and transform data in Redshift with Airflow',
    schedule_interval='@once',
    is_paused_upon_creation=False)


# Dummy operator just to mark the beginning of the pipeline
starting_point = DummyOperator(task_id='starting_point', dag=dag)


# Check whether the world data file exists 
check_world_data_exists_task = PythonOperator(
    task_id='check_world_data_exists',
    python_callable=check_data_exists,
    op_kwargs={'bucket': 'covid19-lake',
               'prefix': 'archived/tableau-jhu/csv',
               'file': 'COVID-19-Cases.csv'},
    provide_context=False,
    dag=dag)


# Copy detailed US data file to a local bucket,
# in case it changes the name during running time
copy_us_data_file_task = PythonOperator(
    task_id='copy_us_data_file',
    python_callable=copy_us_data_file,
    op_kwargs={'bucket_origin': 'covid19-lake',
               'prefix_origin': 'enigma-aggregation/json/us_states',
               'bucket_dest': 'covid19-datalake-silviomori',
               'key_dest': 'raw-data/enigma_agg_usa.json'},
    dag=dag)


# Copy detailed Brazil data file to a local bucket.
copy_brazil_data_file_task = PythonOperator(
    task_id='copy_brazil_data_file',
    python_callable=copy_brazil_data_file,
    op_kwargs={'origin_host': 'data.brasil.io',
               'origin_filepath': 'dataset/covid19/caso_full.csv.gz',
               'dest_bucket': 'covid19-datalake-silviomori',
               'dest_key': 'raw-data/COVID-19-Brazil.csv.gz'},
    dag=dag)


# Spin up an AWS EMR cluster
create_emr_cluster_task = EmrCreateJobFlowOperator(
    task_id='create_emr_cluster',
    aws_conn_id='aws_default',
    emr_conn_id='emr_default',
    job_flow_overrides=emr_settings,
    dag=dag)


# Add task to AWS EMR cluster
add_spark_step_one_task = EmrAddStepsOperator(
    task_id='add_spark_step_one',
    job_flow_id="{{task_instance.xcom_pull(" \
                "      'create_emr_cluster'," \
                "      key='return_value')}}",
    steps=spark_step_one_definition,
    dag=dag)


# Wait until task terminates
watch_spark_step_one_task = EmrStepSensor(
    task_id='watch_spark_step_one',
    job_flow_id="{{task_instance.xcom_pull(" \
                "      'create_emr_cluster'," \
                "      key='return_value')}}",
    step_id="{{task_instance.xcom_pull(" \
            "      'add_spark_step_one'," \
            "      key='return_value')[0]}}",
    dag=dag)


# Spin down an AWS EMR cluster
terminate_emr_cluster_task = EmrTerminateJobFlowOperator(
    task_id='terminate_emr_cluster',
    job_flow_id="{{task_instance.xcom_pull(" \
                "      'create_emr_cluster'," \
                "      key='return_value')}}",
    trigger_rule="all_done",
    dag=dag)


# Stop any container running on covid19-ecs-cluster
stop_airflow_containers_task = PythonOperator(
    task_id='stop_airflow_containers',
    python_callable=stop_airflow_containers,
    op_kwargs={'cluster': 'covid19-ecs-cluster'},
    provide_context=False,
    dag=dag)


# Setting up dependencies
starting_point >> [check_world_data_exists_task,
                   copy_us_data_file_task,
                   copy_brazil_data_file_task] >> \
create_emr_cluster_task >> add_spark_step_one_task
add_spark_step_one_task >> watch_spark_step_one_task
watch_spark_step_one_task >> terminate_emr_cluster_task
terminate_emr_cluster_task >> stop_airflow_containers_task
