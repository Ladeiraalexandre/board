package br.com.dio.persistence.migration;

import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.AllArgsConstructor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.SQLException;

import static br.com.dio.persistence.config.ConnectionConfig.getConnection;

@AllArgsConstructor
public class MigrationStrategy {

    private final Connection connection;

    public void executeMigration() {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try (FileOutputStream fos = new FileOutputStream("liquibase.log");
             PrintStream logStream = new PrintStream(fos)) {

            System.setOut(logStream);
            System.setErr(logStream);

            try (Connection conn = getConnection();
                 JdbcConnection jdbcConnection = new JdbcConnection(conn)) {

                Liquibase liquibase = new Liquibase(
                        "/db/changelog/db.changelog-master.yml",
                        new ClassLoaderResourceAccessor(),
                        jdbcConnection);
                liquibase.update();
            } catch (SQLException | LiquibaseException e) {
                e.printStackTrace(logStream);
            }
        } catch (IOException ex) {
            ex.printStackTrace(originalErr);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
