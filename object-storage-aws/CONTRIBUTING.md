## Setup

To properly execute the cloud test suite, an AWS account is required. Then configure the following environment
variables:

```shell
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_REGION=...
```

Run the tests:
```shell
./gradlew :object-storage-aws:test
```
