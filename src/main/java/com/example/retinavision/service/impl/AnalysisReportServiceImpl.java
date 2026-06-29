package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CorrectionStatus;
import com.example.retinavision.enumeration.ReportStatus;
import com.example.retinavision.enumeration.ReviewStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.exception.ConflictException;
import com.example.retinavision.mapper.AnalysisCorrectionMapper;
import com.example.retinavision.mapper.AnalysisReportMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.mapper.AnalysisReviewMapper;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.TaskMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.DTO.UpdateReportDraftDTO;
import com.example.retinavision.pojo.Entity.AnalysisCorrectionEntity;
import com.example.retinavision.pojo.Entity.AnalysisReportEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.pojo.Entity.AnalysisReviewEntity;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.TaskEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.service.AnalysisReportService;
import com.example.retinavision.service.ReportPdfDocument;
import com.example.retinavision.service.ReportPdfRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析报告服务。
 *
 * <p>它负责报告生命周期：
 * 创建草稿 → 修改草稿 → 医生签发 PDF → 查询版本 → 下载正式文件。</p>
 *
 * <p>几个重要业务约束：
 * 1. 只有审核通过的结果才能签发；
 * 2. 已签发 PDF 不再修改；
 * 3. 新签发版本会把旧 SIGNED 标记为 SUPERSEDED；
 * 4. 文件路径必须限制在结果目录内；
 * 5. 签发时保存医生姓名和 professionalNo 快照，保证历史报告可审计。</p>
 */
@Service
public class AnalysisReportServiceImpl implements AnalysisReportService {

    private static final String REPORT_TITLE = "RetinaVision 眼底图像 AI 辅助分析报告";
    private static final String DISCLAIMER = "AI 辅助分析，不等同于独立医学诊断。";
    private final AnalysisResultMapper results;
    private final AnalysisReportMapper reports;
    private final AnalysisReviewMapper reviews;
    private final UserRegisterMapper users;
    private final ReportPdfRenderer renderer;
    private final Path root;
    private final Path imageRoot;
    private final TaskMapper tasks;
    private final CaseMapper cases;
    private final ImageMapper images;
    private final AnalysisCorrectionMapper corrections;
    // Jackson JSON 解析器，用于处理报告草稿和 AI 结果的 JSON 数据
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public AnalysisReportServiceImpl(AnalysisResultMapper results,
                                     AnalysisReportMapper reports,
                                     AnalysisReviewMapper reviews,
                                     UserRegisterMapper users,
                                     ReportPdfRenderer renderer,
                                     TaskMapper tasks,
                                     CaseMapper cases,
                                     ImageMapper images,
                                     AnalysisCorrectionMapper corrections,
                                     @Value("${retina.upload.result-root:uploads/results}") String root,
                                     @Value("${retina.upload.image-root:uploads/images}") String imageRoot) {
        this.results = results;
        this.reports = reports;
        this.reviews = reviews;
        this.users = users;
        this.renderer = renderer;
        this.tasks = tasks;
        this.cases = cases;
        this.images = images;
        this.corrections = corrections;
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.imageRoot = Paths.get(imageRoot).toAbsolutePath().normalize();
    }

    /**
     * 测试兼容构造器：旧测试只关心报告核心 mapper，不需要病例、图像、修正版本信息。
     */
    public AnalysisReportServiceImpl(AnalysisResultMapper results,
                                     AnalysisReportMapper reports,
                                     AnalysisReviewMapper reviews,
                                     UserRegisterMapper users,
                                     ReportPdfRenderer renderer,
                                     String root) {
        this(results, reports, reviews, users, renderer, null, null, null, null, root, "uploads/images");
    }

    /**
     * 获取当前草稿；如果没有草稿，则基于 AI 结果创建一个默认草稿。
     *
     * <p>这里不会直接生成 PDF。PDF 只在医生执行 sign 时生成。</p>
     */
    @Override
    @Transactional
    public AnalysisReportEntity getOrCreateDraft(Long resultId, Integer userId) {
        AnalysisResultEntity result = requireResult(resultId);
        //reduce((first, second) -> second) 取最后一个草稿版本，如果没有草稿则创建一个新的草稿
        return list(resultId).stream()
                .filter(report -> report.getStatus() == ReportStatus.DRAFT)
                .reduce((first, second) -> second)
                .orElseGet(() -> createDraft(result, userId));
    }

    /**
     * 更新报告草稿。
     *
     * <p>如果草稿选择了 correctionVersion，必须确认该修正版本已经被医生审核接受；
     * 否则不能把未采纳的修正写入正式报告。</p>
     */
    @Override
    @Transactional
    public AnalysisReportEntity updateDraft(Long resultId, UpdateReportDraftDTO request, Integer userId)
    {
        AnalysisReportEntity report = getOrCreateDraft(resultId, userId);
        if (report.getStatus() != ReportStatus.DRAFT) {
            throw conflict("已签发报告不可修改");
        }

        try {
            json.readTree(request.getDraftJson());
        } catch (Exception exception) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "报告草稿必须是合法 JSON");
        }

        if (request.getCorrectionVersion() != null) {
            AnalysisCorrectionEntity correction = findCorrection(resultId, request.getCorrectionVersion());
            if (correction == null || correction.getStatus() != CorrectionStatus.ACCEPTED) {
                throw conflict("报告只能采用医生审核接受的修正版本");
            }
        }

        report.setCorrectionVersion(request.getCorrectionVersion());
        report.setDraftJson(request.getDraftJson());
        report.setUpdatedAt(LocalDateTime.now());
        reports.updateById(report);
        return report;
    }

    /**
     * 医生签发正式 PDF 报告。
     *
     * <p>关键步骤：
     * 1. 检查审核记录必须是 APPROVED；
     * 2. 检查签发人必须有 professionalNo；
     * 3. 渲染 PDF 到临时文件；
     * 4. 计算 SHA-256；
     * 5. 移动为正式 PDF；
     * 6. 旧正式报告标记为 SUPERSEDED；
     * 7. 当前草稿标记为 SIGNED 并保存签发快照。
     *</p>
     */
    @Override
    @Transactional
    public AnalysisReportEntity sign(Long resultId, Integer doctorId) {
        AnalysisReviewEntity review = reviews.selectOne(new LambdaQueryWrapper<AnalysisReviewEntity>()
                .eq(AnalysisReviewEntity::getResultId, resultId));
        if (review == null || review.getStatus() != ReviewStatus.APPROVED) {
            throw conflict("只有审核通过的结果才能签发报告");
        }

        UserEntity doctor = users.selectById(doctorId);
        if (doctor == null || doctor.getProfessionalNo() == null) {
            throw new BaseException(ErrorMessageSignal.FORBIDDEN, "缺少可信医生身份");
        }

        AnalysisReportEntity report = getOrCreateDraft(resultId, doctorId);
        String objectKey = "reports/" + resultId + "/v" + report.getVersion() + ".pdf";
        Path finalPath = resolve(objectKey);
        Path tempPath = resolve(objectKey + ".tmp");
        LocalDateTime signedAt = LocalDateTime.now();

        try {
            renderer.render(document(requireResult(resultId), report, review, doctor, signedAt), tempPath);
            String hash = hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(tempPath)));
            Files.createDirectories(finalPath.getParent());
            Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);

            // 同一个 resultId 只保留一个当前 SIGNED 报告；旧正式版本仍保留为 SUPERSEDED。
            for (AnalysisReportEntity old : list(resultId)) {
                if (old.getStatus() == ReportStatus.SIGNED) {
                    old.setStatus(ReportStatus.SUPERSEDED);
                    old.setUpdatedAt(signedAt);
                    reports.updateById(old);
                }
            }

            report.setStatus(ReportStatus.SIGNED);
            report.setReportObjectKey(objectKey);
            report.setReportSha256(hash);
            report.setSignedBy(doctorId);
            report.setSignerNameSnapshot(doctor.getRealName());
            report.setProfessionalNoSnapshot(doctor.getProfessionalNo());
            report.setSignedAt(signedAt);
            report.setUpdatedAt(signedAt);
            if (reports.updateById(report) != 1) {
                throw new IllegalStateException("signed report row was not updated");
            }
            return report;
        } catch (BaseException exception) {
            cleanup(tempPath);
            throw exception;
        } catch (Exception exception) {
            cleanup(tempPath);
            cleanup(finalPath);
            throw new BaseException(ErrorMessageSignal.FILE_STORAGE_ERROR, "报告签发失败");
        }
    }

    /**
     * 查询某个 resultId 的所有报告版本。
     */
    @Override
    public List<AnalysisReportEntity> list(Long resultId) {
        requireResult(resultId);
        return reports.selectList(new LambdaQueryWrapper<AnalysisReportEntity>()
                .eq(AnalysisReportEntity::getResultId, resultId)
                .orderByAsc(AnalysisReportEntity::getVersion));
    }

    /**
     * 查询指定版本报告元数据。
     */
    @Override
    public AnalysisReportEntity get(Long resultId, Integer version) {
        return list(resultId).stream()
                .filter(report -> report.getVersion().equals(version))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorMessageSignal.NOT_FOUND, "报告版本不存在"));
    }

    /**
     * 获取已签发 PDF 文件路径。
     *
     * <p>草稿没有 PDF 文件，不能下载。</p>
     */
    @Override
    public Path getFile(Long resultId, Integer version) {
        AnalysisReportEntity report = get(resultId, version);
        if (report.getStatus() != ReportStatus.SIGNED && report.getStatus() != ReportStatus.SUPERSEDED) {
            throw conflict("报告尚未签发");
        }

        Path path = resolve(report.getReportObjectKey());
        if (!Files.isRegularFile(path)) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "报告文件不存在");
        }
        return path;
    }

    /**
     * 基于 AI 原始结果创建默认草稿。
     */
    private AnalysisReportEntity createDraft(AnalysisResultEntity result, Integer userId) {
        AnalysisReportEntity report = new AnalysisReportEntity();
        LocalDateTime now = LocalDateTime.now();
        report.setResultId(result.getId());
        report.setVersion(next(result.getId()));
        report.setStatus(ReportStatus.DRAFT);
        report.setDraftJson(defaultDraft(result));
        report.setCreatedBy(userId);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        reports.insert(report);
        return report;
    }

    /**
     * 把数据库中的病例、图像、AI 结果、审核和修正版本组装成 PDF 渲染对象。
     *
     * <p>ReportPdfRenderer 只负责“怎么画 PDF”，这个方法负责“PDF 里放什么内容”。</p>
     */
    private ReportPdfDocument document(AnalysisResultEntity result,
                                       AnalysisReportEntity report,
                                       AnalysisReviewEntity review,
                                       UserEntity doctor,
                                       LocalDateTime signedAt) {
        JsonNode draftJson = readTree(report.getDraftJson());
        JsonNode resultJson = readTree(result.getResultJson());
        CaseAndImage caseAndImage = loadCaseAndImage(result);
        AnalysisCorrectionEntity correction = report.getCorrectionVersion() == null
                ? null
                : findCorrection(result.getId(), report.getCorrectionVersion());

        return new ReportPdfDocument(
                REPORT_TITLE,
                "RV-" + result.getId() + "-V" + report.getVersion(),
                report.getVersion(),
                signedAt,
                text(draftJson, "disclaimer", DISCLAIMER),
                caseFields(caseAndImage),
                analysisFields(result, resultJson, caseAndImage.image()),
                doctorFields(review, draftJson, doctor),
                technicalFields(result, report, correction),
                "",
                caseAndImage.originalPath(),
                path(root, result.getMaskObjectKey()),
                correction == null ? null : path(root, correction.getCorrectedMaskObjectKey())
        );
    }

    /**
     * 病例和图像信息区。
     */
    private List<ReportPdfDocument.Field> caseFields(CaseAndImage caseAndImage) {
        List<ReportPdfDocument.Field> fields = new ArrayList<>();
        CaseEntity caseEntity = caseAndImage.caseEntity();
        ImageFileEntity image = caseAndImage.image();
        add(fields, "病例号", caseEntity == null ? null : caseEntity.getCaseNo());
        add(fields, "患者编码", caseEntity == null ? null : caseEntity.getPatientCode());
        add(fields, "眼别", caseEntity == null || caseEntity.getEyeSide() == null ? null : caseEntity.getEyeSide().name());
        add(fields, "年龄", caseEntity == null || caseEntity.getPatientAge() == null ? null : caseEntity.getPatientAge() + " 岁");
        add(fields, "图像质量", image == null || image.getQualityStatus() == null ? "未检测" : image.getQualityStatus().name());
        add(fields, "质量评分", image == null || image.getQualityScore() == null ? null : String.format("%.1f", image.getQualityScore()));
        return fields;
    }

    /**
     * AI 分析信息区。
     */
    private List<ReportPdfDocument.Field> analysisFields(AnalysisResultEntity result,
                                                         JsonNode resultJson,
                                                         ImageFileEntity image) {
        List<ReportPdfDocument.Field> fields = new ArrayList<>();
        add(fields, "结果类型", result.getResultType() == null ? null : result.getResultType().name());
        add(fields, "AI 结论", firstNonBlank(
                text(resultJson, "conclusion", null),
                "已完成视网膜血管分割，结果仅供辅助分析"
        ));
        add(fields, "血管面积比例", text(resultJson, "vesselAreaRatio", null));
        add(fields, "原图尺寸", image == null || image.getImageWidth() == null || image.getImageHeight() == null
                ? null
                : image.getImageWidth() + " x " + image.getImageHeight());
        add(fields, "模型名称", result.getModelName());
        add(fields, "模型版本", firstNonBlank(result.getModelVersion(), text(resultJson, "modelVersion", null)));
        add(fields, "处理耗时", result.getProcessingTimeMs() == null ? null : result.getProcessingTimeMs() + " ms");
        return fields;
    }

    /**
     * 医生审核信息区。
     */
    private List<ReportPdfDocument.Field> doctorFields(AnalysisReviewEntity review,
                                                       JsonNode draftJson,
                                                       UserEntity doctor) {
        List<ReportPdfDocument.Field> fields = new ArrayList<>();
        add(fields, "审核医生", doctor.getRealName());
        add(fields, "医生编号", doctor.getProfessionalNo());
        add(fields, "医生所见", firstNonBlank(review.getFindings(), text(draftJson, "findings", null)));
        add(fields, "审核结论", firstNonBlank(review.getConclusion(), text(draftJson, "conclusion", null)));
        add(fields, "处理建议", firstNonBlank(review.getRecommendation(), text(draftJson, "recommendation", null)));
        return fields;
    }

    /**
     * 技术追踪信息区。
     */
    private List<ReportPdfDocument.Field> technicalFields(AnalysisResultEntity result,
                                                          AnalysisReportEntity report,
                                                          AnalysisCorrectionEntity correction) {
        List<ReportPdfDocument.Field> fields = new ArrayList<>();
        add(fields, "结果 ID", String.valueOf(result.getId()));
        add(fields, "任务 ID", result.getTaskId() == null ? null : String.valueOf(result.getTaskId()));
        add(fields, "报告版本", "V" + report.getVersion());
        add(fields, "采用修正", correction == null ? "AI 原始结果" : "修正版本 V" + correction.getVersion());
        return fields;
    }

    /**
     * 根据 result.taskId 反查病例和原始图像，用于报告展示。
     */
    private CaseAndImage loadCaseAndImage(AnalysisResultEntity result) {
        if (tasks == null || cases == null || images == null || result.getTaskId() == null) {
            return new CaseAndImage(null, null, null);
        }

        TaskEntity task = tasks.selectById(result.getTaskId());
        if (task == null) {
            return new CaseAndImage(null, null, null);
        }

        CaseEntity caseEntity = task.getCaseId() == null ? null : cases.selectById(task.getCaseId());
        ImageFileEntity image = task.getImageFileId() == null ? null : images.selectById(task.getImageFileId());
        Path originalPath = image == null ? null : path(imageRoot, image.getStorageObjectKey());
        return new CaseAndImage(caseEntity, image, originalPath);
    }

    /**
     * 查找指定修正版本。
     */
    private AnalysisCorrectionEntity findCorrection(Long resultId, Integer version) {
        if (corrections == null) {
            return null;
        }
        return corrections.selectOne(new LambdaQueryWrapper<AnalysisCorrectionEntity>()
                .eq(AnalysisCorrectionEntity::getResultId, resultId)
                .eq(AnalysisCorrectionEntity::getVersion, version));
    }

    /**
     * 确认分析结果存在。
     */
    private AnalysisResultEntity requireResult(Long resultId) {
        AnalysisResultEntity result = results.selectById(resultId);
        if (result == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析结果不存在");
        }
        return result;
    }

    /**
     * 计算下一个报告版本号。
     */
    private int next(Long resultId) {
        return list(resultId).stream()
                .mapToInt(AnalysisReportEntity::getVersion)
                .max()
                .orElse(0) + 1;
    }

    /**
     * 解析报告文件路径，并防止路径穿越。
     */
    private Path resolve(String objectKey) {
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "报告路径无效");
        }
        return path;
    }

    private BaseException conflict(String message) {
        return new ConflictException(message);
    }

    private void cleanup(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * 生成默认草稿 JSON。
     *
     * <p>医生打开草稿页面时，可以在这个基础上填写所见、结论和建议。</p>
     */
    private String defaultDraft(AnalysisResultEntity result) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("resultId", result.getId());
        draft.put("resultType", result.getResultType());
        draft.put("modelName", result.getModelName());
        draft.put("modelVersion", result.getModelVersion());
        draft.put("processingTimeMs", result.getProcessingTimeMs());
        draft.put("findings", "");
        draft.put("conclusion", "");
        draft.put("recommendation", "");
        draft.put("disclaimer", DISCLAIMER);
        try {
            return json.writeValueAsString(draft);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private JsonNode readTree(String value) {
        if (value == null || value.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(value);
        } catch (Exception exception) {
            return json.createObjectNode();
        }
    }

    private String text(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private void add(List<ReportPdfDocument.Field> fields, String label, String value) {
        fields.add(new ReportPdfDocument.Field(label, value));
    }

    /**
     * 将对象 key 解析成真实文件路径。
     *
     * <p>如果 key 非法或越界，返回 null，让 PDF 渲染器按“无图片”处理。</p>
     */
    private Path path(Path base, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        Path path = base.resolve(objectKey).normalize();
        return path.startsWith(base) ? path : null;
    }

    /**
     * 报告生成过程中临时组合出的病例、图像和原图路径。
     */
    private record CaseAndImage(CaseEntity caseEntity, ImageFileEntity image, Path originalPath) {
    }
}
