package com.example.retinavision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.retinavision.pojo.Entity.PatientProfileEntity;
import com.example.retinavision.pojo.VO.LegacyPatientProfileVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PatientProfileMapper extends BaseMapper<PatientProfileEntity> {
    @Select("SELECT * FROM patient_profile WHERE account_user_id = #{userId} AND status = 'ACTIVE' LIMIT 1")
    PatientProfileEntity selectByAccountUserId(@Param("userId") Integer userId);

    @Select("SELECT * FROM patient_profile WHERE patient_no = #{patientNo} LIMIT 1")
    PatientProfileEntity selectByPatientNo(@Param("patientNo") String patientNo);

    @Select("""
            SELECT DISTINCT p.* FROM patient_profile p
            LEFT JOIN medical_case c ON c.patient_id = p.id AND c.status != 'DELETED'
            WHERE p.status = 'ACTIVE'
              AND (p.created_by = #{doctorId} OR c.assigned_doctor_id = #{doctorId})
            ORDER BY p.updated_at DESC, p.id DESC
            """)
    List<PatientProfileEntity> selectAccessibleByDoctor(@Param("doctorId") Integer doctorId);

    @Select("""
            SELECT p.id, p.patient_no AS patientNo, p.legacy_patient_code AS legacyPatientCode,
                   p.account_user_id AS accountUserId, u.username AS accountUsername,
                   COUNT(c.id) AS caseCount, p.created_at AS createdAt
            FROM patient_profile p
            LEFT JOIN sys_user u ON u.id = p.account_user_id
            LEFT JOIN medical_case c ON c.patient_id = p.id AND c.status != 'DELETED'
            WHERE p.source = 'LEGACY' AND p.status = 'ACTIVE'
            GROUP BY p.id, p.patient_no, p.legacy_patient_code, p.account_user_id, u.username, p.created_at
            ORDER BY p.created_at ASC, p.id ASC
            """)
    List<LegacyPatientProfileVO> selectLegacyProfiles();
}
