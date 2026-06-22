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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;
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

@Service
public class ResultHumanWorkflowServiceImpl implements ResultHumanWorkflowService {
    private static final Set<String> ALLOWED_MASK_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/tiff", "image/x-tiff");
    private static final long MAX_MASK_PIXELS = 25_000_000L;

    private final AnalysisResultMapper resultMapper;
    private final AnalysisFeedbackMapper feedbackMapper;
    private final AnalysisCorrectionMapper correctionMapper;
    private final ObjectMapper objectMapper;
    private final Path resultRoot;
    public ResultHumanWorkflowServiceImpl(AnalysisResultMapper resultMapper, AnalysisFeedbackMapper feedbackMapper,
            AnalysisCorrectionMapper correctionMapper, ObjectMapper objectMapper,
            @Value("${retina.upload.result-root:uploads/results}") String resultRoot) {
        this.resultMapper=resultMapper; this.feedbackMapper=feedbackMapper; this.correctionMapper=correctionMapper;
        this.objectMapper=objectMapper; this.resultRoot=Paths.get(resultRoot).toAbsolutePath().normalize();
    }
    @Override public AnalysisFeedbackEntity addFeedback(Long resultId, SubmitFeedbackDTO request, Integer userId) {
        requireResult(resultId);
        if(request==null || request.getVerdict()==null) throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"反馈结论不能为空");
        AnalysisFeedbackEntity x=new AnalysisFeedbackEntity(); x.setResultId(resultId); x.setVerdict(request.getVerdict());
        try { x.setIssueCodes(objectMapper.writeValueAsString(request.getIssueCodes()==null?List.of():request.getIssueCodes())); }
        catch(JsonProcessingException e){throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"问题代码格式无效");}
        x.setComment(request.getComment()); x.setSubmittedBy(userId); x.setCreatedAt(LocalDateTime.now()); feedbackMapper.insert(x); return x;
    }
    @Override public List<AnalysisFeedbackEntity> listFeedback(Long resultId){ requireResult(resultId); return feedbackMapper.selectList(new LambdaQueryWrapper<AnalysisFeedbackEntity>().eq(AnalysisFeedbackEntity::getResultId,resultId).orderByAsc(AnalysisFeedbackEntity::getCreatedAt)); }
    @Override @Transactional
    public AnalysisCorrectionEntity addCorrection(Long resultId, MultipartFile file, String reason, String json, Integer expectedVersion, Integer userId){
        AnalysisResultEntity result=requireResult(resultId);
        if(file==null || file.isEmpty() || !StringUtils.hasText(reason) || expectedVersion==null) throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"修正 mask、原因和 expectedVersion 不能为空");
        String contentType=file.getContentType();
        if(contentType==null || !ALLOWED_MASK_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"修正 mask 格式不支持");
        if(StringUtils.hasText(json)) try{objectMapper.readTree(json);}catch(JsonProcessingException e){throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"correctedResultJson 不是合法 JSON");}
        int latest=listCorrections(resultId).stream().mapToInt(AnalysisCorrectionEntity::getVersion).max().orElse(0);
        if(latest!=expectedVersion) throw new ConflictException("修正版本已变化，请刷新后重试");
        BufferedImage corrected=read(file); BufferedImage original=readOriginal(result);
        if(corrected.getWidth()!=original.getWidth() || corrected.getHeight()!=original.getHeight()) throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"修正 mask 尺寸必须与 AI 原 mask 一致");
        int version=latest+1; String objectKey="corrections/"+resultId+"/v"+version+".png";
        Path target=resolve(objectKey); Path temp=resolve(objectKey+".tmp");
        try { Files.createDirectories(target.getParent()); ImageIO.write(binary(corrected),"png",temp.toFile()); Files.move(temp,target); }
        catch(IOException e){ try{Files.deleteIfExists(temp);}catch(IOException ignored){} throw new BaseException(ErrorMessageSignal.FILE_STORAGE_ERROR,"修正 mask 保存失败"); }
        AnalysisCorrectionEntity x=new AnalysisCorrectionEntity(); LocalDateTime now=LocalDateTime.now();
        x.setResultId(resultId); x.setVersion(version); x.setCorrectedResultJson(StringUtils.hasText(json)?json:"{}"); x.setCorrectedMaskObjectKey(objectKey);
        x.setReason(reason.trim()); x.setStatus(CorrectionStatus.SUBMITTED); x.setSubmittedBy(userId); x.setCreatedAt(now); x.setUpdatedAt(now);
        try{correctionMapper.insert(x);}catch(DuplicateKeyException e){try{Files.deleteIfExists(target);}catch(IOException ignored){} throw new ConflictException("修正版本已变化，请刷新后重试");}catch(RuntimeException e){try{Files.deleteIfExists(target);}catch(IOException ignored){} throw e;} return x;
    }
    @Override public List<AnalysisCorrectionEntity> listCorrections(Long resultId){requireResult(resultId); return correctionMapper.selectList(new LambdaQueryWrapper<AnalysisCorrectionEntity>().eq(AnalysisCorrectionEntity::getResultId,resultId).orderByAsc(AnalysisCorrectionEntity::getVersion));}
    @Override public AnalysisCorrectionEntity getCorrection(Long resultId,Integer version){AnalysisCorrectionEntity x=correctionMapper.selectOne(new LambdaQueryWrapper<AnalysisCorrectionEntity>().eq(AnalysisCorrectionEntity::getResultId,resultId).eq(AnalysisCorrectionEntity::getVersion,version));if(x==null)throw new BaseException(ErrorMessageSignal.NOT_FOUND,"修正版本不存在");return x;}
    private AnalysisResultEntity requireResult(Long id){AnalysisResultEntity x=resultMapper.selectById(id);if(x==null)throw new BaseException(ErrorMessageSignal.NOT_FOUND,"分析结果不存在");return x;}
    private BufferedImage read(MultipartFile f){
        try(InputStream input=f.getInputStream(); ImageInputStream imageInput=ImageIO.createImageInputStream(input)){
            if(imageInput==null)throw new IOException();
            Iterator<ImageReader> readers=ImageIO.getImageReaders(imageInput);
            if(!readers.hasNext())throw new IOException();
            ImageReader reader=readers.next();
            try{
                reader.setInput(imageInput,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0 || height<=0 || (long)width*height>MAX_MASK_PIXELS)throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"修正 mask 像素数量不能超过 "+MAX_MASK_PIXELS);
                BufferedImage x=reader.read(0);if(x==null)throw new IOException();return x;
            }finally{reader.dispose();}
        }catch(BaseException e){throw e;}catch(IOException|RuntimeException e){throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"修正 mask 无法解码");}
    }
    private BufferedImage readOriginal(AnalysisResultEntity r){if(!StringUtils.hasText(r.getMaskObjectKey()))throw new BaseException(ErrorMessageSignal.CONFLICT,"AI 原始结果没有 mask");try{BufferedImage x=ImageIO.read(resolve(r.getMaskObjectKey()).toFile());if(x==null)throw new IOException();return x;}catch(IOException e){throw new BaseException(ErrorMessageSignal.NOT_FOUND,"AI 原始 mask 文件不存在或损坏");}}
    private Path resolve(String key){Path x=resultRoot.resolve(key).normalize();if(!x.startsWith(resultRoot))throw new BaseException(ErrorMessageSignal.PARAM_ERROR,"结果文件路径无效");return x;}
    private BufferedImage binary(BufferedImage src){BufferedImage out=new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_BYTE_BINARY);for(int y=0;y<src.getHeight();y++)for(int x=0;x<src.getWidth();x++){int rgb=src.getRGB(x,y);int gray=(((rgb>>16)&255)+((rgb>>8)&255)+(rgb&255))/3;out.setRGB(x,y,gray>=128?0xffffffff:0xff000000);}return out;}
}
