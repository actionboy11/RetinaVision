package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CorrectionStatus;
import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.exception.ConflictException;
import com.example.retinavision.mapper.AnalysisCorrectionMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.DTO.SubmitReviewDTO;
import com.example.retinavision.pojo.Entity.AnalysisCorrectionEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.service.ResultReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 医生审核服务。
 *
 * <p>这个类负责把“医生对结果的专业判断”保存到 analysis_review。
 * 它不处理 PDF 生成，PDF 签发由 AnalysisReportServiceImpl 完成。</p>
 */
@Service
public class ResultReviewServiceImpl implements ResultReviewService {

    private final AnalysisResultMapper resultMapper;
    private final AnalysisReviewMapper reviewMapper;
    private final UserRegisterMapper userMapper;
    private final AnalysisCorrectionMapper correctionMapper;

    @Autowired
    public ResultReviewServiceImpl(AnalysisResultMapper resultMapper,
                                   AnalysisReviewMapper reviewMapper,
                                   UserRegisterMapper userMapper,
                                   AnalysisCorrectionMapper correctionMapper) {
        this.resultMapper = resultMapper;
        this.reviewMapper = reviewMapper;
        this.userMapper = userMapper;
        this.correctionMapper = correctionMapper;
    }

    /**
     * 测试兼容构造器：某些旧单元测试可能还没有传 correctionMapper。
     */
    public ResultReviewServiceImpl(AnalysisResultMapper resultMapper,
                                   AnalysisReviewMapper reviewMapper,
                                   UserRegisterMapper userMapper) {
        this(resultMapper, reviewMapper, userMapper, null);
    }

    /**
     * 查询某个 resultId 的当前审核记录。
     */
    @Override
    public AnalysisReviewEntity getReview(Long resultId) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<AnalysisReviewEntity>()
                .eq(AnalysisReviewEntity::getResultId, resultId));
    }

    /**
     * 保存医生审核结论。
     *
     * <p>核心设计点：
     * 1. resultId 必须真实存在；
     * 2. doctorId 必须对应有 professionalNo 的可信医生；
     * 3. 如果选择 correctionVersion，必须确认该修正版本存在；
     * 4. 用 expectedVersion 做乐观锁，避免两个医生并发覆盖；
     * 5. 审核通过/拒绝时，同步更新被采用修正版本的状态。</p>
     */
    @Override
    @Transactional
    public AnalysisReviewEntity review(Long resultId, SubmitReviewDTO request, Integer doctorId) {
        if (resultMapper.selectById(resultId) == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析结果不存在");
        }
        if (request == null || request.getStatus() == null || request.getExpectedVersion() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "审核状态和 expectedVersion 不能为空");
        }

        UserEntity doctor = userMapper.selectById(doctorId);
        if (doctor == null || doctor.getProfessionalNo() == null) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "缺少可信医生身份");
        }

        AnalysisCorrectionEntity selectedCorrection = findSelectedCorrection(resultId, request.getCorrectionVersion());
        AnalysisReviewEntity current = getReview(resultId);
        LocalDateTime now = LocalDateTime.now();

        if (current == null) {
            // 第一次审核时，前端 expectedVersion 必须为 0。
            if (request.getExpectedVersion() != 0) {
                throw conflict();
            }
            AnalysisReviewEntity created = fill(new AnalysisReviewEntity(), resultId, request, doctorId, doctor, 1, now);
            try {
                reviewMapper.insert(created);
            } catch (DuplicateKeyException duplicate) {
                throw conflict();
            }
            updateCorrectionStatus(selectedCorrection, request);
            return created;
        }

        if (!current.getVersion().equals(request.getExpectedVersion())) {
            throw conflict();
        }

        // 带 version 条件更新，是乐观锁真正生效的地方。
        int updated = reviewMapper.update(null, new LambdaUpdateWrapper<AnalysisReviewEntity>()
                .eq(AnalysisReviewEntity::getId, current.getId())
                .eq(AnalysisReviewEntity::getVersion, current.getVersion())
                .set(AnalysisReviewEntity::getCorrectionVersion, request.getCorrectionVersion())
                .set(AnalysisReviewEntity::getStatus, request.getStatus())
                .set(AnalysisReviewEntity::getFindings, request.getFindings())
                .set(AnalysisReviewEntity::getConclusion, request.getConclusion())
                .set(AnalysisReviewEntity::getRecommendation, request.getRecommendation())
                .set(AnalysisReviewEntity::getReviewerId, doctorId)
                .set(AnalysisReviewEntity::getReviewerNameSnapshot, doctor.getRealName())
                .set(AnalysisReviewEntity::getProfessionalNoSnapshot, doctor.getProfessionalNo())
                .set(AnalysisReviewEntity::getReviewedAt, now)
                .set(AnalysisReviewEntity::getUpdatedAt, now)
                .set(AnalysisReviewEntity::getVersion, current.getVersion() + 1));
        if (updated != 1) {
            throw conflict();
        }

        updateCorrectionStatus(selectedCorrection, request);
        return fill(current, resultId, request, doctorId, doctor, current.getVersion() + 1, now);
    }

    /**
     * 查找医生本次选择采用的修正版本。
     */
    private AnalysisCorrectionEntity findSelectedCorrection(Long resultId, Integer correctionVersion) {
        if (correctionVersion == null || correctionMapper == null) {
            return null;
        }
        AnalysisCorrectionEntity correction = correctionMapper.selectOne(new LambdaQueryWrapper<AnalysisCorrectionEntity>()
                .eq(AnalysisCorrectionEntity::getResultId, resultId)
                .eq(AnalysisCorrectionEntity::getVersion, correctionVersion));
        if (correction == null) {
            throw new ConflictException("选择的修正版本不存在");
        }
        return correction;
    }

    /**
     * 把请求和医生身份快照写入审核实体。
     */
    private AnalysisReviewEntity fill(AnalysisReviewEntity review,
                                      Long resultId,
                                      SubmitReviewDTO request,
                                      Integer doctorId,
                                      UserEntity doctor,
                                      int version,
                                      LocalDateTime now) {
        review.setResultId(resultId);
        review.setCorrectionVersion(request.getCorrectionVersion());
        review.setStatus(request.getStatus());
        review.setFindings(request.getFindings());
        review.setConclusion(request.getConclusion());
        review.setRecommendation(request.getRecommendation());
        review.setReviewerId(doctorId);
        review.setReviewerNameSnapshot(doctor.getRealName());
        review.setProfessionalNoSnapshot(doctor.getProfessionalNo());
        review.setVersion(version);
        review.setReviewedAt(now);
        if (review.getCreatedAt() == null) {
            review.setCreatedAt(now);
        }
        review.setUpdatedAt(now);
        return review;
    }

    private BaseException conflict() {
        return new ConflictException("审核版本已变化，请刷新后重试");
    }

    /**
     * 当医生明确通过或拒绝时，同步改变修正版本状态。
     *
     * <p>这样报告签发时就能判断：只有 ACCEPTED 修正版本才能被正式报告采用。</p>
     */
    private void updateCorrectionStatus(AnalysisCorrectionEntity correction, SubmitReviewDTO request) {
        if (correction == null || correctionMapper == null) {
            return;
        }

        if (request.getStatus() == ReviewStatus.APPROVED) {
            correction.setStatus(CorrectionStatus.ACCEPTED);
        } else if (request.getStatus() == ReviewStatus.REJECTED) {
            correction.setStatus(CorrectionStatus.REJECTED);
        } else {
            return;
        }

        correction.setUpdatedAt(LocalDateTime.now());
        correctionMapper.updateById(correction);
    }
}
