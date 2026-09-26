package com.example.retinavision.controller;

import com.example.retinavision.enumeration.UserRole;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.llm.RagEvaluationService;
import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RagEvaluationControllerTest {
    private final RagEvaluationService service = mock(RagEvaluationService.class);
    private final RagEvaluationController controller = new RagEvaluationController(service, new ObjectMapper());

    @Test
    void onlyAdminCanStartRuns() {
        BaseException error = catchThrowableOfType(() -> controller.start(
                new PromptEvaluationController.StartRequest(2L), auth(UserRole.USER)), BaseException.class);
        assertThat(error.getCode()).isEqualTo(40300);
        verify(service, never()).start(2L, 1);
    }

    @Test
    void doctorCannotReviewRuns() {
        BaseException error = catchThrowableOfType(() -> controller.review(1L,
                new PromptEvaluationController.ReviewRequest(true, 5, "ok"), auth(UserRole.DOCTOR)),
                BaseException.class);
        assertThat(error.getCode()).isEqualTo(40300);
        verify(service, never()).review(1L, true, 5, "ok", 1);
    }

    private TestingAuthenticationToken auth(UserRole role) {
        return new TestingAuthenticationToken(new CurrentUserVO(1, "user", "User", role), null);
    }
}
