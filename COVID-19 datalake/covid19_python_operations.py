import logging

import boto3
from airflow import settings
from airflow.hooks.http_hook import HttpHook
from airflow.hooks.S3_hook import S3Hook
from airflow.models.connection import Connection


def check_data_exists(bucket, prefix, file):
    logging.info('checking whether data exists in s3')
    source_s3 = S3Hook(aws_conn_id='aws_default')

    if not source_s3.check_for_bucket(bucket):
        raise Exception('Bucket not found:', bucket)

    if not source_s3.check_for_prefix(bucket, prefix, "/"):
        raise Exception('Prefix not found:', prefix)

    if not source_s3.check_for_key(prefix+'/'+file, bucket):
        raise Exception('File not found:', file)

    return f'File found: bucket: {bucket}, prefix: {prefix}, file: {file}'



def copy_us_data_file(bucket_origin, prefix_origin, bucket_dest, key_dest):
    """Copy US data file to a local bucket.
    Since the name of the file which contains US data changes very
    often, it may cause error in runtime. Thus, the file should be
    copied to a local bucket to avoid incurring errors.
    This function expects only one JSON file in the given origin.
    args:
    bucket_origin (str): name of the bucket which holds the source file
    prefix_origin (str): prefix in the bucket where the source file is
    bucket_dest (str): name of the bucket to store the file
    key_dest (str): prefix/name of the file in the destination bucket
    """
    logging.info('Copying US data file ' \
                 f'FROM: {bucket_origin}/{prefix_origin}/*.json ' \
                 f'TO: {bucket_dest}/{key_dest}')

    s3_hook = S3Hook(aws_conn_id='aws_default')

    # Get the object for the data source
    s3_obj = s3_hook.get_wildcard_key(prefix_origin+'/*.json', bucket_origin,
                                      delimiter='/')

    # Copy data file into s3 bucket
    s3_hook.load_file_obj(s3_obj.get()['Body'],
                          key=key_dest,
                          bucket_name=bucket_dest,
                          replace=True)

    logging.info('Data copy finished.')


def copy_brazil_data_file(origin_host, origin_filepath,
                          dest_bucket, dest_key):
    """Copy Brazil data file to a local bucket.
    Copy the source file which contains detailed data about Brazil to
    an AWS S3 bucket to make it available to AWS EMR.
    args:
    origin_host (str): host where the source file is in
    origin_filepath (str): full path to the file in the host
    dest_bucket (str): name of the bucket to store the file
    dest_key (str): prefix/name of the file in the destination bucket
    """
    logging.info('Copying Brazil data file ' \
                f'FROM: http://{origin_host}/{origin_filepath} ' \
                f'TO: s3://{dest_bucket}/{dest_key}')

    # Create a connection to the source server
    conn = Connection(
          conn_id='http_conn_brasilio',
          conn_type='http',
          host=origin_host,
          port=80
    ) #create a connection object
    session = settings.Session() # get the session
    session.add(conn)
    session.commit()

    # Get the data file
    http_hook = HttpHook(method='GET', http_conn_id='http_conn_brasilio')
    response_br_data = http_hook.run(origin_filepath)

    # Store data file into s3 bucket
    s3_hook = S3Hook(aws_conn_id='aws_default')
    s3_hook.load_bytes(response_br_data.content,
                       dest_key,
                       bucket_name=dest_bucket,
                       replace=True)

    logging.info('Data copy finished.')


def stop_airflow_containers(cluster):
    ecs = boto3.client('ecs')
    task_list = ecs.list_tasks(cluster=cluster)
    for task_arn in task_list['taskArns']:
        print('stopping task:', task_arn)
        ecs.stop_task(cluster=cluster, task=task_arn)
