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
package io.micronaut.objectstorage.db.spi;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.query.builder.sql.Dialect;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * The SQL differences between databases that the storage needs: row limits, key ordering, {@code LIKE} escaping,
 * JSON and timestamp binding, and the directory of the shipped schema.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 3.2.0
 */
@Internal
public final class DbSqlDialect {

    /**
     * The escape character used in {@code LIKE} patterns. A backslash is avoided because MySQL treats it as an
     * escape inside string literals.
     */
    public static final char LIKE_ESCAPE = '!';

    private final Dialect dialect;

    public DbSqlDialect(@NonNull Dialect dialect) {
        this.dialect = dialect;
    }

    /**
     * @return The Micronaut Data dialect.
     */
    @NonNull
    public Dialect getDialect() {
        return dialect;
    }

    /**
     * @return The name of the directory holding the shipped schema of this dialect, for example {@code postgres}.
     */
    @NonNull
    public String getSchemaDirectory() {
        return switch (dialect) {
            case POSTGRES -> "postgres";
            case ORACLE -> "oracle";
            case MYSQL -> "mysql";
            case SQL_SERVER -> "sqlserver";
            default -> "h2";
        };
    }

    /**
     * Appends a row limit to a query that already has its {@code ORDER BY} clause.
     *
     * @param sql The query.
     * @param rows The maximum number of rows.
     * @return The query with the limit.
     */
    @NonNull
    public String limit(@NonNull String sql, int rows) {
        return switch (dialect) {
            case MYSQL, SQLITE -> sql + " LIMIT " + rows;
            case SQL_SERVER -> sql + " OFFSET 0 ROWS FETCH NEXT " + rows + " ROWS ONLY";
            default -> sql + " FETCH FIRST " + rows + " ROWS ONLY";
        };
    }

    /**
     * Returns an expression that compares and sorts keys by their binary value. Oracle sorts by the session's
     * {@code NLS_SORT}, which may be linguistic, and rejects column collations on a default install, so its keys are
     * wrapped in {@code NLSSORT(..., 'NLS_SORT=BINARY')}. Other dialects rely on the binary collation of the shipped
     * schema.
     *
     * @param expression A column or a bind marker.
     * @return The expression to order and compare by.
     */
    @NonNull
    public String binaryKey(@NonNull String expression) {
        if (dialect == Dialect.ORACLE) {
            return "NLSSORT(" + expression + ", 'NLS_SORT=BINARY')";
        }
        return expression;
    }

    /**
     * Escapes a prefix for use in a {@code LIKE ... ESCAPE '!'} pattern and appends the wildcard.
     *
     * @param prefix The literal prefix.
     * @return The pattern.
     */
    @NonNull
    public String likePrefix(@NonNull String prefix) {
        StringBuilder sb = new StringBuilder(prefix.length() + 8);
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (c == '%' || c == '_' || c == LIKE_ESCAPE || (c == '[' && dialect == Dialect.SQL_SERVER)) {
                sb.append(LIKE_ESCAPE);
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }

    /**
     * @return The {@code ESCAPE} clause matching {@link #likePrefix(String)}.
     */
    @NonNull
    public String likeEscape() {
        return " ESCAPE '" + LIKE_ESCAPE + "'";
    }

    /**
     * Binds JSON text. On PostgreSQL the value is sent untyped so that it can target {@code jsonb}, {@code json} or
     * text columns alike.
     *
     * @param ps The statement.
     * @param index The parameter index.
     * @param json The JSON text, or {@code null}.
     * @throws SQLException If binding fails.
     */
    public void bindJson(@NonNull PreparedStatement ps, int index, @Nullable String json) throws SQLException {
        if (dialect == Dialect.POSTGRES) {
            if (json == null) {
                ps.setNull(index, Types.OTHER);
            } else {
                ps.setObject(index, json, Types.OTHER);
            }
        } else if (json == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, json);
        }
    }

    /**
     * Returns the select expression of a JSON column, so that it reads back as text.
     *
     * @param column The column.
     * @param builtIn Whether the column is one of the built-in tables, whose types are known.
     * @return The select expression.
     */
    @NonNull
    public String selectJson(@NonNull String column, boolean builtIn) {
        if (builtIn && dialect == Dialect.ORACLE) {
            return "JSON_SERIALIZE(" + column + " RETURNING CLOB)";
        }
        return column;
    }

    /**
     * Binds an instant to a {@code TIMESTAMP} column, as UTC.
     *
     * @param ps The statement.
     * @param index The parameter index.
     * @param instant The instant.
     * @throws SQLException If binding fails.
     */
    public void bindTimestamp(@NonNull PreparedStatement ps, int index, @NonNull Instant instant) throws SQLException {
        ps.setObject(index, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    /**
     * Reads a {@code TIMESTAMP} column written by {@link #bindTimestamp(PreparedStatement, int, Instant)}.
     *
     * @param rs The result set.
     * @param index The column index.
     * @return The instant, or {@code null}.
     * @throws SQLException If reading fails.
     */
    @Nullable
    public Instant readTimestamp(@NonNull ResultSet rs, int index) throws SQLException {
        LocalDateTime value = rs.getObject(index, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /**
     * Returns a query selecting a window of a binary column, or {@code null} when windows are read through
     * {@link java.sql.Blob#getBytes(long, int)} instead. The query takes the 1-based offset and the length as its
     * first two parameters.
     *
     * @param column The binary column.
     * @return The select expression, or {@code null}.
     */
    @Nullable
    public String substringBinary(@NonNull String column) {
        return switch (dialect) {
            case POSTGRES -> "SUBSTRING(" + column + " FROM ? FOR ?)";
            case MYSQL, SQL_SERVER -> "SUBSTRING(" + column + ", ?, ?)";
            default -> null;
        };
    }

    /**
     * Whether an exception reports a unique constraint violation.
     *
     * @param e The exception.
     * @return {@code true} if a unique constraint was violated.
     */
    public boolean isUniqueViolation(@NonNull SQLException e) {
        String state = e.getSQLState();
        if (state != null && state.startsWith("23")) {
            return true;
        }
        int code = e.getErrorCode();
        return switch (dialect) {
            case ORACLE -> code == 1;
            case MYSQL -> code == 1062;
            case SQL_SERVER -> code == 2627 || code == 2601;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return dialect.name().toLowerCase(Locale.ROOT);
    }
}
