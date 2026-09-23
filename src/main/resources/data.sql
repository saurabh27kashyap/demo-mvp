INSERT INTO worker (employee_code, name, classification, hourly_rate) VALUES ('EMP-001', 'Ramesh', 'Electrician', 250.00);
INSERT INTO worker (employee_code, name, classification, hourly_rate) VALUES ('EMP-002', 'Suresh', 'Laborer', 150.00);
INSERT INTO worker (employee_code, name, classification, hourly_rate) VALUES ('EMP-003', 'Mahesh', 'Carpenter', 200.00);
INSERT INTO worker (employee_code, name, classification, hourly_rate) VALUES ('EMP-004', 'Dinesh', 'Electrician', 260.00);
INSERT INTO worker (employee_code, name, classification, hourly_rate) VALUES ('EMP-005', 'Naresh', 'Laborer', 145.00);
INSERT INTO worker (employee_code, name, classification, hourly_rate) VALUES ('EMP-006', 'Yogesh', 'Plumber', 220.00);
INSERT INTO worker (employee_code, name, classification, hourly_rate) VALUES ('EMP-007', 'Ramesh', 'Laborer', 160.00);  -- intentional duplicate name, for testing ambiguity handling

INSERT INTO project (name, location) VALUES ('Site A', 'Andheri, Mumbai');
INSERT INTO project (name, location) VALUES ('Site B', 'Powai, Mumbai');
INSERT INTO project (name, location) VALUES ('Site C', 'Thane');

-- ===== Pay week starting Monday 7 Sep 2026 (worked 8 Sep - 12 Sep) =====
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-08', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-09', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-10', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-11', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-12', '2026-09-07', 8);

INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-08', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-09', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-10', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-11', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-12', '2026-09-07', 8);

INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (3, 1, '2026-09-08', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (3, 1, '2026-09-09', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (3, 1, '2026-09-10', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (3, 1, '2026-09-11', '2026-09-07', 7);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (3, 1, '2026-09-12', '2026-09-07', 7);

INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-08', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-09', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-10', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-11', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-12', '2026-09-07', 8);

-- Naresh only showed up for 3 days this week
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (5, 2, '2026-09-08', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (5, 2, '2026-09-09', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (5, 2, '2026-09-10', '2026-09-07', 8);

INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (6, 3, '2026-09-08', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (6, 3, '2026-09-09', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (6, 3, '2026-09-10', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (6, 3, '2026-09-11', '2026-09-07', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (6, 3, '2026-09-12', '2026-09-07', 8);

-- ===== Pay week starting Monday 14 Sep 2026 (worked 15 Sep - 19 Sep) =====
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-15', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-16', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-17', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-18', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (1, 1, '2026-09-19', '2026-09-14', 6);

INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-15', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-16', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-17', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-18', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (2, 2, '2026-09-19', '2026-09-14', 8);

-- Mahesh worked part-time this week (only 20 hours)
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (3, 1, '2026-09-15', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (3, 1, '2026-09-16', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (3, 1, '2026-09-17', '2026-09-14', 4);

INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-15', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-16', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-17', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-18', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (4, 3, '2026-09-19', '2026-09-14', 8);

INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (5, 2, '2026-09-15', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (5, 2, '2026-09-16', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (5, 2, '2026-09-17', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (5, 2, '2026-09-18', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (5, 2, '2026-09-19', '2026-09-14', 8);

-- Yogesh took Friday off
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (6, 3, '2026-09-15', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (6, 3, '2026-09-16', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (6, 3, '2026-09-17', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (6, 3, '2026-09-18', '2026-09-14', 8);

-- Second Ramesh (Laborer, id=7) - full week on Site B
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (7, 2, '2026-09-15', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (7, 2, '2026-09-16', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (7, 2, '2026-09-17', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (7, 2, '2026-09-18', '2026-09-14', 8);
INSERT INTO shift (worker_id, project_id, work_date, week_starting, hours_worked) VALUES (7, 2, '2026-09-19', '2026-09-14', 8);

-- ===== Pay records: W37 (some correct, some intentionally underpaid) =====
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (1, '2026-09-07', 10000.00); -- 40*250=10000 (correct)
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (2, '2026-09-07', 6000.00);   -- 40*150=6000 (correct)
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (3, '2026-09-07', 7300.00);   -- 38*200=7600, short by 300
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (4, '2026-09-07', 10400.00);  -- 40*260=10400 (correct)
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (5, '2026-09-07', 3480.00);   -- 24*145=3480 (correct, fewer hours because fewer days worked)
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (6, '2026-09-07', 8500.00);   -- 40*220=8800, short by 300

-- ===== Pay records: W38 =====
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (1, '2026-09-14', 9000.00);   -- 38*250=9500, short by 500
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (2, '2026-09-14', 6000.00);   -- 40*150=6000 (correct)
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (3, '2026-09-14', 3800.00);   -- 20*200=4000, short by 200
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (4, '2026-09-14', 10400.00);  -- 40*260=10400 (correct)
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (5, '2026-09-14', 5800.00);   -- 40*145=5800 (correct)
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (6, '2026-09-14', 7040.00);   -- 32*220=7040 (correct)
INSERT INTO pay_record (worker_id, week_starting, amount_paid) VALUES (7, '2026-09-14', 6400.00);   -- second Ramesh: 40*160=6400 (correct)

-- ===== Wage determinations: legal minimum rate per trade, per project =====
INSERT INTO wage_determination (classification, project_id, min_hourly_rate) VALUES ('Electrician', 1, 240.00); -- Ramesh (250) - compliant
INSERT INTO wage_determination (classification, project_id, min_hourly_rate) VALUES ('Carpenter', 1, 210.00);   -- Mahesh (200) - VIOLATION, his own rate is below the legal minimum
INSERT INTO wage_determination (classification, project_id, min_hourly_rate) VALUES ('Laborer', 2, 140.00);     -- Suresh (150), Naresh (145) - both compliant
INSERT INTO wage_determination (classification, project_id, min_hourly_rate) VALUES ('Electrician', 3, 255.00); -- Dinesh (260) - compliant
INSERT INTO wage_determination (classification, project_id, min_hourly_rate) VALUES ('Plumber', 3, 225.00);     -- Yogesh (220) - VIOLATION
