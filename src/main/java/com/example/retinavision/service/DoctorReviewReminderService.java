package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.DoctorReviewReminderVO;
import com.example.retinavision.pojo.VO.CurrentUserVO;

public interface DoctorReviewReminderService {

    DoctorReviewReminderVO getReminderSummary(CurrentUserVO doctor);
}
