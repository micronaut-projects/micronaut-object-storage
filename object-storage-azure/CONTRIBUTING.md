## Setup

To properly execute the cloud test suite, a Microsoft Azure account is required. Then configure services:

1. Create a resource group. Example using [Azure CLI](https://docs.microsoft.com/en-us/azure/azure-resource-manager/management/manage-resource-groups-cli):
```shell
az group create --name <name_of_resource_group> --location <location_of_resource_group>
```

`<location_of_resource_group>` for example `eastus`

2. Create a storage account. Example using the [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/storage/account?view=azure-cli-latest#az-storage-account-create):
```shell
az storage account create -n <name_of_storage_account> -g <name_of_resource_group>
```

3. Create the service principal to access the azure services. Example using the [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/ad/sp?view=azure-cli-latest#az-ad-sp-create-for-rbac)
```shell
az ad sp create-for-rbac -n micronaut-test-sp --role ba92f5b4-2d11-453d-a403-e96b0029c9fe --scopes /subscriptions/<subscriptionID>/resourceGroups/<name_of_resource_group>/providers/Microsoft.Storage/storageAccounts/<name_of_storage_account>
```

Note that the role id `ba92f5b4-2d11-453d-a403-e96b0029c9fe` represents the [Storage Blob Data Contributor](https://docs.microsoft.com/en-us/azure/role-based-access-control/built-in-roles#storage-blob-data-contributor) role. The example output is illustrated below. Make sure to note the `name`, `password` and `tenant` properties.
```json
{
  "appId": "REDACTED",
  "displayName": "micronaut-test-sp",
  "password": "REDACTED",
  "tenant": "REDACTED"
}
```

4. Configure the ENV variables that are required to run the tests:
```shell
export AZURE_TEST_STORAGE_ACCOUNT_ENDPOINT=https://<name_of_storage_account>.blob.core.windows.net
export AZURE_CLIENT_ID=<appId_from_the_service_principal>
export AZURE_CLIENT_SECRET=<password_from_the_service_principal>
export AZURE_TENANT_ID=<tenant_from_the_service_principal>
```

5. Run the tests:
```shell
./gradlew :micronaut-object-storage-azure:test
```
