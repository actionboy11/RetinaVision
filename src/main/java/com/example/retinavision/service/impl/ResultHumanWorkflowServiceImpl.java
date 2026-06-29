package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CorrectionStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.exception.ConflictException;
import com.example.retinavision.mapper.AnalysisCorrectionMapper;
import com.example.retinavision.mapper.AnalysisFeedbackMapper;
import com.example.retinavision.mapper.AnalysisResultMapper;
import com.example.retinavision.pojo.DTO.SubmitFeedbackDTO;
import com.example.retinavision.pojo.Entity.AnalysisCorrectionEntity;
import com.example.retinavision.pojo.Entity.AnalysisFeedbackEntity;
import com.example.retinavision.pojo.Entity.AnalysisResultEntity;
import com.example.retinavision.service.ResultHumanWorkflowService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 人工反馈与人工修正服务。
 *
 * <p>这个类处理“医生审核之前”的人工参与：
 * 用户可以提交反馈，研究员/医生可以上传修正 mask。
 * 它遵守一个核心原则：AI 原始结果不修改，人工内容全部追加保存。</p>
 */
@Service
public class ResultHumanWorkflowServiceImpl implements ResultHumanWorkflowService {

    /**
     * 当前允许上传的修正 mask 类型。
     *
     * <p>虽然最终会统一保存成 PNG，但入口允许常见医学图像导出格式，避免强迫人工修正工具必须导出 PNG。</p>
     */
    private static final Set<String> ALLOWED_MASK_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/tiff", "image/x-tiff");

    /**
     * 防止超大图片把 JVM 内存打爆。
     */
    private static final long MAX_MASK_PIXELS = 25_000_000L;

    private final AnalysisResultMapper resultMapper;
    private final AnalysisFeedbackMapper feedbackMapper;
    private final AnalysisCorrectionMapper correctionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 结果文件根目录。所有修正 mask 都必须保存在这个目录内部，防止路径穿越。
     */
    private final Path resultRoot;

    public ResultHumanWorkflowServiceImpl(AnalysisResultMapper resultMapper,
                                          AnalysisFeedbackMapper feedbackMapper,
                                          AnalysisCorrectionMapper correctionMapper,
                                          ObjectMapper objectMapper,
                                          @Value("${retina.upload.result-root:uploads/results}") String resultRoot) {
        this.resultMapper = resultMapper;
        this.feedbackMapper = feedbackMapper;
        this.correctionMapper = correctionMapper;
        this.objectMapper = objectMapper;
        this.resultRoot = Paths.get(resultRoot).toAbsolutePath().normalize();
    }

    /**
     * 追加一条人工反馈。
     *
     * <p>反馈只是表达“这个结果怎么样”，不会改变 result、review 或 report 状态。</p>
     */
    @Override
    public AnalysisFeedbackEntity addFeedback(Long resultId, SubmitFeedbackDTO request, Integer userId) {
        // 校验分析结果是否存在
        requireResult(resultId);
        if (request == null || request.getVerdict() == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "反馈结论不能为空");
        }

        AnalysisFeedbackEntity feedback = new AnalysisFeedbackEntity();
        feedback.setResultId(resultId);
        feedback.setVerdict(request.getVerdict());
        try {
            // 将问题代码列表序列化为 JSON 字符串，存储在issueCodes字段中。如果问题代码列表为空，则存储空列表的JSON表示。
            feedback.setIssueCodes(objectMapper.writeValueAsString(
                    request.getIssueCodes() == null ? List.of() : request.getIssueCodes()
            ));
        } catch (JsonProcessingException exception) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "问题代码格式无效");
        }

        feedback.setComment(request.getComment());
        feedback.setSubmittedBy(userId);
        feedback.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        return feedback;
    }

    /**
     * 查询一个分析结果下的全部反馈，按提交时间升序返回。
     */
    @Override
    public List<AnalysisFeedbackEntity> listFeedback(Long resultId) {
        requireResult(resultId);
        return feedbackMapper.selectList(new LambdaQueryWrapper<AnalysisFeedbackEntity>()
                .eq(AnalysisFeedbackEntity::getResultId, resultId)
                .orderByAsc(AnalysisFeedbackEntity::getCreatedAt));
    }

    /**
     * 上传一个新的人工修正版本。
     *
     * <p>关键步骤：
     * 1. 校验 resultId、文件、原因和 expectedVersion；
     * 2. 校验 correctedResultJson 是合法 JSON；
     * 3. 比较 expectedVersion，防止并发上传产生版本错乱；
     * 4. 解码修正 mask，并检查尺寸必须与 AI 原始 mask 一致；
     * 5. 二值化后保存为新的版本文件；
     * 6. 插入 analysis_correction 记录。</p>
     */
    @Override
    @Transactional
    public AnalysisCorrectionEntity addCorrection(Long resultId,
                                                  MultipartFile file,
                                                  String reason,
                                                  String json,
                                                  Integer expectedVersion,
                                                  Integer userId) {
        AnalysisResultEntity result = requireResult(resultId);
        if (file == null || file.isEmpty() || !StringUtils.hasText(reason) || expectedVersion == null) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "修正 mask、原因和 expectedVersion 不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MASK_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "修正 mask 格式不支持");
        }

        if (StringUtils.hasText(json)) {
            try {
                // 校验 correctedResultJson 是合法 JSON
                // 如果不是合法 JSON，会抛出 JsonProcessingException
                objectMapper.readTree(json);
            } catch (JsonProcessingException exception) {
                throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "correctedResultJson 不是合法 JSON");
            }
        }

        int latest = listCorrections(resultId).stream()
                .mapToInt(AnalysisCorrectionEntity::getVersion)
                .max()
                .orElse(0);
        if (latest != expectedVersion) {
            throw new ConflictException("修正版本已变化，请刷新后重试");
        }

        BufferedImage corrected = read(file);
        BufferedImage original = readOriginal(result);
        if (corrected.getWidth() != original.getWidth() || corrected.getHeight() != original.getHeight()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "修正 mask 尺寸必须与 AI 原 mask 一致");
        }

        int version = latest + 1;
        String objectKey = "corrections/" + resultId + "/v" + version + ".png";
        Path target = resolve(objectKey);
        Path temp = resolve(objectKey + ".tmp");

        // 先写临时文件，再移动为正式文件，降低半文件被读取的概率。
        try {
            Files.createDirectories(target.getParent());
            ImageIO.write(binary(corrected), "png", temp.toFile());
            Files.move(temp, target);
        } catch (IOException exception) {
            cleanup(temp);
            throw new BaseException(ErrorMessageSignal.FILE_STORAGE_ERROR, "修正 mask 保存失败");
        }

        AnalysisCorrectionEntity correction = new AnalysisCorrectionEntity();
        LocalDateTime now = LocalDateTime.now();
        correction.setResultId(resultId);
        correction.setVersion(version);
        correction.setCorrectedResultJson(StringUtils.hasText(json) ? json : "{}");
        correction.setCorrectedMaskObjectKey(objectKey);
        correction.setReason(reason.trim());
        correction.setStatus(CorrectionStatus.SUBMITTED);
        correction.setSubmittedBy(userId);
        correction.setCreatedAt(now);
        correction.setUpdatedAt(now);

        try {
            correctionMapper.insert(correction);
        } catch (DuplicateKeyException exception) {
            cleanup(target);
            throw new ConflictException("修正版本已变化，请刷新后重试");
        } catch (RuntimeException exception) {
            cleanup(target);
            throw exception;
        }
        return correction;
    }

    /**
     * 查询全部修正版本。
     */
    @Override
    public List<AnalysisCorrectionEntity> listCorrections(Long resultId) {
        requireResult(resultId);
        return correctionMapper.selectList(new LambdaQueryWrapper<AnalysisCorrectionEntity>()
                .eq(AnalysisCorrectionEntity::getResultId, resultId)
                .orderByAsc(AnalysisCorrectionEntity::getVersion));
    }

    /**
     * 查询指定版本的修正记录。
     */
    @Override
    public AnalysisCorrectionEntity getCorrection(Long resultId, Integer version) {
        AnalysisCorrectionEntity correction = correctionMapper.selectOne(new LambdaQueryWrapper<AnalysisCorrectionEntity>()
                .eq(AnalysisCorrectionEntity::getResultId, resultId)
                .eq(AnalysisCorrectionEntity::getVersion, version));
        if (correction == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "修正版本不存在");
        }
        return correction;
    }

    /**
     * 确认 resultId 存在。后续业务都以真实 AI 结果为基础。
     */
    private AnalysisResultEntity requireResult(Long id) {
        AnalysisResultEntity result = resultMapper.selectById(id);
        if (result == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "分析结果不存在");
        }
        return result;
    }

    /**
     * 安全解码上传图片。
     *
     * <p>使用 ImageReader 先读宽高，可以在真正加载像素前拦截超大图片。</p>
     */
    private BufferedImage read(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new IOException();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IOException();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_MASK_PIXELS) {
                    throw new BaseException(ErrorMessageSignal.PARAM_ERROR,
                            "修正 mask 像素数量不能超过 " + MAX_MASK_PIXELS);
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (BaseException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "修正 mask 无法解码");
        }
    }

    /**
     * 读取 AI 原始 mask，用来校验人工修正 mask 的尺寸。
     */
    private BufferedImage readOriginal(AnalysisResultEntity result) {
        if (!StringUtils.hasText(result.getMaskObjectKey())) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "AI 原始结果没有 mask");
        }
        try {
            BufferedImage image = ImageIO.read(resolve(result.getMaskObjectKey()).toFile());
            if (image == null) {
                throw new IOException();
            }
            return image;
        } catch (IOException exception) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "AI 原始 mask 文件不存在或损坏");
        }
    }

    /**
     * 将对象 key 解析成真实路径，并确保仍在结果根目录下。
     */
    private Path resolve(String key) {
        Path path = resultRoot.resolve(key).normalize();
        if (!path.startsWith(resultRoot)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "结果文件路径无效");
        }
        return path;
    }

    /**
     * 将人工上传的 mask 二值化，统一保存成黑白 PNG。
     */
    private BufferedImage binary(BufferedImage source) {
        BufferedImage output = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_BYTE_BINARY
        );
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int gray = (((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3;
                output.setRGB(x, y, gray >= 128 ? 0xffffffff : 0xff000000);
            }
        }
        return output;
    }

    private void cleanup(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
