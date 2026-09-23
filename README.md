# Payroll Assistant

Ask a question about worker pay in plain English. Get a correct answer back.

```
You type:  "Why is Mahesh's pay short in 2026-W38?"

You get:   Mahesh worked 20 hours at Rs.200 per hour, so he should have been
           paid Rs.4,000. He was paid Rs.3,800. He is short by Rs.200.
```

No SQL. No spreadsheets. No waiting two days for someone in IT.

---

## 1. Why this exists

A construction site keeps five registers: who works here, which sites we run, who
turned up each day, who got paid what, and what the law says each trade must be paid.

The site foreman has questions about those registers every single day:

- *Was Mahesh paid the right amount last week?*
- *Who worked the most hours?*
- *Is anyone being paid below the legal minimum for their trade?*

All the answers are sitting in the database. The problem is that getting them out
requires writing SQL, and the foreman does not write SQL. So he asks someone technical,
and waits.

That delay is not harmless. If a worker is underpaid and nobody notices, the company can
face a government audit and a fine. The information exists; only the access is missing.

**This project is that missing access.** The foreman asks in English, an AI writes the SQL,
and the answer comes back in English.

---

## 2. The one idea behind the whole thing

An AI language model is good with words and **bad with facts and numbers**. Left alone it
will happily invent a salary that sounds plausible.

So it is never trusted with either. Instead:

```
   The AI can WRITE a question.        It cannot ANSWER one by itself.

   It writes:  "run this SQL for me"    -> our code checks it, runs it, hands back the rows
   It writes:  "multiply 20 by 200"     -> our code calculates it, hands back 4000

   Only then does it write the final sentence, using numbers it was given.
```

Every number in an answer came out of the database or a calculator. Never out of the AI's
imagination.

---

## 3. How a question travels

```
   ┌──────────┐
   │ Foreman  │  types a question in the browser
   └────┬─────┘
        │  POST /query   {"question": "Why is Mahesh's pay short in 2026-W38?"}
        ▼
   ┌─────────────────┐
   │ QueryController │  checks it is not empty and not absurdly long
   └────┬────────────┘
        ▼
   ┌─────────────────┐
   │  PayrollAgent   │  ◄── the loop lives here
   └────┬────────────┘
        │
        │  1. builds the instructions: the rules + what tables exist + today's date
        │  2. sends the question to the AI
        ▼
   ┌─────────────────┐
   │   Groq (AI)     │  somewhere on the internet
   └────┬────────────┘
        │  replies with ONE of two things:
        │
        │    (a) "run this SQL for me"     ──► go to the tools below, then come back here
        │    (b) "here is your answer"     ──► the loop stops
        ▼
   ┌─────────────────┐
   │   AgentTools    │  decides which tool the AI asked for
   └────┬────────────┘
        │
        ├──► execute_sql ──► Guardrails ──► H2 database
        │                    (checks the SQL first, then runs it as a
        │                     read-only user that CANNOT change anything)
        │
        └──► calculate   ──► exact arithmetic, done in Java
                              (never by the AI)
```

The AI never touches the database. It only ever asks, and our code decides whether to
comply.

---

## 4. A real example, step by step

This is an actual run, copied from the logs.

**The question:** *"Why is Mahesh's pay short in 2026-W38?"*

| Round | The AI asks for | It gets back |
|-------|-----------------|--------------|
| 1 | `SELECT id, employee_code, name FROM worker WHERE name = 'Mahesh'` | 1 row: Mahesh is worker 3 |
| 2 | `SELECT SUM(hours_worked) FROM shift WHERE worker_id = 3 AND week = '2026-W38'` | 20 hours |
| 3 | `SELECT hourly_rate FROM worker WHERE id = 3` | Rs. 200 |
| 4 | `calculate("20 * 200")` | 4000 |
| 5 | `SELECT amount_paid FROM pay_record WHERE worker_id = 3 AND week = '2026-W38'` | Rs. 3,800 |
| 6 | `calculate("4000 - 3800")` | 200 |
| 7 | *nothing* — it writes the answer | **Mahesh is short by Rs.200** |

Notice what happened there:

- It did **not** try to answer in one go. It asked one small question at a time.
- After each answer it decided what to ask next. That is what makes it an *agent*.
- It never did the multiplication itself. It asked for it.
- Seven rounds, and every single number is traceable back to the database.

---

## 5. What is in the database

Five tables. Nothing clever.

**`worker`** — who works here

| id | employee_code | name | classification | hourly_rate |
|----|---------------|------|----------------|-------------|
| 1 | EMP-001 | Ramesh | Electrician | 250 |
| 3 | EMP-003 | Mahesh | Carpenter | 200 |
| 7 | EMP-007 | Ramesh | Laborer | 160 |

There are deliberately **two people called Ramesh**. Real payroll has this problem, and
section 7 explains how it is handled.

**`project`** — the construction sites

| id | name | location |
|----|------|----------|
| 1 | Site A | Andheri, Mumbai |
| 3 | Site C | Thane |

**`shift`** — who turned up, one row per person per day

| worker_id | project_id | work_date | week | hours_worked |
|-----------|------------|-----------|------|--------------|
| 3 | 1 | 2026-09-15 | 2026-W38 | 8 |
| 3 | 1 | 2026-09-17 | 2026-W38 | 4 |

Read that as: *Mahesh worked 8 hours at Site A on 15 September.*

`week` is just a label for a working week: `2026-W37` means 8–12 September and
`2026-W38` means 15–19 September.

**`pay_record`** — what each person was actually paid, one row per week

| worker_id | week | amount_paid |
|-----------|------|-------------|
| 3 | 2026-W38 | 3800 |

**`wage_determination`** — the legal minimum rate for a trade on a site

| classification | project_id | min_hourly_rate |
|----------------|------------|-----------------|
| Carpenter | 1 | 210 |

Mahesh is a Carpenter on Site A earning Rs.200, and the legal minimum there is Rs.210.
He is being paid below the legal minimum — a completely separate problem from being
short-paid, and the assistant can spot both.

**How the tables connect:** never by name, always by number. `shift.worker_id = 3` means
"the person whose `worker.id` is 3". That is why two people called Ramesh never get mixed up
inside the database.

---

## 6. The code: seven files

| File | Its one job |
|------|-------------|
| `PayrollAssistantApplication.java` | Starts the application |
| `QueryController.java` | The front door. Receives the question, checks it, passes it on |
| `PayrollAgent.java` | **The loop.** Ask the AI, run what it asks for, repeat until it answers |
| `AgentTools.java` | The menu: what the AI may ask for, and what happens when it does |
| `Guardrails.java` | The limits: what SQL is allowed, and the exact-arithmetic calculator |
| `DatabaseSchema.java` | Tells the AI what tables exist — read live from the database |
| `QueryResponse.java` | The shape of the answer that goes back |

If you only read one file, read `PayrollAgent.java`. The loop is the whole story.

---

## 7. How it avoids being wrong

An assistant that is confidently wrong about pay is worse than no assistant at all. Six
things prevent that.

**It is shown the real values, not just column names.**
The AI is told `location values: Andheri, Mumbai | Powai, Mumbai | Thane`, so it knows
"Thane" is a location and "Site C" is a name. Without this it guessed, and guessed wrong.

**It cannot do arithmetic.**
Every calculation goes to a calculator written in Java. Language models get long
multiplications subtly wrong while sounding certain.

**It stops when a name is ambiguous.**
Ask *"What is Ramesh's hourly rate?"* and there are two Rameshes. It does not pick one. It
replies:

```
AMBIGUOUS
id=1, code=EMP-001, name=Ramesh, classification=Electrician
id=7, code=EMP-007, name=Ramesh, classification=Laborer
Which worker did you mean?
```

The screen turns that into two buttons. You click one, and the question is asked again
with that worker's id attached. From then on every query uses the id, never the name.

**It knows the difference between "paid less" and "underpaid".**
Naresh received the smallest payment of anyone one week. He was not underpaid — he only
worked three days. The rules tell the assistant to compare pay against *hours worked times
rate*, never against other people's pay.

**It corrects its own mistakes.**
If the AI writes SQL with a column that does not exist, the error is handed back to the AI
rather than shown to the user. It reads the error, fixes the query and tries again.

**It shows its working.**
Every answer comes with the SQL that produced it, plus how many steps it took, how many
tokens it used and what that cost. Nothing has to be taken on trust.

---

## 8. How it cannot break anything

The AI writes SQL, and SQL can delete data. Three separate barriers stop that.

**Barrier 1 — the query is inspected.** It must begin with `SELECT` or `WITH`, and it is
rejected outright if it contains `INSERT`, `UPDATE`, `DELETE`, `DROP`, `ALTER`, or a second
statement.

**Barrier 2 — the database itself refuses.** AI-written SQL runs as a database user called
`readonly_agent` that has been granted permission to read and nothing else. Even if barrier 1
had a bug, the database would reject the write.

> There is a test that deliberately bypasses barrier 1 and tries to delete data directly as
> that user, purely to prove barrier 2 really works. "Defence in depth" should be a tested
> fact, not a claim in a README.

**Barrier 3 — nothing can run away.** A query is capped at 200 rows and 10 seconds. The
loop is capped at 9 rounds. A question is capped at 500 characters.

---

## 9. Running it

You need Docker and a free API key from [console.groq.com](https://console.groq.com).

```bash
cd payroll-assistant

# put your key in a .env file (it is gitignored and never committed)
echo "GROQ_API_KEY=your_key_here" > .env

docker compose up --build
```

Then open **http://localhost:8081** and ask a question.

To watch the agent think while you use it, open a second terminal:

```bash
docker compose logs -f
```

You will see every round, every query and every calculation as it happens.

Other useful addresses:

| Address | What it shows |
|---------|---------------|
| http://localhost:8081 | The question box |
| http://localhost:8081/schema | Exactly what the AI is told about the database |
| http://localhost:8081/h2-console | The raw tables (JDBC URL `jdbc:h2:mem:payroll`, user `sa`, no password) |

Run the tests with `./mvnw test` — there are 40.

---

## 10. Questions to try

Every answer below has been checked by hand against the data.

| Ask this | Correct answer |
|----------|----------------|
| What is Suresh's hourly rate? | Rs. 150 |
| What is Ramesh's hourly rate? | Asks which Ramesh you mean |
| Why is Mahesh's pay short in 2026-W38? | Short by Rs. 200 (20h × 200 = 4,000, paid 3,800) |
| Was Naresh paid correctly in 2026-W37? | Yes — 24h × 145 = 3,480, and he was paid exactly that |
| Who worked the most hours in 2026-W38? | Four-way tie at 40 hours |
| Is anyone paid below the legal minimum wage? | Mahesh (200 vs 210) and Yogesh (220 vs 225) |
| What is the weather today? | Politely refuses — it only answers payroll questions |

The free Groq account allows about 8,000 tokens per minute, and one question uses roughly
7,000. **Leave 15–20 seconds between questions**, or you will be rate limited. If that
happens the app does not crash; it says the service is busy and asks you to retry.

---

## 11. Swapping the AI provider

Three lines in `src/main/resources/application.properties`:

```properties
llm.base-url=https://api.groq.com/openai/v1
llm.model=openai/gpt-oss-120b
llm.api-key=${GROQ_API_KEY}
```

Groq, OpenAI, Together and most locally-run models all speak the same API, so moving
between them is a configuration change with no code change at all.

---

## 12. What this is not

This is a working demonstration, not a production payroll system. The gaps are deliberate
and known:

- **No login.** Anyone who can reach the page can ask about anyone's pay. A real version
  needs authentication, and a foreman should only see their own crew.
- **Worker pay is sent to a third party.** The questions and results go to Groq's servers.
  A real payroll product needs a data processing agreement, or a model that runs in-house.
- **Rates have no history.** If someone's hourly rate changes, questions about past weeks
  will use today's rate. Real payroll stores a rate with the dates it applied from.
- **No automated quality checks on the AI.** There are 40 tests for the code, but none that
  measure whether answers stay correct when the instructions are edited. For an AI feature
  that suite matters as much as unit tests do.
- **The keyword check is crude.** It looks for banned words anywhere in the query, so a
  column named `updated_at` would be wrongly rejected for containing `UPDATE`. Matching
  whole words would fix it.

None of these are hard to add. They were left out to keep the demonstration small enough
to read in one sitting.
