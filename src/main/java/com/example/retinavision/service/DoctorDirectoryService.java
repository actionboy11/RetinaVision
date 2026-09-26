package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.DoctorOptionVO;

import java.util.List;

public interface DoctorDirectoryService {
    List<DoctorOptionVO> listActiveDoctors();
}
