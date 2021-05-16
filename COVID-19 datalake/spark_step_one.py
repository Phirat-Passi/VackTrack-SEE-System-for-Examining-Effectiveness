mport pyspark.sql.functions as F
from pyspark.sql import SparkSession


def create_spark_session():
    """Create a Spark session."""
    spark = SparkSession \
        .builder \
        .config("spark.jars.packages", "org.apache.hadoop:hadoop-aws:2.7.0") \
        .getOrCreate()
    return spark


def step_one():
    spark = create_spark_session()
    
    covid_global_data = "s3a://covid19-lake/archived/tableau-jhu/csv/" \
                       +"COVID-19-Cases.csv"

    global_data_df = spark.read.load(covid_global_data, \
        format="csv", sep=",", inferSchema="true", header="true")
    global_data_df = global_data_df.dropDuplicates()
    global_data_df.createOrReplaceTempView("global_data")

    date_df = global_data_df.filter("country_region = 'Brazil'")
    date_df = date_df.select("Date")
    date_df = date_df.dropDuplicates()

    split_date = F.split(date_df["Date"],'/')
    date_df = date_df.withColumn('Month', split_date.getItem(0))
    date_df = date_df.withColumn('Day', split_date.getItem(1))
    date_df = date_df.withColumn('Year', split_date.getItem(2))
    date_df = date_df.select(date_df.Date, \
            F.lpad(date_df.Month,2,'0').alias('Month'), \
            F.lpad(date_df.Day,2,'0').alias('Day'), \
            F.lpad(date_df.Year,4,'0').alias('Year'))
    date_df = date_df.select(date_df.Date, \
            date_df.Month, \
            date_df.Day, \
            date_df.Year, \
            F.to_date(F.concat_ws('-',date_df.Month, date_df.Day, date_df.Year), \
            'MM-dd-yyyy').alias('Date_format'))
    date_df = date_df.select(date_df.Date, \
            date_df.Date_format, \
            date_df.Year, \
            F.weekofyear(date_df.Date_format).alias('Week'), \
            date_df.Month, \
            date_df.Day, \
            F.dayofweek(date_df.Date_format).alias('Week_Day'))
    date_df.show(2)


if __name__ == "__main__":
    step_one()
