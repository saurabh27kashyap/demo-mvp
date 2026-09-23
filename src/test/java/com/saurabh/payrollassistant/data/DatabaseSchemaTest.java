package com.saurabh.payrollassistant.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// This is everything the model is told about the database. If any of it goes missing the
// model starts guessing, which is exactly how the "Site C" bug happened - so it is asserted.
@SpringBootTest
class DatabaseSchemaTest {

    @Autowired
    private DatabaseSchema databaseSchema;

    @Test
    void listsEveryTableWithItsColumns() {
        assertThat(databaseSchema.describeForModel())
                .contains("worker(id, employee_code, name, classification, hourly_rate)")
                .contains("shift(id, worker_id, project_id, work_date, week_starting, hours_worked)")
                .contains("pay_record(id, worker_id, week_starting, amount_paid)")
                .contains("wage_determination(id, classification, project_id, min_hourly_rate)");
    }

    // Without the real values, the model cannot know that 'Site C' is a project NAME and not
    // a location, and it silently queries the wrong column
    @Test
    void listsTheRealValuesOfTextAndDateColumnsSoTheModelNeverGuesses() {
        assertThat(databaseSchema.describeForModel())
                .contains("name values: Site A | Site B | Site C")
                .contains("Thane")
                .contains("classification values: Carpenter | Electrician | Laborer | Plumber")
                .contains("week_starting values: 2026-09-07 | 2026-09-14");
    }

    // Derived from the database, not hand-written, so a new table can't leave them stale
    @Test
    void derivesForeignKeysFromTheDatabase() {
        assertThat(databaseSchema.describeForModel())
                .contains("shift.worker_id -> worker.id")
                .contains("shift.project_id -> project.id")
                .contains("pay_record.worker_id -> worker.id")
                .contains("wage_determination.project_id -> project.id");
    }
}
