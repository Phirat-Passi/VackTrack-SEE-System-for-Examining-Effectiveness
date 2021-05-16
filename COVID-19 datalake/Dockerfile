## Build Docker image
# sudo docker build -t silviomori/covid19-datalake:0.0.1 .
#
## Push image to DockerHub
# sudo docker push silviomori/covid19-datalake:0.0.1
#
# Run Docker image from a machine other than in a AWS ECS cluster
# sudo docker run -v ~/.aws:/usr/local/airflow/.aws \
#   -i -p 8080:8080 silviomori/covid19-datalake:0.0.1

FROM puckel/docker-airflow
LABEL maintainer="silviomori@gmail.com"

ADD . /usr/local/airflow/

RUN pip install boto3
