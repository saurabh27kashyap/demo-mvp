package com.saurabh.payrollassistant.web;

import java.util.List;

// What the caller gets back. Beyond the answer itself it carries the agent's "working":
// every SQL query that ran, how many tool calls it took, and what the run cost - so a
// human can audit where each number came from and what it spent getting there.
public record QueryResponse(
        String question,
        String answer,
        List<String> sqlQueries,
        int toolCalls,
        long totalTokens,
        double estimatedCostUsd,
        long durationMs
) {
}
