## Setup

To properly execute full test suite, the AWS account is required. Then configure services:

1. Initialize the `~/.aws/credentials` file.

2. Create a AWS S3 bucket. Example using the [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-services-s3-commands.html#using-s3-commands-managing-buckets-creating):
```shell
aws s3 mb s3://<name_of_the_bucket>
```

5. Configure the ENV variables that are required to run the tests:
```shell
export AWS_TEST_BUCKET_NAME=<name_of_the_bucket>
```

6. Run the tests:
```shell
./gradlew test
```
