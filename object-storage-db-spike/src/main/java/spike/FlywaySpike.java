package spike;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Checks how library-shipped Flyway migrations coexist with the application's own Flyway migrations.
 */
public final class FlywaySpike {

    private static final String URL = "jdbc:postgresql://localhost:5432/spike";

    public static void main(String[] args) throws Exception {
        // Option A: library migrations with their own history table, app migrations first.
        reset("fw_a");
        run("A1 app V1", () -> app("fw_a", "db/app-v1").migrate());
        run("A2 library (own history table)", () -> library("fw_a").migrate());
        run("A3 app V2..V5 after library", () -> app("fw_a", "db/app-v1", "db/app-v2").migrate());
        run("A4 library again (no-op)", () -> library("fw_a").migrate());
        print("fw_a", app("fw_a", "db/app-v1", "db/app-v2"));
        print("fw_a", library("fw_a"));

        // Option A, library first on an empty schema, app Flyway second.
        reset("fw_a2");
        run("A5 library first on empty schema", () -> library("fw_a2").migrate());
        run("A6 app V1 after library (default config)", () -> app("fw_a2", "db/app-v1").migrate());

        // Option B: shared history table, library migrations with a reserved version prefix.
        reset("fw_b");
        run("B1 app V1..V5", () -> app("fw_b", "db/app-v1", "db/app-v2").migrate());
        run("B2 app + library 3.2.0.1 in the same history", () -> app("fw_b", "db/app-v1", "db/app-v2", "db/mnos-reserved").migrate());
        run("B3 same with outOfOrder=true", () -> Flyway.configure().dataSource(URL, "spike", "spike").schemas("fw_b")
            .locations("classpath:db/app-v1", "classpath:db/app-v2", "classpath:db/mnos-reserved").outOfOrder(true).load().migrate());
    }

    private static Flyway app(String schema, String... locations) {
        String[] locs = new String[locations.length];
        for (int i = 0; i < locations.length; i++) {
            locs[i] = "classpath:" + locations[i];
        }
        return Flyway.configure().dataSource(URL, "spike", "spike").schemas(schema).locations(locs).load();
    }

    private static Flyway library(String schema) {
        return Flyway.configure().dataSource(URL, "spike", "spike").schemas(schema)
            .table("mn_os_schema_history")
            .locations("classpath:db/mnos/postgres")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
    }

    private static void run(String label, Runnable r) {
        try {
            r.run();
            System.out.println("FLYWAY " + label + ": OK");
        } catch (FlywayException e) {
            String msg = e.getMessage().replaceAll("\\s+", " ");
            System.out.println("FLYWAY " + label + ": FAILED " + msg.substring(0, Math.min(400, msg.length())));
        }
    }

    private static void print(String schema, Flyway flyway) {
        StringBuilder sb = new StringBuilder("FLYWAY history " + schema + "." + flyway.getConfiguration().getTable() + ": ");
        for (MigrationInfo info : flyway.info().applied()) {
            sb.append(info.getVersion()).append(' ').append(info.getState()).append("; ");
        }
        System.out.println(sb);
    }

    private static void reset(String schema) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, "spike", "spike"); Statement s = c.createStatement()) {
            s.execute("drop schema if exists " + schema + " cascade");
            s.execute("create schema " + schema);
        }
    }

    private FlywaySpike() { }
}
