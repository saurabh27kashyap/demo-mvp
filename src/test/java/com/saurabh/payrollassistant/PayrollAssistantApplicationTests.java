package com.saurabh.payrollassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PayrollAssistantApplicationTests {

    @Test
    void contextLoads() {
        // Just checks that the Spring context builds without error - all beans wire up
        // correctly. This exact test would have immediately caught our earlier
        // @Bean DataSource mistake, without needing to run the app manually.
    }
}
