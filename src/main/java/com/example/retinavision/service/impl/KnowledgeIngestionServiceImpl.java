package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.KnowledgeChunkMapper;
import com.example.retinavision.mapper.KnowledgeDocumentMapper;
import com.example.retinavision.pojo.DTO.KnowledgeDocumentCreateDTO;
import com.example.retinavision.pojo.Entity.KnowledgeChunkEntity;
import com.example.retinavision.pojo.Entity.KnowledgeDocumentEntity;
import com.example.retinavision.rag.EmbeddingClient;
import com.example.retinavision.rag.EmbeddingProperties;
import com.example.retinavision.rag.QdrantClient;
import com.example.retinavision.rag.QdrantPoint;
import com.example.retinavision.service.KnowledgeIngestionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {
    private static final int CHUNK_SIZE = 700;
    private static final int CHUNK_OVERLAP = 80;
    private static final int FAILURE_REASON_LIMIT = 500;
    private static final String DEFAULT_CATEGORY = "MEDICAL_BASE";
    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "MEDICAL_BASE", "AI_RESULT_EXPLANATION", "WORKFLOW", "FAQ", "SAFETY"
    );
    private static final List<String> ENABLED_STATUSES = List.of("ACTIVE", "DISABLED");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(sk-[a-z0-9._-]{12,}|api[_-]?key\\s*[:=]|bearer\\s+[a-z0-9._-]{16,})");
    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile("(?i)([a-z]:\\\\|/home/|/root/|/var/|/tmp/)");
    private static final Pattern PATIENT_ID_PATTERN = Pattern.compile("(身份证|住院号|门诊号|患者姓名|联系电话)\\s*[:：]");

    private final KnowledgeDocumentMapper documents;
    private final KnowledgeChunkMapper chunks;
    private final EmbeddingClient embeddings;
    private final EmbeddingProperties embeddingProperties;
    private final QdrantClient qdrant;

    public KnowledgeIngestionServiceImpl(KnowledgeDocumentMapper documents,
                                         KnowledgeChunkMapper chunks,
                                         EmbeddingClient embeddings,
                                         EmbeddingProperties embeddingProperties,
                                         QdrantClient qdrant) {
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.embeddingProperties = embeddingProperties;
        this.qdrant = qdrant;
    }

    @Override
    @Transactional
    public KnowledgeDocumentEntity create(KnowledgeDocumentCreateDTO request, Integer userId) {
        validate(request);
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        LocalDateTime now = LocalDateTime.now();
        document.setTitle(request.title().trim());
        document.setSource(blankToDefault(request.source(), "未注明来源"));
        document.setContentType("markdown");
        document.setOriginalContent(request.content().trim());
        document.setCategory(normalizeCategory(request.category()));
        document.setStatus("INDEXING");
        document.setVersion(1);
        document.setUploadedBy(userId);
        document.setChunkCount(0);
        document.setFailureReason(null);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documents.insert(document);
        return rebuildIndex(document, document.getOriginalContent());
    }

    @Override
    public List<KnowledgeDocumentEntity> list() {
        return documents.selectList(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .orderByDesc(KnowledgeDocumentEntity::getCreatedAt));
    }

    @Override
    @Transactional
    public KnowledgeDocumentEntity reindex(Long documentId) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        String content = StringUtils.hasText(document.getOriginalContent())
                ? document.getOriginalContent()
                : reconstructFromChunks(documentId);
        if (!StringUtils.hasText(content)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "知识文档缺少可重建的原始内容");
        }
        return rebuildIndex(document, content);
    }

    @Override
    @Transactional
    public KnowledgeDocumentEntity updateStatus(Long documentId, String status) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        String normalized = normalizeStatus(status);
        document.setStatus(normalized);
        document.setFailureReason(null);
        document.setUpdatedAt(LocalDateTime.now());
        documents.updateById(document);
        List<QdrantPoint> points = chunksFor(documentId).stream()
                .map(chunk -> new QdrantPoint(
                        chunk.getQdrantPointId(),
                        embeddings.embed(chunk.getChunkText()),
                        document.getId(),
                        chunk.getId(),
                        document.getTitle(),
                        document.getSource(),
                        document.getCategory(),
                        normalized,
                        chunk.getChunkText()
                ))
                .toList();
        if (!points.isEmpty()) {
            qdrant.upsert(points);
        }
        return document;
    }

    @Override
    @Transactional
    public void delete(Long documentId) {
        KnowledgeDocumentEntity document = requireDocument(documentId);
        List<KnowledgeChunkEntity> existingChunks = chunksFor(document.getId());
        qdrant.deletePoints(existingChunks.stream().map(KnowledgeChunkEntity::getQdrantPointId).toList());
        chunks.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>().eq(KnowledgeChunkEntity::getDocumentId, document.getId()));
        documents.deleteById(document.getId());
    }

    private KnowledgeDocumentEntity rebuildIndex(KnowledgeDocumentEntity document, String content) {
        List<KnowledgeChunkEntity> existingChunks = chunksFor(document.getId());
        qdrant.deletePoints(existingChunks.stream().map(KnowledgeChunkEntity::getQdrantPointId).toList());
        if (!existingChunks.isEmpty()) {
            chunks.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>().eq(KnowledgeChunkEntity::getDocumentId, document.getId()));
        }

        document.setStatus("INDEXING");
        document.setFailureReason(null);
        document.setUpdatedAt(LocalDateTime.now());
        documents.updateById(document);

        try {
            int chunkCount = indexContent(document, content);
            LocalDateTime now = LocalDateTime.now();
            document.setStatus("ACTIVE");
            document.setOriginalContent(content.trim());
            document.setChunkCount(chunkCount);
            document.setLastIndexedAt(now);
            document.setFailureReason(null);
            document.setUpdatedAt(now);
            documents.updateById(document);
            return document;
        } catch (BaseException exception) {
            markFailed(document, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            markFailed(document, exception.getMessage());
            throw new BaseException(ErrorMessageSignal.SERVICE_UNAVAILABLE, "知识文档索引失败");
        }
    }

    private int indexContent(KnowledgeDocumentEntity document, String content) {
        qdrant.ensureCollection(embeddingProperties.getDimension());
        List<QdrantPoint> points = new ArrayList<>();
        List<String> texts = split(content);
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(i);
            chunk.setChunkText(text);
            chunk.setCharLength(text.length());
            chunk.setQdrantPointId(UUID.randomUUID().toString());
            chunk.setCreatedAt(LocalDateTime.now());
            chunks.insert(chunk);
            points.add(new QdrantPoint(
                    chunk.getQdrantPointId(),
                    embeddings.embed(text),
                    document.getId(),
                    chunk.getId(),
                    document.getTitle(),
                    document.getSource(),
                    document.getCategory(),
                    "ACTIVE",
                    text
            ));
        }
        if (!points.isEmpty()) {
            qdrant.upsert(points);
        }
        return points.size();
    }

    private List<String> split(String content) {
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> blocks = paragraphBlocks(normalized);
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (block.length() > CHUNK_SIZE) {
                flush(current, result);
                splitLongBlock(block, result);
                continue;
            }
            if (!current.isEmpty() && current.length() + block.length() + 2 > CHUNK_SIZE) {
                flush(current, result);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(block);
        }
        flush(current, result);
        return result;
    }

    private List<String> paragraphBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        String[] rawBlocks = content.split("\\n\\s*\\n");
        for (String raw : rawBlocks) {
            String block = raw.trim();
            if (StringUtils.hasText(block)) {
                blocks.add(block);
            }
        }
        return blocks.isEmpty() ? List.of(content) : blocks;
    }

    private void splitLongBlock(String block, List<String> result) {
        int start = 0;
        while (start < block.length()) {
            int end = Math.min(block.length(), start + CHUNK_SIZE);
            result.add(block.substring(start, end).trim());
            if (end == block.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
    }

    private void flush(StringBuilder current, List<String> result) {
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
            current.setLength(0);
        }
    }

    private void validate(KnowledgeDocumentCreateDTO request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "知识文档标题不能为空");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "知识文档内容不能为空");
        }
        rejectSensitiveContent(request.content());
        normalizeCategory(request.category());
    }

    private void rejectSensitiveContent(String content) {
        if (SECRET_PATTERN.matcher(content).find()
                || ABSOLUTE_PATH_PATTERN.matcher(content).find()
                || PATIENT_ID_PATTERN.matcher(content).find()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "知识文档包含疑似敏感信息，请脱敏后再上传");
        }
    }

    private String normalizeCategory(String category) {
        String normalized = StringUtils.hasText(category) ? category.trim().toUpperCase(Locale.ROOT) : DEFAULT_CATEGORY;
        if (!ALLOWED_CATEGORIES.contains(normalized)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "知识文档分类不合法");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "";
        if (!ENABLED_STATUSES.contains(normalized)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, "知识文档状态不合法");
        }
        return normalized;
    }

    private KnowledgeDocumentEntity requireDocument(Long documentId) {
        KnowledgeDocumentEntity document = documents.selectById(documentId);
        if (document == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, "知识文档不存在");
        }
        return document;
    }

    private List<KnowledgeChunkEntity> chunksFor(Long documentId) {
        return chunks.selectList(new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId)
                .orderByAsc(KnowledgeChunkEntity::getChunkIndex));
    }

    private String reconstructFromChunks(Long documentId) {
        return String.join("\n\n", chunksFor(documentId).stream()
                .map(KnowledgeChunkEntity::getChunkText)
                .toList());
    }

    private void markFailed(KnowledgeDocumentEntity document, String reason) {
        document.setStatus("FAILED");
        document.setFailureReason(limit(StringUtils.hasText(reason) ? reason : "知识文档索引失败"));
        document.setUpdatedAt(LocalDateTime.now());
        documents.updateById(document);
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String limit(String value) {
        return value.length() > FAILURE_REASON_LIMIT ? value.substring(0, FAILURE_REASON_LIMIT) : value;
    }
}
