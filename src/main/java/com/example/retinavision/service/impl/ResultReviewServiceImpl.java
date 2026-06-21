package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.exception.ConflictException;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.mapper.AnalysisCorrectionMapper;
import com.example.retinavision.pojo.Entity.AnalysisCorrectionEntity;
import com.example.retinavision.enumeration.CorrectionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.retinavision.pojo.DTO.SubmitReviewDTO;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.service.ResultReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;

@Service
public class ResultReviewServiceImpl implements ResultReviewService {
    private final AnalysisResultMapper resultMapper;
    private final AnalysisReviewMapper reviewMapper;
    private final UserRegisterMapper userMapper;
    private final AnalysisCorrectionMapper correctionMapper;
    @Autowired public ResultReviewServiceImpl(AnalysisResultMapper resultMapper, AnalysisReviewMapper reviewMapper, UserRegisterMapper userMapper, AnalysisCorrectionMapper correctionMapper) {
        this.resultMapper=resultMapper; this.reviewMapper=reviewMapper; this.userMapper=userMapper; this.correctionMapper=correctionMapper;
    }
    public ResultReviewServiceImpl(AnalysisResultMapper resultMapper, AnalysisReviewMapper reviewMapper, UserRegisterMapper userMapper) { this(resultMapper,reviewMapper,userMapper,null); }
    @Override public AnalysisReviewEntity getReview(Long resultId) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<AnalysisReviewEntity>().eq(AnalysisReviewEntity::getResultId,resultId));
    }
    @Override @Transactional
    public AnalysisReviewEntity review(Long resultId, SubmitReviewDTO request, Integer doctorId) {
        if (resultMapper.selectById(resultId)==null) throw new BaseException(ErrorMessageSignal.NOT_FOUND,"分析结果不存在");
        if (request==null || request.getStatus()==null || request.getExpectedVersion()==null)
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"审核状态和 expectedVersion 不能为空");
        UserEntity doctor=userMapper.selectById(doctorId);
        if (doctor==null || doctor.getProfessionalNo()==null)
            throw new BaseException(ErrorMessageSignal.FORBIDDEN,"缺少可信医生身份");
        AnalysisCorrectionEntity selectedCorrection = null;
        if (request.getCorrectionVersion() != null && correctionMapper != null) {
            selectedCorrection = correctionMapper.selectOne(new LambdaQueryWrapper<AnalysisCorrectionEntity>()
                    .eq(AnalysisCorrectionEntity::getResultId, resultId)
                    .eq(AnalysisCorrectionEntity::getVersion, request.getCorrectionVersion()));
            if (selectedCorrection == null) throw new ConflictException("选择的修正版本不存在");
        }
        AnalysisReviewEntity current=getReview(resultId);
        LocalDateTime now=LocalDateTime.now();
        if (current==null) {
            if (request.getExpectedVersion()!=0) throw conflict();
            AnalysisReviewEntity created=values(new AnalysisReviewEntity(), resultId, request, doctorId, doctor, 1, now);
            try { reviewMapper.insert(created); } catch (DuplicateKeyException duplicate) { throw conflict(); }
            updateCorrectionStatus(selectedCorrection, request); return created;
        }
        if (!current.getVersion().equals(request.getExpectedVersion())) throw conflict();
        int updated=reviewMapper.update(null,new LambdaUpdateWrapper<AnalysisReviewEntity>()
                .eq(AnalysisReviewEntity::getId,current.getId()).eq(AnalysisReviewEntity::getVersion,current.getVersion())
                .set(AnalysisReviewEntity::getCorrectionVersion,request.getCorrectionVersion())
                .set(AnalysisReviewEntity::getStatus,request.getStatus())
                .set(AnalysisReviewEntity::getFindings,request.getFindings())
                .set(AnalysisReviewEntity::getConclusion,request.getConclusion())
                .set(AnalysisReviewEntity::getRecommendation,request.getRecommendation())
                .set(AnalysisReviewEntity::getReviewerId,doctorId)
                .set(AnalysisReviewEntity::getReviewerNameSnapshot,doctor.getRealName())
                .set(AnalysisReviewEntity::getProfessionalNoSnapshot,doctor.getProfessionalNo())
                .set(AnalysisReviewEntity::getReviewedAt,now).set(AnalysisReviewEntity::getUpdatedAt,now)
                .set(AnalysisReviewEntity::getVersion,current.getVersion()+1));
        if(updated!=1) throw conflict();
        updateCorrectionStatus(selectedCorrection, request);
        return values(current,resultId,request,doctorId,doctor,current.getVersion()+1,now);
    }
    private AnalysisReviewEntity values(AnalysisReviewEntity x,Long resultId,SubmitReviewDTO r,Integer id,UserEntity d,int version,LocalDateTime now){
        x.setResultId(resultId); x.setCorrectionVersion(r.getCorrectionVersion()); x.setStatus(r.getStatus());
        x.setFindings(r.getFindings()); x.setConclusion(r.getConclusion()); x.setRecommendation(r.getRecommendation());
        x.setReviewerId(id); x.setReviewerNameSnapshot(d.getRealName()); x.setProfessionalNoSnapshot(d.getProfessionalNo());
        x.setVersion(version); x.setReviewedAt(now); if(x.getCreatedAt()==null)x.setCreatedAt(now); x.setUpdatedAt(now); return x;
    }
    private BaseException conflict(){return new ConflictException("审核版本已变化，请刷新后重试");}
    private void updateCorrectionStatus(AnalysisCorrectionEntity correction, SubmitReviewDTO request) {
        if (correction == null || correctionMapper == null) return;
        if (request.getStatus() == com.example.retinavision.enumeration.ReviewStatus.APPROVED) correction.setStatus(CorrectionStatus.ACCEPTED);
        else if (request.getStatus() == com.example.retinavision.enumeration.ReviewStatus.REJECTED) correction.setStatus(CorrectionStatus.REJECTED);
        else return;
        correction.setUpdatedAt(LocalDateTime.now()); correctionMapper.updateById(correction);
    }
}
