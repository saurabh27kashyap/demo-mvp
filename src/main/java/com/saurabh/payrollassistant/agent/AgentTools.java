package com.saurabh.payrollassistant.agent;

import com.saurabh.payrollassistant.data.Guardrails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The complete catalogue of what the AI can ask for - there is nothing else it can do.
// This file holds two things per tool: what the model is TOLD it can call (definitions),
// and what actually happens when it calls it (run). The safety limits themselves live in
// Guardrails; this file only decides which one to invoke and how to report the outcome.
@Component
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    private static final Map<String, Object> EXECUTE_SQL = Map.of(
            "type", "function", "function", Map.of(
                    "name", "execute_sql",
                    "description", "Run a read-only SQL SELECT or WITH query against the payroll database.",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of("sql", Map.of("type", "string")),
                            "required", List.of("sql"))));

    private static final Map<String, Object> CALCULATE = Map.of(
            "type", "function", "function", Map.of(
                    "name", "calculate",
                    "description", "Evaluate a basic arithmetic expression (+, -, *, /, parentheses) to get an "
                            + "exact result. Always use this for any arithmetic instead of computing it yourself.",
                    "parameters", Map.of(
                            "type", "object",
                            "properties", Map.of("expression", Map.of("type", "string", "description", "e.g. '38 * 250'")),
                            "required", List.of("expression"))));

    private final Guardrails guardrails;
    private final ObjectMapper mapper;

    public AgentTools(Guardrails guardrails, ObjectMapper mapper) {
        this.guardrails = guardrails;
        this.mapper = mapper;
    }

    /**
     * The menu the model may order from, sent with every question.
     *
     * <p>CALLED BY: PayrollAgent.callModel()
     */
    public List<Map<String, Object>> toolDefinitionsForModel() {
        return List.of(EXECUTE_SQL, CALCULATE);
    }

    /**
     * @param content the text handed back to the model as this tool's result
     * @param sqlRun  the query that was executed, or null if this call was not SQL
     */
    public record ToolResult(String content, String sqlRun) {
    }

    /**
     * Runs whichever tool the model asked for.
     *
     * <p>CALLED BY: PayrollAgent.runTool()
     * <p>CALLS:     Guardrails.calculate() or Guardrails.runSql()
     *
     * @param toolName      the name the model used, e.g. "execute_sql"
     * @param argumentsJson the model's arguments, still a JSON string
     */
    public ToolResult runToolCall(String toolName, String argumentsJson) {
        var arguments = mapper.readTree(argumentsJson);
        try {
            if ("calculate".equals(toolName)) {
                String expression = arguments.get("expression").asString();
                double result = guardrails.calculate(expression);
                log.info("TOOL calculate({}) = {}", expression, result);
                return new ToolResult(mapper.writeValueAsString(Map.of("result", result)), null);
            }

            String sql = arguments.get("sql").asString();
            log.info("TOOL execute_sql -> {}", sql.replaceAll("\\s+", " "));

            List<Map<String, Object>> rows = guardrails.runSql(sql);
            log.info("TOOL execute_sql <- {} row(s)", rows.size());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rows", rows);
            if (rows.size() == Guardrails.MAX_ROWS) {
                // Say so explicitly, or the model presents a capped result as the complete answer
                result.put("note", "Returned the maximum of " + Guardrails.MAX_ROWS
                        + " rows - there may be more. Narrow the query or aggregate instead of listing rows.");
            }
            return new ToolResult(mapper.writeValueAsString(result), sql);

        } catch (IllegalArgumentException | DataAccessException e) {
            // Hand the failure back as the tool's result so the model can fix its own query.
            // Otherwise one bad column name ends the whole request with an error page.
            log.warn("TOOL FAILED, handing the error back to the model: {}", e.getMessage());
            String sqlAttempted = arguments.has("sql") ? arguments.get("sql").asString() : null;
            return new ToolResult(mapper.writeValueAsString(Map.of("error", String.valueOf(e.getMessage()))), sqlAttempted);
        }
    }
}
