package com.saurabh.payrollassistant.web;

import com.saurabh.payrollassistant.web.QueryController.QueryRequest;
import com.saurabh.payrollassistant.agent.PayrollAgent;
import com.saurabh.payrollassistant.data.DatabaseSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Input validation has to happen BEFORE the agent runs: everything past this point costs
// tokens, so a junk question must be rejected without ever reaching the model.
class QueryControllerTest {

    private final PayrollAgent agent = mock(PayrollAgent.class);
    private final QueryController controller = new QueryController(agent, mock(DatabaseSchema.class));

    @Test
    void passesAValidQuestionToTheAgent() {
        when(agent.ask(any())).thenReturn(new QueryResponse("q", "an answer", List.of(), 0, 0, 0.0, 0));

        QueryResponse response = controller.query(new QueryRequest("Who works on Site C?"));

        assertThat(response.answer()).isEqualTo("an answer");
        verify(agent).ask("Who works on Site C?");
    }

    @Test
    void rejectsABlankQuestionWithoutCallingTheModel() {
        assertThatThrownBy(() -> controller.query(new QueryRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(agent, never()).ask(any());
    }

    @Test
    void rejectsAMissingQuestionWithoutCallingTheModel() {
        assertThatThrownBy(() -> controller.query(new QueryRequest(null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(agent, never()).ask(any());
    }

    // An oversized "question" is not a question - it is a way to run up the token bill
    @Test
    void rejectsAnOversizedQuestionWithoutCallingTheModel() {
        assertThatThrownBy(() -> controller.query(new QueryRequest("a".repeat(501))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(agent, never()).ask(any());
    }
}
