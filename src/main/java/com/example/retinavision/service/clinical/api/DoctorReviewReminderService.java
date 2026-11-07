package com.example.retinavision.service.clinical.api;

import com.example.retinavision.pojo.clinical.vo.DoctorReviewReminderVO;
import com.example.retinavision.pojo.auth.vo.CurrentUserVO;

public interface DoctorReviewReminderService {

    DoctorReviewReminderVO getReminderSummary(CurrentUserVO doctor);
}
