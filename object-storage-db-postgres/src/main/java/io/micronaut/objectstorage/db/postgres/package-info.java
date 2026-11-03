/*
 * Copyright 2017-2026 original authors
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
/**
 * PostgreSQL support for the database object storage: object content in Large Objects, streamed in windows, with
 * copies and multipart uploads assembled inside the database, and optional {@code LISTEN/NOTIFY} change
 * notifications.
 *
 * <p>Add this module next to {@code micronaut-object-storage-db}; storages on a PostgreSQL datasource then use it by
 * default.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
package io.micronaut.objectstorage.db.postgres;
