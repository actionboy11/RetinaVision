package com.example.retinavision.service.user;

import com.example.retinavision.pojo.VO.DoctorOptionVO;

import java.util.List;

public interface DoctorDirectoryService {
    List<DoctorOptionVO> listActiveDoctors();
}
