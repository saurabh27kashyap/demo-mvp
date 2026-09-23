package com.saurabh.payrollassistant.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// EVERY limit on what the AI is allowed to do lives in this one file.
// The AI can do exactly two things, and both are constrained here:
//   runSql(...)    - read the database, and only read it
//   calculate(...) - do arithmetic, because models get arithmetic wrong
@Component
public class Guardrails {

    private static final Logger log = LoggerFactory.getLogger(Guardrails.class);

    // The model decides what to SELECT, so an unbounded result is its decision, not ours:
    // one "SELECT * FROM shift" on a real table would exhaust memory and blow the token budget.
    public static final int MAX_ROWS = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    /** A ';' is here too: one trailing semicolon is stripped first, so any that remains is a second statement. */
    private static final String[] BLOCKED_KEYWORDS = {"INSERT", "UPDATE", "DELETE", "DROP", "ALTER", ";"};

    // Deliberately NOT a Spring @Bean - if this were a DataSource bean, Spring Boot would stop
    // creating its own admin datasource entirely (@ConditionalOnMissingBean rule).
    private final JdbcTemplate readOnlyJdbcTemplate;

    public Guardrails() {
        DriverManagerDataSource readOnlyDataSource = new DriverManagerDataSource();
        readOnlyDataSource.setDriverClassName("org.h2.Driver");
        readOnlyDataSource.setUrl("jdbc:h2:mem:payroll");
        readOnlyDataSource.setUsername("readonly_agent"); // granted SELECT only, in schema.sql
        readOnlyDataSource.setPassword("agent_readonly_pw");

        this.readOnlyJdbcTemplate = new JdbcTemplate(readOnlyDataSource);
        this.readOnlyJdbcTemplate.setMaxRows(MAX_ROWS);
        this.readOnlyJdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS); // a cartesian join can't hang a thread
    }

    /**
     * Runs one model-written query. Three things stand between the model and the database:
     * LAYER 1 rejectIfNotReadOnly(), LAYER 2 the readonly_agent database user, and the
     * row and time caps configured on the template above.
     */
    public List<Map<String, Object>> runSql(String sql) {
        String query = withoutTrailingSemicolon(sql.trim());
        rejectIfNotReadOnly(query);

        // Even if LAYER 1 were bypassed, readonly_agent has no permission to write, and none
        // to read files off disk through H2's FILE_READ/CSVREAD functions either.
        return readOnlyJdbcTemplate.queryForList(query);
    }

    /** The model routinely ends its SQL with ';'. One trailing semicolon is harmless. */
    private static String withoutTrailingSemicolon(String sql) {
        return sql.endsWith(";") ? sql.substring(0, sql.length() - 1).trim() : sql;
    }

    /** LAYER 1: reads are allowed, everything else is refused before it reaches the database. */
    private static void rejectIfNotReadOnly(String query) {
        String upperCased = query.toUpperCase();

        if (!upperCased.startsWith("SELECT") && !upperCased.startsWith("WITH")) {
            log.warn("REJECTED (not a SELECT/WITH query): {}", query);
            throw new IllegalArgumentException("Only SELECT/WITH queries are allowed: " + query);
        }
        for (String blocked : BLOCKED_KEYWORDS) {
            if (upperCased.contains(blocked)) {
                log.warn("REJECTED (blocked keyword '{}'): {}", blocked, query);
                throw new IllegalArgumentException("This keyword is not allowed (" + blocked + "): " + query);
            }
        }
    }

    // A small, safe arithmetic evaluator - digits, + - * / and parentheses, nothing else.
    // Not a script engine, so there is no code-execution risk: it can only produce a number.
    public double calculate(String expression) {
        return new ExpressionParser(expression).parse();
    }

    private static class ExpressionParser {
        private final String expr;
        private int pos = -1;
        private int ch;

        ExpressionParser(String expression) {
            this.expr = expression.replaceAll("\\s+", "");
            nextChar();
        }

        private void nextChar() {
            ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        double parse() {
            double result = parseExpression();
            if (pos < expr.length()) {
                throw new IllegalArgumentException("Unexpected character at position " + pos + " in: " + expr);
            }
            return result;
        }

        // + and - (lowest precedence)
        private double parseExpression() {
            double x = parseTerm();
            while (true) {
                if (eat('+')) x += parseTerm();
                else if (eat('-')) x -= parseTerm();
                else return x;
            }
        }

        // * and / (higher precedence)
        private double parseTerm() {
            double x = parseFactor();
            while (true) {
                if (eat('*')) x *= parseFactor();
                else if (eat('/')) x /= parseFactor();
                else return x;
            }
        }

        // numbers, parentheses, unary +/-
        private double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();

            double x;
            int startPos = pos;
            if (eat('(')) {
                x = parseExpression();
                eat(')');
            } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                x = Double.parseDouble(expr.substring(startPos, pos));
            } else {
                throw new IllegalArgumentException("Unexpected character at position " + pos + " in: " + expr);
            }
            return x;
        }
    }
}
