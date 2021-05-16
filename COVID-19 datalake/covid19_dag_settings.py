rom datetime import datetime, timedelta


# Define default_args to be passed in to operators
default_args = {
    'owner': 'airflow',
    'depends_on_past': False,
    'start_date': datetime.now(),
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 3,
    'retry_delay': timedelta(minutes=5),
}


# Define the EMR settings
emr_settings = {
   "Applications": [
      {
         "Name": "Spark"
      }
   ],
   "EbsRootVolumeSize": 10,
   "Instances": { 
      "Ec2SubnetId": "subnet-49403b67",
      "EmrManagedMasterSecurityGroup": "sg-0c9ef433da846c7bf",
      "EmrManagedSlaveSecurityGroup": "sg-0bf779a7d90a8d321",
      "InstanceGroups": [
         { 
            "EbsConfiguration": { 
               "EbsBlockDeviceConfigs": [ 
                  { 
                     "VolumeSpecification": { 
                        "SizeInGB": 32,
                        "VolumeType": "gp2"
                     },
                     "VolumesPerInstance": 2
                  }
               ],
            },
            "InstanceCount": 1,
            "InstanceRole": "MASTER",
            "InstanceType": "m5.xlarge",
            "Name": "Master node"
         },
         { 
            "EbsConfiguration": { 
               "EbsBlockDeviceConfigs": [ 
                  { 
                     "VolumeSpecification": { 
                        "SizeInGB": 32,
                        "VolumeType": "gp2"
                     },
                     "VolumesPerInstance": 2
                  }
               ],
            },
            "InstanceCount": 2,
            "InstanceRole": "CORE",
            "InstanceType": "m5.xlarge",
            "Name": "Core node"
         }
      ],
      "KeepJobFlowAliveWhenNoSteps": True,
   },
   "JobFlowRole": "EMR_EC2_DefaultRole",
   "LogUri": "s3n://aws-logs-042884995395-us-east-1/elasticmapreduce/",
   "Name": "covid19-emr-cluster",
   "ReleaseLabel": "emr-5.30.0",
   "ServiceRole": "EMR_DefaultRole",
   "VisibleToAllUsers": True
}


# Step one definition
spark_step_one_path = "https://github.com/silviomori/covid19-datalake/raw/" \
                      "master/dags/spark_steps/spark_step_one.py"

spark_step_one_definition = [{
    "Name": "Spark Step One",
    "ActionOnFailure": "CONTINUE",
    "HadoopJarStep": {
        "Jar":"command-runner.jar",
        "Args": [
            "spark-submit",
            "--deploy-mode", "client",
            "--py-files", spark_step_one_path,
            spark_step_one_path
            ]
        }
    }]
