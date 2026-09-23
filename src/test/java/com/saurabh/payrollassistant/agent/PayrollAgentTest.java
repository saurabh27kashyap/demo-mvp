package com.saurabh.payrollassistant.agent;

import com.saurabh.payrollassistant.data.DatabaseSchema;
import com.saurabh.payrollassistant.data.Guardrails;
import com.saurabh.payrollassistant.web.QueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The agent loop is tested against a FAKE model: the real API is slow, costs money and is
// rate-limited, and we want to test OUR logic, not Groq's. Each test scripts exactly what
// the "model" replies with, by overriding callModel().
class PayrollAgentTest {

    private final Guardrails guardrails = mock(Guardrails.class);
    private final DatabaseSchema databaseSchema = mock(DatabaseSchema.class);
    // The real tool layer on top of mocked guardrails, so dispatch and result-formatting
    // are exercised for real - only the database and the model itself are faked.
    private final AgentTools agentTools = new AgentTools(guardrails, JsonMapper.builder().build());
    private int modelCalls = 0;

    @BeforeEach
    void stubSchema() {
        when(databaseSchema.describeForModel()).thenReturn("worker(id, employee_code, name)\n");
    }

    @Test
    void answersDirectlyWhenTheModelNeedsNoTool() {
        PayrollAgent agent = agentReplying(finalAnswer("Rs. 150.00"));

        QueryResponse response = agent.ask("What is Suresh's hourly rate?");

        assertThat(response.answer()).isEqualTo("Rs. 150.00");
        assertThat(response.sqlQueries()).isEmpty();
        verify(guardrails, never()).runSql(any());
    }

    @Test
    void runsTheSqlTheModelAsksForAndFeedsTheRowsBack() {
        when(guardrails.runSql(any())).thenReturn(List.of(Map.of("HOURLY_RATE", 150.00)));
        PayrollAgent agent = agentReplying(
                sqlToolCall("SELECT hourly_rate FROM worker WHERE id = 2"),
                finalAnswer("Suresh earns Rs. 150.00 per hour."));

        QueryResponse response = agent.ask("What is Suresh's hourly rate?");

        assertThat(response.answer()).contains("150.00");
        assertThat(response.sqlQueries()).containsExactly("SELECT hourly_rate FROM worker WHERE id = 2");
        assertThat(modelCalls).isEqualTo(2);
    }

    // The response carries the agent's own working, which is what makes a run auditable
    @Test
    void reportsToolCallsTokensAndCostForTheRun() {
        when(guardrails.runSql(any())).thenReturn(List.of(Map.of("HOURLY_RATE", 150.00)));
        PayrollAgent agent = agentReplying(
                sqlToolCall("SELECT hourly_rate FROM worker WHERE id = 2"),
                finalAnswer("Rs. 150.00"));

        QueryResponse response = agent.ask("What is Suresh's hourly rate?");

        assertThat(response.toolCalls()).isEqualTo(1);
        assertThat(response.totalTokens()).isEqualTo(240); // 2 rounds x (100 prompt + 20 completion)
        assertThat(response.estimatedCostUsd()).isGreaterThan(0.0);
        assertThat(response.durationMs()).isGreaterThanOrEqualTo(0);
    }

    // A bad query must not end the request: the model gets the error back as a tool result
    // and is given another turn to fix its own SQL.
    @Test
    void handsSqlErrorsBackToTheModelInsteadOfFailingTheRequest() {
        when(guardrails.runSql(any())).thenThrow(new IllegalArgumentException("Column not found: NONEXISTENT"));
        PayrollAgent agent = agentReplying(
                sqlToolCall("SELECT nonexistent FROM worker"),
                finalAnswer("Corrected and answered."));

        QueryResponse response = agent.ask("What is Suresh's hourly rate?");

        assertThat(response.answer()).isEqualTo("Corrected and answered.");
        assertThat(modelCalls).isEqualTo(2); // it got a second turn
    }

    @Test
    void usesTheCalculatorRatherThanLettingTheModelDoArithmetic() {
        when(guardrails.calculate("38 * 250")).thenReturn(9500.0);
        PayrollAgent agent = agentReplying(
                calculateToolCall("38 * 250"),
                finalAnswer("Expected pay is Rs. 9500."));

        QueryResponse response = agent.ask("What should Ramesh have been paid?");

        assertThat(response.answer()).contains("9500");
        assertThat(response.sqlQueries()).isEmpty(); // a calculation is not a SQL query
        verify(guardrails).calculate("38 * 250");
    }

    @Test
    void degradesGracefullyWhenGroqIsUnavailable() {
        PayrollAgent agent = new PayrollAgent("http://test", "model", "key", databaseSchema, agentTools) {
            @Override
            protected ModelReply callModel(List<Map<String, Object>> messages) {
                throw new ResourceAccessException("connection timed out");
            }
        };

        QueryResponse response = agent.ask("What is Suresh's hourly rate?");

        assertThat(response.answer()).contains("temporarily unavailable");
    }

    // Without a round limit a confused model could loop forever, burning tokens every turn
    @Test
    void givesUpAfterTheRoundLimit() {
        when(guardrails.runSql(any())).thenReturn(List.of());
        PayrollAgent agent = agentReplying(sqlToolCall("SELECT 1")); // one reply = it repeats forever

        QueryResponse response = agent.ask("A question it can never finish");

        assertThat(response.answer()).contains("too complex");
        assertThat(modelCalls).isEqualTo(9);
    }

    @Test
    void stampsTodaysDateAndTheToolsIntoEveryPrompt() {
        String prompt = agentReplying(finalAnswer("x")).buildPrompt();

        // Built per request, not once at startup - a long-running server must not stay
        // stuck on the date it happened to boot on
        assertThat(prompt)
                .contains(LocalDate.now().toString())
                .contains("execute_sql")
                .contains("calculate")
                .contains("worker(id, employee_code, name)"); // the live schema really is embedded
    }

    // --- the fake model ---

    private PayrollAgent agentReplying(Map<String, Object>... replies) {
        Deque<Map<String, Object>> scripted = new ArrayDeque<>(List.of(replies));
        return new PayrollAgent("http://test", "model", "key", databaseSchema, agentTools) {
            @Override
            protected ModelReply callModel(List<Map<String, Object>> messages) {
                modelCalls++;
                Map<String, Object> message = scripted.size() > 1 ? scripted.poll() : scripted.peek();
                return new ModelReply(message, 100, 20); // pretend each round cost some tokens
            }
        };
    }

    private static Map<String, Object> finalAnswer(String content) {
        return Map.of("role", "assistant", "content", content);
    }

    private static Map<String, Object> sqlToolCall(String sql) {
        return toolCall("execute_sql", "{\"sql\": \"" + sql + "\"}");
    }

    private static Map<String, Object> calculateToolCall(String expression) {
        return toolCall("calculate", "{\"expression\": \"" + expression + "\"}");
    }

    private static Map<String, Object> toolCall(String name, String argumentsJson) {
        return Map.of("role", "assistant", "tool_calls", List.of(Map.of(
                "id", "call_1",
                "function", Map.of("name", name, "arguments", argumentsJson))));
    }
}
