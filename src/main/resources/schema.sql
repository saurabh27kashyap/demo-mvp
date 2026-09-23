CREATE TABLE worker (
    id INT AUTO_INCREMENT PRIMARY KEY,
    -- The stable identifier payroll actually uses. Names are not unique (two workers here
    -- are both called Ramesh) and two people can share a name, a trade AND a site, so a
    -- person is never resolved by name alone.
    employee_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    classification VARCHAR(50) NOT NULL,
    hourly_rate DECIMAL(10,2) NOT NULL
);

CREATE TABLE project (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(100) NOT NULL
);

CREATE TABLE shift (
    id INT AUTO_INCREMENT PRIMARY KEY,
    worker_id INT NOT NULL,
    project_id INT NOT NULL,
    work_date DATE NOT NULL,
    -- Same format as pay_record.week ('2026-W37'). Payroll is reconciled per pay week, so the
    -- week label is stored here directly: without it, comparing hours worked against pay paid
    -- would need date-to-ISO-week maths in every query, which is where text-to-SQL goes wrong.
    week VARCHAR(20) NOT NULL,
    hours_worked DECIMAL(5,2) NOT NULL,
    FOREIGN KEY (worker_id) REFERENCES worker(id),
    FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE TABLE pay_record (
    id INT AUTO_INCREMENT PRIMARY KEY,
    worker_id INT NOT NULL,
    week VARCHAR(20) NOT NULL,
    amount_paid DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (worker_id) REFERENCES worker(id),
    -- A worker is paid once per week. Without this, a double payment run would silently
    -- insert a second row and every "was he paid correctly" answer would be wrong.
    UNIQUE (worker_id, week)
);

-- Legal minimum rate per trade, per project (prevailing wage / Davis-Bacon style rule)
CREATE TABLE wage_determination (
    id INT AUTO_INCREMENT PRIMARY KEY,
    classification VARCHAR(50) NOT NULL,
    project_id INT NOT NULL,
    min_hourly_rate DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(id)
);

-- Every payroll question filters shifts by worker and pay week, so that pair is indexed.
-- At 70 rows it changes nothing; at a real company's volume it is the difference between
-- a full table scan and an index lookup on every single question asked.
CREATE INDEX idx_shift_worker_week ON shift(worker_id, week);

-- Restricted DB user - LLM-generated SQL runs as THIS user, not the admin 'sa' user.
-- Only SELECT is granted, so even if the app-level safety check ever fails, the
-- database itself refuses any INSERT/UPDATE/DELETE/DROP.
CREATE USER readonly_agent PASSWORD 'agent_readonly_pw';
GRANT SELECT ON worker TO readonly_agent;
GRANT SELECT ON project TO readonly_agent;
GRANT SELECT ON shift TO readonly_agent;
GRANT SELECT ON pay_record TO readonly_agent;
GRANT SELECT ON wage_determination TO readonly_agent;
