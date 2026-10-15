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
package io.micronaut.objectstorage.db;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.objectstorage.ObjectStorageException;
import io.micronaut.objectstorage.configuration.ToggeableCondition;
import io.micronaut.objectstorage.db.internal.DbObjectSql;
import io.micronaut.objectstorage.db.internal.DbStorageRuntime;
import io.micronaut.objectstorage.db.internal.DigestingInputStream;
import io.micronaut.objectstorage.db.internal.JsonMaps;
import io.micronaut.objectstorage.db.spi.DbContentStore;
import io.micronaut.objectstorage.db.spi.DbObjectRef;
import io.micronaut.objectstorage.db.spi.DbPartRef;
import io.micronaut.objectstorage.db.spi.DbSqlDialect;
import io.micronaut.objectstorage.db.spi.DbStorageContext;
import io.micronaut.objectstorage.db.spi.DbTableMapping;
import io.micronaut.objectstorage.multipart.AbortMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CompleteMultipartUploadResponse;
import io.micronaut.objectstorage.multipart.CreateMultipartUploadRequest;
import io.micronaut.objectstorage.multipart.CreateMultipartUploadResponse;
import io.micronaut.objectstorage.multipart.ListMultipartPartsRequest;
import io.micronaut.objectstorage.multipart.ListMultipartPartsResponse;
import io.micronaut.objectstorage.multipart.MultipartObjectStorageOperations;
import io.micronaut.objectstorage.multipart.MultipartPart;
import io.micronaut.objectstorage.multipart.MultipartUploadHandle;
import io.micronaut.objectstorage.multipart.UploadPartRequest;
import io.micronaut.objectstorage.multipart.UploadPartResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multipart uploads of the database storage. Parts are staged in the {@code MN_OS_UPLOAD_PART} table and their
 * content in the layout of the storage; completing an upload builds the object server side (chunk rows are
 * re-linked, LOBs are appended) and replaces any existing object in one transaction.
 *
 * <p>The entity tag of a completed upload follows the S3 convention: the MD5 of the concatenated part digests,
 * followed by a dash and the number of parts.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@EachBean(DbStorageConfiguration.class)
@Requires(condition = ToggeableCondition.class)
@Requires(beans = DbStorageConfiguration.class)
@Primary
public class DbMultipartOperations implements MultipartObjectStorageOperations<DbMultipartUpload, DbUploadPart, DbStorageObject> {

    private static final String UPLOAD = DbTableMapping.UPLOAD_TABLE;
    private static final String PART = DbTableMapping.UPLOAD_PART_TABLE;

    private final DbStorageRuntime runtime;

    DbMultipartOperations(@Parameter DbStorageRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    @NonNull
    public CreateMultipartUploadResponse<DbMultipartUpload> createMultipartUpload(@NonNull CreateMultipartUploadRequest request) {
        runtime.requireBuiltInTables("Multipart uploads");
        DbStorageContext context = runtime.context();
        String uploadId = UUID.randomUUID().toString();
        String bucket = runtime.objects().bucket();
        Instant now = Instant.now();
        runtime.transactions().write(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + UPLOAD
                + " (UPLOAD_ID, BUCKET, OBJECT_KEY, CONTENT_TYPE, METADATA, CREATED_AT) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, uploadId);
                ps.setString(2, bucket);
                ps.setString(3, request.getKey());
                setNullableString(ps, 4, request.getContentType().orElse(null));
                context.sql().bindJson(ps, 5, JsonMaps.write(request.getMetadata()));
                context.sql().bindTimestamp(ps, 6, now);
                ps.executeUpdate();
            }
            return null;
        });
        return CreateMultipartUploadResponse.of(
            new MultipartUploadHandle(request.getKey(), uploadId),
            new DbMultipartUpload(uploadId, bucket, request.getKey(), now)
        );
    }

    @Override
    @NonNull
    public UploadPartResponse<DbUploadPart> uploadPart(@NonNull UploadPartRequest request) {
        runtime.requireBuiltInTables("Multipart uploads");
        DbStorageContext context = runtime.context();
        DbContentStore store = runtime.store();
        MultipartUploadHandle handle = request.getUpload();
        int partNumber = request.getPartNumber();
        try (InputStream in = request.getUploadRequest().getInputStream()) {
            DbUploadPart part = runtime.transactions().write(connection -> {
                findUpload(connection, handle);
                List<DbPartRef> existing = findParts(connection, handle.getUploadId(), partNumber);
                if (!existing.isEmpty()) {
                    store.deleteParts(context, connection, existing);
                    deletePartRows(connection, handle.getUploadId(), partNumber);
                }
                DbPartRef ref = new DbPartRef(handle.getUploadId(), partNumber, UUID.randomUUID().toString(), 0);
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + PART
                    + " (UPLOAD_ID, PART_NUMBER, CONTENT_ID, CREATED_AT) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, ref.uploadId());
                    ps.setInt(2, partNumber);
                    ps.setString(3, ref.contentId());
                    context.sql().bindTimestamp(ps, 4, Instant.now());
                    ps.executeUpdate();
                }
                DigestingInputStream content = new DigestingInputStream(in, context.configuration().getMaxObjectSize());
                store.writePart(context, connection, ref, content);
                content.drain();
                String etag = DigestingInputStream.toHex(content.getDigest());
                try (PreparedStatement ps = connection.prepareStatement("UPDATE " + PART
                    + " SET ETAG = ?, PART_SIZE = ? WHERE UPLOAD_ID = ? AND PART_NUMBER = ?")) {
                    ps.setString(1, etag);
                    ps.setLong(2, content.getCount());
                    ps.setString(3, ref.uploadId());
                    ps.setInt(4, partNumber);
                    ps.executeUpdate();
                }
                return new DbUploadPart(ref.uploadId(), partNumber, content.getCount(), etag);
            });
            return UploadPartResponse.of(new MultipartPart(partNumber, part.etag(), part.size()), part);
        } catch (IOException e) {
            throw new ObjectStorageException("Error closing the part stream of upload " + handle.getUploadId(), e);
        }
    }

    @Override
    @NonNull
    public ListMultipartPartsResponse listParts(@NonNull ListMultipartPartsRequest request) {
        runtime.requireBuiltInTables("Multipart uploads");
        DbSqlDialect sql = runtime.context().sql();
        MultipartUploadHandle handle = request.getUpload();
        int marker = request.getContinuationToken().map(DbMultipartOperations::decodeToken).orElse(0);
        int pageSize = request.getPageSize();
        List<MultipartPart> parts = runtime.transactions().read(connection -> {
            findUpload(connection, handle);
            List<MultipartPart> found = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql.limit("SELECT PART_NUMBER, ETAG, PART_SIZE FROM " + PART
                + " WHERE UPLOAD_ID = ? AND PART_NUMBER > ? AND ETAG IS NOT NULL ORDER BY PART_NUMBER", pageSize + 1))) {
                ps.setString(1, handle.getUploadId());
                ps.setInt(2, marker);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        found.add(new MultipartPart(rs.getInt(1), rs.getString(2), rs.getLong(3)));
                    }
                }
            }
            return found;
        });
        if (parts.size() <= pageSize) {
            return new ListMultipartPartsResponse(parts);
        }
        List<MultipartPart> page = parts.subList(0, pageSize);
        return new ListMultipartPartsResponse(page, Integer.toString(page.get(page.size() - 1).getPartNumber()));
    }

    @Override
    @NonNull
    public CompleteMultipartUploadResponse<DbStorageObject> completeMultipartUpload(@NonNull CompleteMultipartUploadRequest request) {
        runtime.requireBuiltInTables("Multipart uploads");
        DbStorageContext context = runtime.context();
        DbContentStore store = runtime.store();
        DbObjectSql objects = runtime.objects();
        MultipartUploadHandle handle = request.getUpload();
        DbStorageObject stored = runtime.transactions().write(connection -> {
            UploadRow upload = findUpload(connection, handle);
            Map<Integer, StoredPart> uploaded = new LinkedHashMap<>();
            for (StoredPart part : findStoredParts(connection, handle.getUploadId())) {
                uploaded.put(part.ref().partNumber(), part);
            }
            List<DbPartRef> manifest = new ArrayList<>();
            MessageDigest digest = DigestingInputStream.newMd5();
            long length = 0;
            for (MultipartPart part : request.getParts()) {
                StoredPart storedPart = uploaded.remove(part.getPartNumber());
                if (storedPart == null || !storedPart.etag().equals(part.getETag())) {
                    throw new ObjectStorageException("Part " + part.getPartNumber() + " of multipart upload "
                        + handle.getUploadId() + " was not uploaded or has a different ETag");
                }
                manifest.add(storedPart.ref());
                digest.update(HexFormat.of().parseHex(storedPart.etag()));
                length += storedPart.ref().size();
            }
            Long maxObjectSize = context.configuration().getMaxObjectSize();
            if (maxObjectSize != null && length > maxObjectSize) {
                throw new ObjectStorageException("Database storage '" + context.storageName()
                    + "': the object exceeds the maximum object size of " + maxObjectSize + " bytes");
            }
            String etag = DigestingInputStream.toHex(digest.digest()) + "-" + manifest.size();
            Instant now = Instant.now();
            var existing = objects.find(connection, handle.getKey());
            if (existing.isPresent()) {
                store.deleteContent(context, connection, existing.get().ref());
                objects.delete(connection, existing.get().ref());
            }
            DbObjectRef object = objects.insert(connection, handle.getKey(), upload.contentType(), upload.metadataJson(),
                now, Map.of(), store, null);
            store.assemble(context, connection, object, manifest);
            objects.updateContentInfo(connection, object, length, etag);
            List<DbPartRef> all = new ArrayList<>(manifest);
            uploaded.values().forEach(part -> all.add(part.ref()));
            deleteUpload(connection, context, store, handle.getUploadId(), all);
            return new DbStorageObject(object.bucket(), handle.getKey(), length, upload.contentType(), etag, now);
        });
        return CompleteMultipartUploadResponse.of(handle, stored.etag(), stored);
    }

    @Override
    public void abortMultipartUpload(@NonNull AbortMultipartUploadRequest request) {
        runtime.requireBuiltInTables("Multipart uploads");
        DbStorageContext context = runtime.context();
        DbContentStore store = runtime.store();
        MultipartUploadHandle handle = request.getUpload();
        runtime.transactions().write(connection -> {
            UploadRow upload = findUploadRow(connection, handle.getUploadId());
            if (upload == null) {
                return null;
            }
            checkKey(handle, upload);
            List<DbPartRef> parts = new ArrayList<>();
            findStoredParts(connection, handle.getUploadId()).forEach(part -> parts.add(part.ref()));
            deleteUpload(connection, context, store, handle.getUploadId(), parts);
            return null;
        });
    }

    private void deleteUpload(Connection connection, DbStorageContext context, DbContentStore store, String uploadId,
                              List<DbPartRef> parts) throws SQLException {
        store.deleteParts(context, connection, parts);
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + PART + " WHERE UPLOAD_ID = ?")) {
            ps.setString(1, uploadId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + UPLOAD + " WHERE UPLOAD_ID = ?")) {
            ps.setString(1, uploadId);
            ps.executeUpdate();
        }
    }

    private UploadRow findUpload(Connection connection, MultipartUploadHandle handle) throws SQLException {
        UploadRow upload = findUploadRow(connection, handle.getUploadId());
        if (upload == null) {
            throw new ObjectStorageException("Multipart upload " + handle.getUploadId() + " does not exist in database storage '"
                + runtime.configuration().getName() + "'");
        }
        checkKey(handle, upload);
        return upload;
    }

    private void checkKey(MultipartUploadHandle handle, UploadRow upload) {
        if (!upload.key().equals(handle.getKey()) || !upload.bucket().equals(runtime.objects().bucket())) {
            throw new ObjectStorageException("Multipart upload " + handle.getUploadId() + " does not target key " + handle.getKey());
        }
    }

    @Nullable
    private UploadRow findUploadRow(Connection connection, String uploadId) throws SQLException {
        DbSqlDialect sql = runtime.context().sql();
        try (PreparedStatement ps = connection.prepareStatement("SELECT BUCKET, OBJECT_KEY, CONTENT_TYPE, "
            + sql.selectJson("METADATA", true) + " FROM " + UPLOAD + " WHERE UPLOAD_ID = ?")) {
            ps.setString(1, uploadId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new UploadRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
            }
        }
    }

    private List<StoredPart> findStoredParts(Connection connection, String uploadId) throws SQLException {
        List<StoredPart> parts = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT PART_NUMBER, CONTENT_ID, PART_SIZE, ETAG FROM " + PART
            + " WHERE UPLOAD_ID = ? ORDER BY PART_NUMBER")) {
            ps.setString(1, uploadId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String etag = rs.getString(4);
                    parts.add(new StoredPart(new DbPartRef(uploadId, rs.getInt(1), rs.getString(2), rs.getLong(3)), etag == null ? "" : etag));
                }
            }
        }
        return parts;
    }

    private List<DbPartRef> findParts(Connection connection, String uploadId, int partNumber) throws SQLException {
        List<DbPartRef> parts = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT CONTENT_ID, PART_SIZE FROM " + PART
            + " WHERE UPLOAD_ID = ? AND PART_NUMBER = ?")) {
            ps.setString(1, uploadId);
            ps.setInt(2, partNumber);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    parts.add(new DbPartRef(uploadId, partNumber, rs.getString(1), rs.getLong(2)));
                }
            }
        }
        return parts;
    }

    private static void deletePartRows(Connection connection, String uploadId, int partNumber) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + PART + " WHERE UPLOAD_ID = ? AND PART_NUMBER = ?")) {
            ps.setString(1, uploadId);
            ps.setInt(2, partNumber);
            ps.executeUpdate();
        }
    }

    private static int decodeToken(String token) {
        try {
            int marker = Integer.parseInt(token);
            if (marker < 0) {
                throw new NumberFormatException();
            }
            return marker;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid multipart continuation token: " + token);
        }
    }

    private static void setNullableString(PreparedStatement ps, int index, @Nullable String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private record UploadRow(String bucket, String key, @Nullable String contentType, @Nullable String metadataJson) {
    }

    private record StoredPart(DbPartRef ref, String etag) {
    }
}
