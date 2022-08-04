## Setup

To properly execute cloud test suite, the Google Cloud account is required. Then configure services:

1. Authenticate as [a service account](https://cloud.google.com/docs/authentication/production):
```shell
gcloud auth application-default login
```

2. Create a project. Example using the [Google Cloud CLI](https://cloud.google.com/sdk/gcloud/reference/projects/create):
```shell
gcloud projects create <project_id>
```

3. Configure the ENV variables that are required to run the tests:
```shell
export GCLOUD_TEST_PROJECT_ID=<project_id>
```

4. Run the tests:
```shell
./gradlew :object-storage-gcp:test
```
