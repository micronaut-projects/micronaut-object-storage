/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.objectstorage.metadata.jdbc.postgres

import io.micronaut.objectstorage.metadata.StorageDescriptor
import io.micronaut.objectstorage.metadata.StorageObjectMetadataQuery
import io.micronaut.objectstorage.request.UploadRequest

class MetadataJdbcPostgresSmokeSpec extends AbstractPostgresMetadataJdbcSpec {

    void 'postgres smoke verifies schema creation upload reconciliation exact lookup prefix query and qualifier portability'() {
        expect:
        tableCount('storage_container_metadata') == 1
        tableCount('storage_container_metadata_attr') == 1
        tableCount('storage_object_metadata') == 1
        tableCount('storage_object_metadata_attr') == 1
        tableCount('storage_metadata_outbox') == 1

        when:
        def januaryUpload = UploadRequest.fromBytes('jan'.bytes, 'reports/2026/03/summary-a.txt', 'text/plain')
        januaryUpload.metadata = [owner: 'tenant-a', source: 'postgres-smoke']
        operations.upload(januaryUpload)
        reconcileAll()
        def exactLookup = metadataOperations.findObject(descriptor, 'reports/2026/03/summary-a.txt')
        def prefixQuery = metadataOperations.listObjects(StorageObjectMetadataQuery.all()
            .withStorageName('default')
            .withLogicalContainer('default')
            .withObjectKeyPrefix('reports/2026/03/'))

        then:
        exactLookup.present
        exactLookup.get().objectKey == 'reports/2026/03/summary-a.txt'
        prefixQuery*.objectKey == ['reports/2026/03/summary-a.txt']
        exactLookup.get().storageName == 'default'
        exactLookup.get().providerId == 'local'
        exactLookup.get().logicalContainer == 'default'

        when:
        tenantResolver.tenantId = 'tenant-b'

        then:
        !metadataOperations.findObject(descriptor, 'reports/2026/03/summary-a.txt').present
        metadataOperations.listObjects(StorageObjectMetadataQuery.all()
            .withStorageName('default')
            .withLogicalContainer('default')
            .withObjectKeyPrefix('reports/2026/03/')).isEmpty()
    }
}
