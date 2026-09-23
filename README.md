# Payroll Query Assistant

Natural-language payroll questions answered directly from the database.

Ask *"Is anyone paid below the legal minimum wage for their trade?"* and the agent plans the
lookups itself, writes and runs its own read-only SQL — as many queries as the question needs
— and returns an answer backed by every query it ran.

**Java 21 · Spring Boot 4 · H2 · Groq (`openai/gpt-oss-120b`)**

---

## Quick start

```bash
cp .env.example .env        # add your GROQ_API_KEY
docker compose up
```

The UI is at <http://localhost:8081>.

## API

**`POST /query`**

```bash
curl -X POST localhost:8081/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"Is anyone paid below the legal minimum wage for their trade?"}'
```

```json
{
  "question": "Is anyone paid below the legal minimum wage for their trade?",
  "answer": "Yes — Naresh (EMP-005) is paid ...",
  "sqlQueries": ["SELECT ...", "SELECT ..."],
  "toolCalls": 3,
  "totalTokens": 4820,
  "estimatedCostUsd": 0.0009,
  "durationMs": 2140
}
```

Every response carries the agent's working — the SQL it ran, the number of tool calls, tokens
and cost — so any figure in the answer can be traced back to the query that produced it.
Questions are capped at 500 characters and rejected with `400`.

**`GET /schema`** returns the exact schema description the model is given, generated live from
the database.

---

## Architecture

The model never touches the database. It decides; the application acts.

```
   question
      |
      v
  +---------+  needs data   +------------+
  |  THINK  | ------------> |    ACT     |   the app runs the tool,
  |  model  |               |    app     |   never the model
  +---------+               +------------+
      ^                           |
      |        OBSERVE            v
      +--- result appended to the conversation
      |
      | replies with words instead of a tool call
      v
    answer
```

1. **Think** — the model receives the question plus everything learned so far, and replies with
   either a tool request or a final answer.
2. **Act** — the application executes the requested tool.
3. **Observe** — the result is appended to the conversation and the loop runs again.

**Termination.** Each round the model returns either `tool_calls` or ordinary text. Tool calls
mean it still needs data; plain text is the answer, and that is the exit condition. A round cap
bounds a model that stalls, and hitting it is reported as a failure rather than a normal finish.

Because the model is stateless, the conversation list is the agent's entire memory and is
re-sent every round. That is what turns one English question into four SQL queries: the model
only ever commits to the *next* step, never to the whole plan up front.

### Tools

| Tool | Behaviour |
|---|---|
| `execute_sql(sql)` | Runs one read-only `SELECT` / `WITH`, returns rows as JSON |
| `calculate(expr)` | Evaluates arithmetic exactly, e.g. `38 * 250` |

A failing tool does not throw. The error is returned *to the model*, which reads it, corrects
its SQL and retries on the next round.

---

## Correctness and safety

An agent that writes its own SQL is only as trustworthy as the boundary around it.

**Three layers prevent a write:**

1. `Guardrails` rejects anything that is not a `SELECT` / `WITH`, and blocks `INSERT`, `UPDATE`,
   `DELETE`, `DROP`, `ALTER` and `;`.
2. Generated SQL executes as a dedicated `readonly_agent` database user holding only `SELECT`,
   so the database itself refuses a write even if the application check were bypassed.
3. `calculate` is a hand-written expression parser, not a script engine — it can only produce a
   number.

**Three rules prevent an invented answer:**

- The live schema — every table, column, and the real values of each low-cardinality column — is
  included in the prompt, so filters are matched against values that exist rather than guessed.
- Every number in an answer must originate from a query result or from `calculate`.
- The server's date is stamped into each request, since the model has no clock and would
  otherwise resolve "last week" against its training cutoff.

Out-of-scope questions, prompt-injection attempts, and instructions arriving inside tool results
are treated as data and refused with a fixed response.

---

## Data model

Five tables: `worker`, `project`, `shift`, `pay_record`, and `wage_determination` (the legal
minimum rate for a trade on a given site).

A pay week is identified by the Monday it begins on — `week_starting`, a real date, present on
both `shift` and `pay_record` — so hours worked and money paid reconcile on
`(worker_id, week_starting)`.

The seed data is deliberately awkward, because these are the cases where the system breaks:

- Two different workers are named **Ramesh**, so a question by name alone is ambiguous and the
  agent must ask which one is meant rather than guess.
- Some workers are paid **less than hours × rate**.
- Some are paid their agreed rate but still **below the legal minimum** for their trade — a
  separate question requiring a different join.

Data is seeded into in-memory H2 at startup; nothing is persisted.

---

## Tests

```bash
./mvnw test
```

40 tests. The majority cover `Guardrails` — the injection and write attempts it must refuse —
since that is the boundary the rest of the system depends on.

---

## Notes

Provider is configuration, not code: Groq, OpenAI, Together and most local servers expose the
same OpenAI-compatible API, so switching means changing three properties. The API key is read
from the environment and never committed.

The OpenAI-compatible HTTP call is made directly rather than through Spring AI's starter, which
has an open bug with Groq tool calling ([spring-ai#6968](https://github.com/spring-projects/spring-ai/issues/6968))
where it replays an unsupported `reasoning_content` field.
