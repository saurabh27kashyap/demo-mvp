package com.saurabh.payrollassistant.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GuardrailsTest {

    @Autowired
    private Guardrails guardrails;

    // ===== what the AI is allowed to read =====

    @Test
    void allowsSelectQuery() {
        assertThat(guardrails.runSql("SELECT * FROM worker WHERE id = 1")).hasSize(1);
    }

    @Test
    void allowsWithQuery() {
        assertThat(guardrails.runSql("WITH x AS (SELECT * FROM worker) SELECT * FROM x")).isNotEmpty();
    }

    // The model writes SQL in whatever case it likes, so the check cannot be case-sensitive
    @Test
    void allowsLowercaseSelect() {
        assertThat(guardrails.runSql("select * from worker where id = 1")).hasSize(1);
    }

    // This exact bug was found during live debugging - the LLM adds ';' at the end of its SQL
    @Test
    void allowsTrailingSemicolon() {
        assertThat(guardrails.runSql("SELECT * FROM worker WHERE id = 1;")).hasSize(1);
    }

    // ===== what it is not allowed to do =====

    @Test
    void rejectsInsert() {
        assertThatThrownBy(() -> guardrails.runSql("INSERT INTO worker (name, classification, hourly_rate) VALUES ('X','Y',1)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUpdate() {
        assertThatThrownBy(() -> guardrails.runSql("UPDATE worker SET hourly_rate = 9999 WHERE id = 1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDelete() {
        assertThatThrownBy(() -> guardrails.runSql("DELETE FROM pay_record WHERE id = 1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDrop() {
        assertThatThrownBy(() -> guardrails.runSql("DROP TABLE worker"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAlter() {
        assertThatThrownBy(() -> guardrails.runSql("ALTER TABLE worker ADD COLUMN hacked INT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLowercaseInsert() {
        assertThatThrownBy(() -> guardrails.runSql("insert into worker (name, classification, hourly_rate) values ('X','Y',1)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // One harmless trailing ';' is allowed, but a ';' in the middle (i.e. 2 statements) is not
    @Test
    void rejectsMultipleStatements() {
        assertThatThrownBy(() -> guardrails.runSql("SELECT 1; DROP TABLE worker;"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== limits, even on queries that are allowed =====

    // A "SELECT *" over a join the model did not think through must not be able to pull an
    // unbounded result into memory and into the prompt
    @Test
    void capsTheNumberOfRowsReturned() {
        List<Map<String, Object>> rows = guardrails.runSql("SELECT s1.id, s2.id AS id2 FROM shift s1, shift s2");
        assertThat(rows).hasSize(Guardrails.MAX_ROWS);
    }

    // H2 has functions that read files off the disk, and they start with SELECT, so the
    // keyword check lets them through. What stops them is the database user: these need
    // admin rights, which readonly_agent does not have.
    @Test
    void cannotReadFilesFromDiskThroughH2FileFunctions() {
        assertThatThrownBy(() -> guardrails.runSql("SELECT FILE_READ('pom.xml', 'UTF-8')"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void cannotReadFilesFromDiskThroughCsvread() {
        assertThatThrownBy(() -> guardrails.runSql("SELECT * FROM CSVREAD('pom.xml')"))
                .isInstanceOf(DataAccessException.class);
    }

    // The most important security test in the project: even if every check above were
    // bypassed, the database user the agent's SQL runs as has only been granted SELECT,
    // so the database itself refuses to write. This proves the second layer really exists.
    @Test
    void theDatabaseItselfRefusesWritesFromTheAgentUser() {
        DriverManagerDataSource asAgent = new DriverManagerDataSource();
        asAgent.setDriverClassName("org.h2.Driver");
        asAgent.setUrl("jdbc:h2:mem:payroll");
        asAgent.setUsername("readonly_agent");
        asAgent.setPassword("agent_readonly_pw");
        JdbcTemplate bypassingTheGuard = new JdbcTemplate(asAgent);

        assertThatThrownBy(() -> bypassingTheGuard.update("DELETE FROM pay_record"))
                .isInstanceOf(DataAccessException.class);
    }

    // ===== the calculator the AI must use instead of doing maths itself =====

    @Test
    void multiplies() {
        assertThat(guardrails.calculate("38 * 250")).isEqualTo(9500.0);
    }

    @Test
    void subtracts() {
        assertThat(guardrails.calculate("9500 - 9000")).isEqualTo(500.0);
    }

    @Test
    void divides() {
        assertThat(guardrails.calculate("9500 / 38")).isEqualTo(250.0);
    }

    @Test
    void respectsOperatorPrecedence() {
        assertThat(guardrails.calculate("2 + 3 * 4")).isEqualTo(14.0);
    }

    @Test
    void handlesParentheses() {
        assertThat(guardrails.calculate("(2 + 3) * 4")).isEqualTo(20.0);
    }

    @Test
    void handlesDecimalAmounts() {
        assertThat(guardrails.calculate("7.5 * 200")).isEqualTo(1500.0);
    }

    @Test
    void handlesNegativeResults() {
        assertThat(guardrails.calculate("9000 - 9500")).isEqualTo(-500.0);
    }

    // The model could send anything here, so anything that is not arithmetic must be
    // rejected rather than silently evaluated - this parser is not a script engine.
    @Test
    void rejectsAnythingThatIsNotArithmetic() {
        assertThatThrownBy(() -> guardrails.calculate("System.exit(0)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnIncompleteExpression() {
        assertThatThrownBy(() -> guardrails.calculate("38 *"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
