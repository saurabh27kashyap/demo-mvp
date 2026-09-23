# Payroll Query Assistant

A site foreman asks a payroll question in plain English. The assistant works out what it
needs, queries a real database as many times as it takes, and answers with numbers it
actually found.

> **This is a trial build** — a demo put together to try the idea out and to learn how an
> agent loop really works. It runs on seeded sample data, not real payroll.

---

## What it looks like

**It asks when a question is ambiguous.** Two workers here are called Ramesh, so the
assistant refuses to guess which one you meant:

![Disambiguating two workers with the same name](docs/01-ambiguity.png)

**It answers compliance questions by joining several tables:**

![Workers paid below the legal minimum wage](docs/02-wage-compliance.png)

---

## How it works: the ReAct loop

The model cannot see the database and cannot run anything. It can only read text and write
text. So the work is split — the model decides, the application acts:

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

1. **Think** — the model is sent the question plus everything learned so far, and it replies
   with either a tool request or an answer
2. **Act** — if it asked for a tool, the application runs it and the model never touches the
   database itself
3. **Observe** — the result is appended to the conversation, and the loop goes round again

**How it knows to stop:** there is no cleverness here. Each round the model returns either
`tool_calls` or ordinary text. Tool calls mean "I still need something"; plain text means "I
have everything, here is the answer" — and that is the exit condition. A round cap catches a
model that gets stuck in a loop, and hitting it is treated as a failure, not a normal finish.

The model is stateless, so the conversation list is the agent's entire memory and all of it
is re-sent every round. That is what lets one English question become four SQL queries: the
model only ever decides the *next* step, never the whole plan up front.

### The two tools

| Tool | What it does |
|---|---|
| `execute_sql(sql)` | Runs one read-only `SELECT` / `WITH` and returns rows as JSON |
| `calculate(expr)` | Evaluates arithmetic exactly, e.g. `38 * 250` |

A failing tool does not throw. The error is handed *back to the model*, which reads it,
fixes its SQL and tries again on the next round.

---

## Keeping the answers honest

An agent that writes its own SQL is only as trustworthy as what it is allowed to do.

**Three layers stop a write:**

1. `Guardrails` rejects anything that is not a `SELECT`/`WITH`, and blocks `INSERT`,
   `UPDATE`, `DELETE`, `DROP`, `ALTER` and `;`
2. The generated SQL runs as a separate `readonly_agent` database user holding only
   `SELECT` — so a write is refused by the database even if layer 1 were bypassed
3. `calculate` is a small hand-written parser, not a script engine, so it can only ever
   produce a number

**Three rules stop an invented answer:**

- The live schema — every table, column and the real values of each small column — is put
  into the prompt, so filters are matched against values that exist instead of guessed
- Every number in an answer must come from a query result or from `calculate`
- The server's date is stamped into each request, because a model has no clock and would
  otherwise resolve "last week" against whenever its training data ended

---

## The data

Five tables: `worker`, `project`, `shift`, `pay_record` and `wage_determination` (the legal
minimum rate for a trade on a site).

A pay week is identified by the Monday it starts on — `week_starting`, a real date, on both
`shift` and `pay_record`, so hours worked and money paid line up on `(worker_id,
week_starting)`.

The seed data is deliberately awkward, because that is where this kind of system breaks:

- two different workers are both named **Ramesh**, so a question by name alone is ambiguous
- some workers are paid **less than their hours × rate**
- some are paid their agreed rate but still **below the legal minimum** for their trade —
  a different question entirely

---

## Running it

```bash
cp .env.example .env        # put your GROQ_API_KEY in it
docker compose up
```

Then open http://localhost:8080, or call it directly:

```bash
curl -X POST localhost:8080/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"Is anyone paid below the legal minimum wage for their trade?"}'
```

The response carries the answer plus every SQL query that ran, the tool-call count, tokens
and cost — so any number in the answer can be traced back to where it came from.

## Tests

```bash
./mvnw test
```

40 tests. Most of them are on `Guardrails` — the injection and write attempts it has to
refuse — since that is the part that has to hold.

## Built with

Java 21 · Spring Boot 4 · H2 · Groq (`openai/gpt-oss-120b`)
