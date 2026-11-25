package io.micronaut.objectstorage.db

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jdbc.DataSourceResolver
import io.micronaut.objectstorage.db.internal.DbSchemaScripts
import io.micronaut.objectstorage.request.UploadRequest
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.flywaydb.core.Flyway
import spock.lang.Specification
import spock.lang.Unroll

import javax.sql.DataSource

class DbSchemaDeliverySpec extends Specification {

    static Map<String, String> h2(String db, String schemaGenerate) {
        [
            'datasources.default.url'                         : "jdbc:h2:mem:${db};DB_CLOSE_DELAY=-1".toString(),
            'datasources.default.driver-class-name'           : 'org.h2.Driver',
            'datasources.default.username'                    : 'sa',
            'datasources.default.password'                    : '',
            'datasources.default.dialect'                     : 'H2',
            'micronaut.object-storage.db.files.schema-generate': schemaGenerate,
            'micronaut.object-storage.db.files.enabled'       : 'true',
        ]
    }

    static DataSource target(ApplicationContext ctx) {
        ctx.getBean(DataSourceResolver).resolve(ctx.getBean(DataSource))
    }

    static Set<String> tables(DataSource dataSource) {
        dataSource.connection.withCloseable { c ->
            c.metaData.getTables(null, 'PUBLIC', null, ['TABLE'] as String[]).withCloseable { rs ->
                Set<String> names = []
                while (rs.next()) {
                    names << rs.getString('TABLE_NAME')
                }
                names
            }
        }
    }

    void 'CREATE creates the tables on first use and leaves existing data alone'() {
        given:
        String db = UUID.randomUUID()
        def first = ApplicationContext.run(h2(db, 'CREATE'))

        expect: 'nothing is created at startup'
        !tables(target(first)).contains('MN_OS_OBJECT')

        when:
        first.getBean(DbStorageOperations, Qualifiers.byName('files')).upload(UploadRequest.fromBytes('a'.bytes, 'a.txt'))

        then:
        tables(target(first)).containsAll(['MN_OS_OBJECT', 'MN_OS_CHUNK', 'MN_OS_BUCKET', 'MN_OS_UPLOAD', 'MN_OS_UPLOAD_PART'])

        when: 'a second application starts on the same database'
        def second = ApplicationContext.run(h2(db, 'CREATE'))

        then:
        second.getBean(DbStorageOperations, Qualifiers.byName('files')).retrieve('a.txt').get().inputStream.text == 'a'

        cleanup:
        second?.close()
        first?.close()
    }

    void 'FLYWAY migrates the tables with a dedicated history table'() {
        given:
        String db = UUID.randomUUID()
        def ctx = ApplicationContext.run(h2(db, 'FLYWAY'))
        def storage = ctx.getBean(DbStorageOperations, Qualifiers.byName('files'))

        when:
        storage.upload(UploadRequest.fromBytes('a'.bytes, 'a.txt'))

        then:
        tables(target(ctx)).containsAll(['MN_OS_OBJECT', 'MN_OS_SCHEMA_HISTORY'])
        !tables(target(ctx)).contains('flyway_schema_history')

        when: 'the application restarts'
        ctx.close()
        ctx = ApplicationContext.run(h2(db, 'FLYWAY'))

        then:
        ctx.getBean(DbStorageOperations, Qualifiers.byName('files')).retrieve('a.txt').get().inputStream.text == 'a'

        cleanup:
        ctx?.close()
    }

    void 'the shipped Flyway migrations can be applied by the application Flyway'() {
        given:
        String db = UUID.randomUUID()
        def ctx = ApplicationContext.run(h2(db, 'NONE'))
        DataSource dataSource = target(ctx)

        when:
        def result = Flyway.configure()
            .dataSource(dataSource)
            .locations('classpath:db/micronaut-object-storage-db/h2')
            .load()
            .migrate()

        then:
        result.migrationsExecuted == 1
        ctx.getBean(DbStorageOperations, Qualifiers.byName('files')).upload(UploadRequest.fromBytes('a'.bytes, 'a.txt'))

        cleanup:
        ctx?.close()
    }

    void 'the shipped Liquibase changelog can be included by the application'() {
        given:
        String db = UUID.randomUUID()
        def ctx = ApplicationContext.run(h2(db, 'NONE'))
        DataSource dataSource = target(ctx)

        when:
        dataSource.connection.withCloseable { c ->
            new Liquibase('db/changelog/micronaut-object-storage-db/h2.xml', new ClassLoaderResourceAccessor(), new JdbcConnection(c))
                .update('')
        }

        then:
        def storage = ctx.getBean(DbStorageOperations, Qualifiers.byName('files'))
        storage.upload(UploadRequest.fromBytes('a'.bytes, 'a.txt'))
        storage.retrieve('a.txt').get().inputStream.text == 'a'

        cleanup:
        ctx?.close()
    }

    @Unroll
    void 'the #dialect schema script declares every built-in table'() {
        when:
        List<String> statements = DbSchemaScripts.load(dialect)

        then:
        ['MN_OS_BUCKET', 'MN_OS_OBJECT', 'MN_OS_CHUNK', 'MN_OS_UPLOAD', 'MN_OS_UPLOAD_PART'].every { table ->
            statements.any { it.toUpperCase().replaceAll(/\s+/, ' ').startsWith("CREATE TABLE $table ") }
        }
        statements.every { !it.trim().isEmpty() }

        where:
        dialect << ['h2', 'postgres', 'oracle', 'mysql', 'sqlserver']
    }

    void 'the script splitter respects quotes and comments'() {
        expect:
        DbSchemaScripts.split('''
            -- a comment; with a semicolon
            CREATE TABLE A (X VARCHAR(10) DEFAULT 'a;b');
            /* block; comment */
            CREATE FUNCTION F() RETURNS TRIGGER AS 'BEGIN PERFORM 1; RETURN OLD; END' LANGUAGE plpgsql;
            INSERT INTO A VALUES ('it''s; fine');
        ''') == [
            "CREATE TABLE A (X VARCHAR(10) DEFAULT 'a;b')",
            "CREATE FUNCTION F() RETURNS TRIGGER AS 'BEGIN PERFORM 1; RETURN OLD; END' LANGUAGE plpgsql",
            "INSERT INTO A VALUES ('it''s; fine')",
        ]
    }
}
