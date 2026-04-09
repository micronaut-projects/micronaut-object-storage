package io.micronaut.objectstorage.relational

import io.micronaut.objectstorage.ObjectStorageException

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

class JdbcRelationalObjectStoreSpec extends spock.lang.Specification {

    void 'openInputStream closes allocated jdbc resources when stream acquisition fails'() {
        given:
        DataSource dataSource = Mock()
        Connection initializationConnection = Stub()
        Statement initializationStatement = Stub()
        DatabaseMetaData initializationMetadata = Stub()
        Connection queryConnection = Mock()
        PreparedStatement queryStatement = Mock()
        ResultSet queryResultSet = Mock()
        RelationalStorageConfiguration configuration = new RelationalStorageConfiguration('default')

        configuration.setTableName('jdbc_relational_object_store_spec')

        dataSource.getConnection() >>> [initializationConnection, queryConnection]

        initializationConnection.createStatement() >> initializationStatement
        initializationConnection.getMetaData() >> initializationMetadata
        initializationMetadata.getDatabaseProductName() >> 'H2'

        queryConnection.prepareStatement(_ as String) >> queryStatement
        queryStatement.executeQuery() >> queryResultSet
        queryResultSet.next() >> true
        queryResultSet.getBinaryStream(1) >> { throw new SQLException('boom') }

        JdbcRelationalObjectStore store = new JdbcRelationalObjectStore(dataSource, configuration)

        when:
        store.openInputStream('docs/failure.bin')

        then:
        def e = thrown(ObjectStorageException)
        e.message == 'Error opening relational object stream for key [docs/failure.bin]'

        1 * queryStatement.setString(1, 'docs/failure.bin')
        1 * queryResultSet.close()
        1 * queryStatement.close()
        1 * queryConnection.close()
    }
}
