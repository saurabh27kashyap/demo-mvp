package com.saurabh.payrollassistant.agent;

import com.saurabh.payrollassistant.data.DatabaseSchema;
import com.saurabh.payrollassistant.web.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The agent. One question in, one answer out.
 *
 * <p>Call order for a single question:
 * <pre>
 *   ask(question)                      &lt;- QueryController calls this, and nothing else does
 *     new Run(question)                opens the conversation: instructions + the question
 *     loop, up to MAX_ROUNDS:
 *       askModel(run)                  send the conversation to the model, get its reply
 *       firstToolCall(reply)           did it ask for a tool, or is this the final answer?
 *         null  -> run.finish(answer)  done: return the answer
 *         tool  -> runTool(...)        run it, put the result back, loop again
 *     run.finish("too complex")        the loop ran out of rounds
 * </pre>
 */
@Service
public class PayrollAgent {

    private static final Logger log = LoggerFactory.getLogger(PayrollAgent.class);

    /** If the model has not answered in this many rounds it never will, and each round costs tokens. */
    private static final int MAX_ROUNDS = 9;

    /** Published pricing for the configured model, in dollars per million tokens. */
    private static final double PROMPT_PRICE_PER_MILLION = 0.15;
    private static final double COMPLETION_PRICE_PER_MILLION = 0.60;

    /** One reply from the model: what it said, and what that round cost. */
    public record ModelReply(Map<String, Object> message, long promptTokens, long completionTokens) {
    }

    private final DatabaseSchema databaseSchema;
    private final AgentTools agentTools;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public PayrollAgent(@Value("${llm.base-url}") String baseUrl,
                        @Value("${llm.model}") String model,
                        @Value("${llm.api-key}") String apiKey,
                        DatabaseSchema databaseSchema,
                        AgentTools agentTools) {
        this.apiKey = apiKey;
        this.model = model;
        this.databaseSchema = databaseSchema;
        this.agentTools = agentTools;
        this.restClient = buildRestClient(baseUrl);
    }

    // ================= 1. the loop =================

    /**
     * Answers one payroll question, calling tools as many times as the model needs.
     *
     * <p>CALLED BY: QueryController.query() - and nothing else
     * <p>CALLS:     askModel(), firstToolCall(), runTool(), Run.finish()
     */
    public QueryResponse ask(String question) {
        log.info("QUESTION: {}", question);
        Run run = new Run(question);

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            log.info("Round {}/{}: asking the model...", round, MAX_ROUNDS);

            Map<String, Object> reply;
            try {
                reply = askModel(run);
            } catch (RestClientException e) {
                // Groq is unreachable, slow or rate-limited. Answer normally rather than
                // throwing, so the caller always receives something readable.
                log.error("Round {}/{}: GROQ CALL FAILED: {}", round, MAX_ROUNDS, e.getMessage());
                return run.finish("The AI service is temporarily unavailable (" + e.getMessage()
                        + "). Please try again shortly.");
            }

            Map<String, Object> toolCall = firstToolCall(reply);
            if (toolCall == null) {
                log.info("DONE in {} round(s), {} tool call(s), {} tokens, {} ms",
                        round, run.toolCalls, run.totalTokens(), run.elapsedMs());
                return run.finish((String) reply.get("content"));
            }

            runTool(run, reply, toolCall);
        }

        log.warn("GAVE UP after {} rounds ({} ms): {}", MAX_ROUNDS, run.elapsedMs(), question);
        return run.finish("This question was too complex - no answer after " + MAX_ROUNDS + " attempts.");
    }

    /**
     * Sends the conversation so far to the model and records what the round cost.
     *
     * <p>CALLED BY: ask(), once per round
     * <p>CALLS:     callModel()
     *
     * @return the model's message: either a tool request or the final answer
     */
    private Map<String, Object> askModel(Run run) {
        ModelReply reply = callModel(run.messages);
        run.promptTokens += reply.promptTokens();
        run.completionTokens += reply.completionTokens();
        return reply.message();
    }

    /**
     * The model either asks for a tool or gives its final answer.
     *
     * <p>CALLED BY: ask(), once per round - this is the decision that ends the loop
     *
     * @return the tool the model asked for, or null if it is finished and this reply is the answer
     */
    private static Map<String, Object> firstToolCall(Map<String, Object> reply) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) reply.get("tool_calls");
        return toolCalls == null ? null : toolCalls.get(0);
    }

    /**
     * Runs the tool the model asked for, then puts BOTH the model's request and the tool's
     * result back into the conversation - that pair is what the next round reads.
     *
     * <p>CALLED BY: ask(), whenever firstToolCall() returned something
     * <p>CALLS:     AgentTools.runToolCall()
     */
    private void runTool(Run run, Map<String, Object> reply, Map<String, Object> toolCall) {
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) toolCall.get("function");

        AgentTools.ToolResult result = agentTools.runToolCall(
                (String) function.get("name"), (String) function.get("arguments"));

        run.toolCalls++;
        if (result.sqlRun() != null) {
            run.sqlRun.add(result.sqlRun());
        }

        run.messages.add(reply);
        run.messages.add(Map.of(
                "role", "tool",
                "tool_call_id", (String) toolCall.get("id"),
                "content", result.content()));
    }

    /**
     * Everything one question accumulates while it runs. It is created per call, so two
     * users asking at the same time never share any of it.
     */
    private final class Run {
        private final String question;
        private final Instant startedAt = Instant.now();
        private final List<Map<String, Object>> messages = new ArrayList<>();
        private final List<String> sqlRun = new ArrayList<>();
        private int toolCalls;
        private long promptTokens;
        private long completionTokens;

        private Run(String question) {
            this.question = question;
            messages.add(Map.of("role", "system", "content", buildPrompt()));
            messages.add(Map.of("role", "user", "content", question));
        }

        private long totalTokens() {
            return promptTokens + completionTokens;
        }

        private long elapsedMs() {
            return Duration.between(startedAt, Instant.now()).toMillis();
        }

        /** Token counts only become a cost once the run is over, so that arithmetic lives here. */
        private QueryResponse finish(String answer) {
            double cost = (promptTokens / 1_000_000.0) * PROMPT_PRICE_PER_MILLION
                    + (completionTokens / 1_000_000.0) * COMPLETION_PRICE_PER_MILLION;
            return new QueryResponse(question, answer, sqlRun, toolCalls, totalTokens(), cost, elapsedMs());
        }
    }

    // ================= 2. what the model is told =================

    /** Package-private so a test can assert exactly what the model receives. */
    String buildPrompt() {
        return """
                You are a payroll assistant for a construction company. You answer questions
                about workers, sites, shifts, pay and wage compliance strictly by querying the
                database described below. You never guess, estimate or invent a number.

                TODAY'S DATE
                """
                + today() + """

                Resolve every relative date against that, never against your own training data:
                "this week" is the week above, "last week" is the one before it, "today" is that
                date. The weeks that actually hold data are listed under week values in the
                schema below. If the week you resolved to is not one of them, do not just say
                there is no data - answer for the most recent week that does have data and state
                clearly which week you used.

                YOUR TOOLS
                - execute_sql(sql): runs ONE read-only SELECT or WITH query and returns the rows
                  as JSON. This is the only way you can see data. If the result contains an
                  "error" field, read the message, fix your SQL and call the tool again.
                - calculate(expression): evaluates arithmetic exactly, e.g. '38 * 250'. Use it
                  for every arithmetic result the query did not already compute. Never do
                  arithmetic in your head, even when it looks easy.

                DATABASE SCHEMA
                The schema below is read from the live database every few minutes, so it is
                always current - including any newly added table or column. The listed values
                are the complete set of values in that column, so match the words in a question
                against them instead of guessing which column holds what.

                """
                + databaseSchema.describeForModel() + """

                HOW THE TABLES RELATE
                - worker is a person; project is a construction site.
                - shift is one worker's work on one site on one day: it carries hours_worked and
                  the pay week that day belongs to.
                - pay_record is what a worker was actually paid for one whole week.
                - wage_determination is the legally required minimum hourly rate for a trade
                  (classification) on a specific site.
                - shift.week and pay_record.week use identical labels, so hours worked and money
                  paid for a week line up on (worker_id, week). Never derive a week from
                  work_date.

                WORKED EXAMPLE - "Was Ramesh (id 1) paid correctly in 2026-W38?"
                1. Hours worked: SELECT SUM(hours_worked) FROM shift
                   WHERE worker_id = 1 AND week = '2026-W38'                  -> 38
                2. His rate:     SELECT hourly_rate FROM worker WHERE id = 1  -> 250
                3. Expected pay: calculate('38 * 250')                        -> 9500
                4. Actually paid: SELECT amount_paid FROM pay_record
                   WHERE worker_id = 1 AND week = '2026-W38'                  -> 9000
                5. Shortfall:    calculate('9500 - 9000')                     -> 500
                   Conclusion: he was underpaid by Rs.500 for that week.

                FOLLOW THESE STEPS FOR EVERY QUESTION:

                STEP 0 - Scope check: if the question is not about workers, sites, shifts, pay
                or wage compliance in this database, do NOT call any tool. Reply that you can
                only answer questions about this payroll database.

                STEP 1 - Resolve the worker, if one is named:
                - employee_code (e.g. 'EMP-003') is the only identifier guaranteed to be
                  unique. A name is not: two workers here are both called Ramesh.
                - If the question already gives a worker id or employee_code, filter on it
                  directly and skip the name check below.
                - Otherwise, if a worker is named, first run: SELECT id, employee_code, name,
                  classification FROM worker WHERE name = '<name>'. If more than one row comes
                  back, STOP - do not guess or analyse further. Reply with EXACTLY this format
                  (nothing else): the literal word AMBIGUOUS on its own line, then one line per
                  match as 'id=<id>, code=<employee_code>, name=<name>,
                  classification=<classification>', then a line asking the user which one.

                STEP 2 - Write the query:
                - The database is H2. Use standard SQL only - no SQLite/MySQL functions such as
                  STRFTIME or DATE_FORMAT.
                - Call execute_sql with one SELECT or WITH query. You may call it several times
                  across turns if you need data from more than one table.

                STEP 3 - Apply the right business rule:
                - "Underpaid" / "kam paisa mila" / "pay short": compare pay_record.amount_paid
                  against EXPECTED pay (SUM of that worker's shift.hours_worked for that week,
                  multiplied by worker.hourly_rate). Never judge by amount_paid alone - a worker
                  who simply worked fewer hours is not underpaid.
                - "Most/least hours" or any ranking: if several workers tie at the max/min
                  value, return ALL of them - never use LIMIT 1 in a way that drops ties.
                - "Below legal minimum wage": join worker.classification =
                  wage_determination.classification AND the site they worked on (via
                  shift.project_id) = wage_determination.project_id, then compare
                  worker.hourly_rate against wage_determination.min_hourly_rate. This is a
                  different question from underpayment - a worker can be paid their full
                  contracted rate and still be below the legal minimum.

                STEP 4 - Check before you answer:
                - If a query returns zero rows, do NOT conclude there is no data yet: re-check
                  your filter values against the values listed in the schema, correct them if
                  they were wrong, and query once more. Only report that there is no data if
                  the corrected query is also empty.
                - Every number in your answer must come from a query result or from the
                  calculate tool. Never state a number from memory.

                OUTPUT FORMAT
                - Answer in plain language, with Markdown tables or bold for structure.
                - The output is shown in a plain Markdown renderer, so never use LaTeX or math
                  notation such as \\text{} or \\times. Write a finished calculation as ordinary
                  text, e.g. '38 hours x Rs.250 = Rs.9500' - every number in it must be one a
                  query or the calculate tool returned, never one you worked out yourself.
                """;
    }

    /**
     * A language model has no clock: left to itself it resolves "this week" from whenever its
     * training data ended. The server's date is stamped in on every request instead.
     */
    private static String today() {
        LocalDate today = LocalDate.now();
        String week = today.get(IsoFields.WEEK_BASED_YEAR) + "-W"
                + String.format("%02d", today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        return "Today is " + today + " (" + today.getDayOfWeek() + "), which falls in week " + week + ".";
    }

    // ================= 3. the HTTP call to Groq =================

    /**
     * The one and only outgoing HTTP call in this project.
     *
     * <p>CALLED BY: askModel()
     * <p>Protected so tests can replace it with scripted replies instead of calling Groq.
     */
    @SuppressWarnings("unchecked")
    protected ModelReply callModel(List<Map<String, Object>> messages) {
        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", model,
                        "messages", messages,
                        "tools", agentTools.toolDefinitionsForModel(),
                        "temperature", 0.0)) // 0 = same question, same SQL; no creativity wanted here
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

        // Groq reports usage on every response; summing it is what makes the cost visible.
        Map<String, Object> usage = (Map<String, Object>) response.get("usage");
        return new ModelReply(message, tokens(usage, "prompt_tokens"), tokens(usage, "completion_tokens"));
    }

    private static long tokens(Map<String, Object> usage, String field) {
        return usage == null ? 0 : ((Number) usage.getOrDefault(field, 0)).longValue();
    }

    /**
     * The base URL comes from configuration, so switching provider - Groq, OpenAI, Together,
     * a model on your own machine - is three lines in application.properties and no Java
     * change at all. They all speak the same OpenAI-compatible API.
     */
    private static RestClient buildRestClient(String baseUrl) {
        // Without timeouts, a hung connection holds a request thread open forever
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
