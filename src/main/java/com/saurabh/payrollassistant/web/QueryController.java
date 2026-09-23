package com.saurabh.payrollassistant.web;

import com.saurabh.payrollassistant.agent.PayrollAgent;
import com.saurabh.payrollassistant.data.DatabaseSchema;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// The only door into this application. No business logic here - it validates and delegates.
@RestController
public class QueryController {

    // Anything longer is not a payroll question - it is a way to run up the token bill
    private static final int MAX_QUESTION_LENGTH = 500;

    private final PayrollAgent payrollAgent;
    private final DatabaseSchema databaseSchema;

    QueryController(PayrollAgent payrollAgent, DatabaseSchema databaseSchema) {
        this.payrollAgent = payrollAgent;
        this.databaseSchema = databaseSchema;
    }

    public record QueryRequest(String question) {
    }

    // POST, not GET: the question carries worker names and pay details, and a GET would
    // leave all of it in access logs, browser history and proxy caches.
    // Body: {"question": "Why is Ramesh's pay short this week?"}
    @PostMapping("/query")
    public QueryResponse query(@RequestBody QueryRequest request) {
        String question = request.question();
        if (question == null || question.isBlank() || question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("Question must be between 1 and " + MAX_QUESTION_LENGTH + " characters.");
        }
        return payrollAgent.ask(question);
    }

    // Live, database-driven schema - proof that what the model is told can never go stale
    @GetMapping("/schema")
    public String schema() {
        return databaseSchema.describeForModel();
    }


    // A rejected question is the caller's mistake, so return 400 rather than a 500 blob
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
