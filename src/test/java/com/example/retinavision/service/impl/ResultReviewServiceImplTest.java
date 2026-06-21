package com.example.retinavision.service.impl;

import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.DTO.SubmitReviewDTO;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultReviewServiceImplTest {
    private final AnalysisResultMapper resultMapper = mock(AnalysisResultMapper.class);
    private final AnalysisReviewMapper reviewMapper = mock(AnalysisReviewMapper.class);
    private final UserRegisterMapper userMapper = mock(UserRegisterMapper.class);
    private final ResultReviewServiceImpl service = new ResultReviewServiceImpl(resultMapper, reviewMapper, userMapper);

    @Test
    void firstReviewCapturesDoctorIdentitySnapshot() {
        when(resultMapper.selectById(3L)).thenReturn(new AnalysisResultEntity());
        UserEntity doctor = new UserEntity();
        doctor.setRealName("张医生");
        doctor.setProfessionalNo("DOC-001");
        when(userMapper.selectById(7)).thenReturn(doctor);

        service.review(3L, new SubmitReviewDTO(null, ReviewStatus.APPROVED,
                "所见", "结论", "建议", 0), 7);

        ArgumentCaptor<AnalysisReviewEntity> captor = ArgumentCaptor.forClass(AnalysisReviewEntity.class);
        verify(reviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getReviewerNameSnapshot()).isEqualTo("张医生");
        assertThat(captor.getValue().getProfessionalNoSnapshot()).isEqualTo("DOC-001");
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
    }

    @Test
    void staleExpectedVersionIsRejected() {
        when(resultMapper.selectById(3L)).thenReturn(new AnalysisResultEntity());
        UserEntity doctor = new UserEntity();
        doctor.setProfessionalNo("DOC-001");
        when(userMapper.selectById(7)).thenReturn(doctor);
        AnalysisReviewEntity existing = new AnalysisReviewEntity();
        existing.setId(9L);
        existing.setVersion(2);
        when(reviewMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> service.review(3L,
                new SubmitReviewDTO(null, ReviewStatus.APPROVED, null, null, null, 1), 7))
                .isInstanceOf(BaseException.class);
    }
}
