package com.example.retinavision.controller.user;

import com.example.retinavision.pojo.user.vo.DoctorOptionVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.user.DoctorDirectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserDirectoryController {
    private final DoctorDirectoryService doctors;

    public UserDirectoryController(DoctorDirectoryService doctors) {
        this.doctors = doctors;
    }

    @GetMapping("/doctors")
    public Result<List<DoctorOptionVO>> listDoctors() {
        return Result.success(doctors.listActiveDoctors());
    }
}
