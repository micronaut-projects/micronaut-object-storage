## Setup

To properly execute full test suite, the Google Cloud account is required. Then configure services:

1. Authenticate as [a service account](https://cloud.google.com/docs/authentication/production):
```shell
gcloud auth application-default login
```

2. Create a project. Example using the [Google Cloud CLI](https://cloud.google.com/sdk/gcloud/reference/projects/create):
```shell
gcloud projects create <project_id>
```

3. Create the bucket. Example using the [gsutil](https://cloud.google.com/storage/docs/creating-buckets#storage-create-bucket-gsutil)
```shell
gsutil mb -p <project_id> gs://<bucket_name>
```

5. Configure the ENV variables that are required to run the tests:
```shell
export GCLOUD_TEST_PROJECT_ID=<project_id>
export GCLOUD_TEST_BUCKET_NAME=<bucket_name>
```

6. Run the tests:
```shell
./gradlew test
```
