## Setup

To properly execute the cloud test suite, an Oracle Cloud account is required. Then configure services:

1. Create the API key [configuration file](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#SDK_and_CLI_Configuration_File) under the target tenancy:

2. Create the bucket. Example using the [Oracle CLI](https://docs.oracle.com/en-us/iaas/tools/oci-cli/2.9.1/oci_cli_docs/cmdref/os/bucket/create.html)
```shell
oci os bucket create --compartment-id <compartment_id> --name <bucket_name>
```

3. Configure the ENV variables that are required to run the tests:
```shell
export ORACLE_CLOUD_TEST_BUCKET_NAME=<bucket_name>
export ORACLE_CLOUD_TEST_NAMESPACE=<tenancy_name>
```

4Run the tests:
```shell
./gradlew test
```
