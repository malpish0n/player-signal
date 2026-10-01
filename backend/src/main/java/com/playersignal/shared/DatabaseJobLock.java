package com.playersignal.shared;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Session lock and page writes share one connection: losing the lock also stops writes. */
public final class DatabaseJobLock implements AutoCloseable {
    public final Connection connection;
    public final JdbcTemplate jdbc;
    private final long key;
    private DatabaseJobLock(Connection connection, long key) {
        this.connection = connection;
        this.key = key;
        this.jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }
    public static DatabaseJobLock acquire(DataSource source, long key) throws SQLException {
        Connection connection = source.getConnection();
        try {
            try (var statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
                statement.setLong(1, key);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (result.getBoolean(1)) return new DatabaseJobLock(connection, key);
                }
            }
            connection.close();
            return null;
        } catch (SQLException error) { connection.close(); throw error; }
    }
    @Override public void close() throws SQLException {
        try {
            if (!connection.getAutoCommit()) { connection.rollback(); connection.setAutoCommit(true); }
            try (var statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                statement.setLong(1, key);
                statement.execute();
            }
        } catch (SQLException error) {
            // A pooled connection must never retain an advisory lock on return to the pool.
            connection.abort(Runnable::run);
            throw error;
        } finally { connection.close(); }
    }
}
