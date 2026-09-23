package com.saurabh.payrollassistant.data;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Everything the model is told about the database is generated here, FROM the database:
// tables, columns, the real values of each text column, and the foreign keys. Nothing is
// hardcoded, so a migration that adds a table shows up in the prompt on its own instead of
// silently making the model's SQL wrong.
@Service
public class DatabaseSchema {

    // Column names alone are not enough: given project(name, location) the model cannot know
    // whether 'Site C' lives in name or in location. So it is shown the actual values.
    private static final int MAX_VALUES_PER_COLUMN = 12;

    // Rebuilt this often, so a schema change is picked up without restarting the app
    private static final Duration REFRESH_AFTER = Duration.ofMinutes(5);

    private final JdbcTemplate jdbcTemplate;

    private volatile String cached;
    private volatile Instant builtAt;

    public DatabaseSchema(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The finished description. Built once, then reused until it goes stale. */
    public String describeForModel() {
        if (cached == null || Duration.between(builtAt, Instant.now()).compareTo(REFRESH_AFTER) > 0) {
            List<Column> columns = readColumns();
            List<String> tables = columns.stream().map(Column::table).distinct().toList();

            cached = buildTables(columns, tables) + buildForeignKeys(tables);
            builtAt = Instant.now();
        }
        return cached;
    }

    /**
     * One column of one table, as the database describes it. {@code listValues} marks the
     * columns worth showing real values for: text, and dates. Dates matter because the pay
     * week is a date now, and the model has to see which weeks actually hold data.
     */
    private record Column(String table, String name, boolean listValues) {
    }

    /** INFORMATION_SCHEMA is built into every SQL database - it describes its own structure. */
    private List<Column> readColumns() {
        return jdbcTemplate.query(
                "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME, ORDINAL_POSITION",
                (row, rowNumber) -> new Column(
                        row.getString("TABLE_NAME").toLowerCase(),
                        row.getString("COLUMN_NAME").toLowerCase(),
                        isListable(row.getString("DATA_TYPE"))));
    }

    /** Text and date columns have values worth listing; numbers and ids do not. */
    private static boolean isListable(String dataType) {
        String type = dataType.toUpperCase();
        return type.contains("CHAR") || type.equals("DATE");
    }

    /** Writes one line per table, plus the real values of its small text and date columns. */
    private String buildTables(List<Column> columns, List<String> tables) {
        StringBuilder description = new StringBuilder();

        for (String table : tables) {
            List<String> names = columns.stream()
                    .filter(column -> column.table().equals(table))
                    .map(Column::name)
                    .toList();
            description.append(table).append("(").append(String.join(", ", names)).append(")\n");

            columns.stream()
                    .filter(column -> column.table().equals(table) && column.listValues())
                    .forEach(column -> distinctValues(table, column.name()).ifPresent(values ->
                            description.append("    ").append(column.name())
                                    .append(" values: ").append(values).append("\n")));
        }
        return description.toString();
    }

    /**
     * Lists which column points at which, e.g. "shift.worker_id -> worker.id", so the model
     * knows how to join the tables.
     *
     * <p>JDBC already knows this. getImportedKeys(table) means "which other tables does this
     * one point at", and every driver implements it, so no SQL is written here at all.
     */
    private String buildForeignKeys(List<String> tables) {
        return jdbcTemplate.execute((ConnectionCallback<String>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            StringBuilder description = new StringBuilder("\nForeign keys:\n");

            for (String table : tables) {
                try (ResultSet links = metaData.getImportedKeys(null, "PUBLIC", table.toUpperCase())) {
                    while (links.next()) {
                        description.append("    ")
                                .append(table).append(".")
                                .append(links.getString("FKCOLUMN_NAME").toLowerCase())
                                .append(" -> ")
                                .append(links.getString("PKTABLE_NAME").toLowerCase()).append(".")
                                .append(links.getString("PKCOLUMN_NAME").toLowerCase())
                                .append("\n");
                    }
                }
            }
            return description.toString();
        });
    }

    // Identifiers come from INFORMATION_SCHEMA (the database's own metadata), never from user input
    private Optional<String> distinctValues(String table, String column) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT DISTINCT \"" + column.toUpperCase() + "\" FROM \"" + table.toUpperCase() + "\""
                        + " ORDER BY 1 LIMIT " + (MAX_VALUES_PER_COLUMN + 1), String.class);

        // Too many distinct values to be a useful hint (e.g. a free-text column) - skip it
        if (values.isEmpty() || values.size() > MAX_VALUES_PER_COLUMN) {
            return Optional.empty();
        }
        return Optional.of(String.join(" | ", values));
    }
}
