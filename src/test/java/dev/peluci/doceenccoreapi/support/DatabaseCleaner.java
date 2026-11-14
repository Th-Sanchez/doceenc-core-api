package dev.peluci.doceenccoreapi.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

public class DatabaseCleaner {

    private static final String RESET_SETTINGS = """
            UPDATE settings SET target_margin_retail = 50.00, target_margin_wholesale = 35.00""";

    private final JdbcTemplate jdbc;
    private String truncateStatement;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        jdbc.execute(truncateStatement());
        jdbc.execute(RESET_SETTINGS);
    }

    private String truncateStatement() {
        if (truncateStatement == null) {
            List<String> tables = jdbc.queryForList("""
                    SELECT tablename FROM pg_tables
                    WHERE schemaname = current_schema()
                    AND tablename NOT IN ('flyway_schema_history', 'settings')""", String.class);
            truncateStatement = "TRUNCATE TABLE"
                    + tables.stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(", "))
                    + " RESTART IDENTITY CASCADE";
        }
        return truncateStatement;
    }
}
