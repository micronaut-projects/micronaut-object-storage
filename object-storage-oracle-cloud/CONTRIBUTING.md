## Setup

To properly execute the cloud test suite, an Oracle Cloud account is required. Then configure services:

1. Create the API key [configuration file](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm#SDK_and_CLI_Configuration_File) under the target tenancy:

2. Configure the ENV variables that are required to run the tests:
```shell
export ORACLE_CLOUD_TEST_COMPARTMENT_ID=<compartment_ociid>
export ORACLE_CLOUD_TEST_NAMESPACE=<tenancy_name>
```

3. Run the tests:
```shell
./gradlew :micronaut-object-storage-oracle-cloud:test
```
