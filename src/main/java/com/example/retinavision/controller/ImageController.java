package com.example.retinavision.controller;


import com.example.retinavision.pojo.VO.CurrentUserVO;
import com.example.retinavision.pojo.VO.ImageFileItemVO;
import com.example.retinavision.result.Result;
import com.example.retinavision.service.ImageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping
public class ImageController {
    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/cases/{caseId}/images")
    public Result<List<ImageFileItemVO>> getCaseImagesList(@PathVariable Integer caseId) {
        List<ImageFileItemVO> imageFileItemVOList =imageService.getCaseImagesList(caseId);
        return Result.success(imageFileItemVOList);
    }

    @PostMapping("/cases/{caseId}/images")
    public Result<ImageFileItemVO> uploadImage
            (@PathVariable Integer caseId,
             @RequestParam("file") MultipartFile file,
             Authentication authentication)
    {
        // 上传文件由 MultipartFile 承接，字段名必须和前端 FormData.append("file", file) 保持一致。
        CurrentUserVO tokenUser = (CurrentUserVO)authentication.getPrincipal();
        Integer userid = tokenUser.getId();
        ImageFileItemVO imageFileItemVO = imageService.uploadImage(caseId, file, userid);
        return Result.success(imageFileItemVO);
    }

    @GetMapping("/images/{imageId}/preview")
    public ResponseEntity<InputStreamResource> previewImage(@PathVariable Long imageId) throws IOException {
        ImageFileItemVO imageFileItemVO = imageService.getImageById(imageId);
        Path imagePath = imageService.getImagePreviewPath(imageId);
        String contentType = imageFileItemVO.getFileType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : imageFileItemVO.getFileType();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(Files.newInputStream(imagePath)));
    }

    @DeleteMapping("/images/{imageId}")
    public Result<Boolean> deleteImage(@PathVariable Long imageId) {
        return Result.success(imageService.deleteImage(imageId));
    }

}
