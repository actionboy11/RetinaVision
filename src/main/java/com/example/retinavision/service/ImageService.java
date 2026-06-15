package com.example.retinavision.service;

import com.example.retinavision.pojo.VO.ImageFileItemVO;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

public interface ImageService {
    List<ImageFileItemVO> getCaseImagesList(Integer caseId);

    ImageFileItemVO uploadImage(Integer caseId, MultipartFile file, Integer userid);

    Path getImagePreviewPath(Long imageId);

    ImageFileItemVO getImageById(Long imageId);

    boolean deleteImage(Long imageId);
}
