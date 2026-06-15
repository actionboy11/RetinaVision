package com.example.retinavision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retinavision.constant.ErrorMessageContant;
import com.example.retinavision.constant.ErrorMessageSignal;
import com.example.retinavision.enumeration.CaseStatus;
import com.example.retinavision.enumeration.ImageStatus;
import com.example.retinavision.exception.BaseException;
import com.example.retinavision.mapper.CaseMapper;
import com.example.retinavision.mapper.ImageMapper;
import com.example.retinavision.mapper.UserRegisterMapper;
import com.example.retinavision.pojo.Entity.CaseEntity;
import com.example.retinavision.pojo.Entity.ImageFileEntity;
import com.example.retinavision.pojo.Entity.UserEntity;
import com.example.retinavision.pojo.VO.ImageFileItemVO;
import com.example.retinavision.service.ImageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ImageServiceImpl implements ImageService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final String LOCAL_STORAGE_BUCKET = "local";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "tif", "tiff");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/tiff",
            "image/x-tiff"
    );

    private final ImageMapper imageMapper;
    private final UserRegisterMapper userMapper;
    private final CaseMapper caseMapper;
    private final Path imageRootPath;

    public ImageServiceImpl(
            ImageMapper imageMapper,
            CaseMapper caseMapper,
            UserRegisterMapper userMapper,
            @Value("${retina.upload.image-root:uploads/images}") String imageRoot) {  // @Value 注解 获取配置文件中的属性值
        this.imageMapper = imageMapper;
        this.caseMapper = caseMapper;
        this.userMapper = userMapper;
        this.imageRootPath = Paths.get(imageRoot).toAbsolutePath().normalize();  //toAbsolutePath 获取绝对路径 normalize 获取规范路径
    }

    @Override
    public List<ImageFileItemVO> getCaseImagesList(Integer caseId) {
        validateCaseCanUse(caseId);

        LambdaQueryWrapper<ImageFileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImageFileEntity::getCaseId, caseId.longValue())
                .ne(ImageFileEntity::getStatus, ImageStatus.DELETED)
                .orderByDesc(ImageFileEntity::getUploadedAt);

        return imageMapper.selectList(wrapper)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public ImageFileItemVO uploadImage(Integer caseId, MultipartFile file, Integer userid) {
        validateCaseCanUse(caseId);
        validateUploadFile(file);

        String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        String objectKey = buildObjectKey(caseId, extension);
        Path targetPath = resolveStoragePath(objectKey);

        Integer imageWidth = null;
        Integer imageHeight = null;

        // 补充：宽高来自图片内容本身；ImageIO 对部分 tiff 环境可能返回 null，所以宽高字段允许为空。
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image != null) {
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
            }
        } catch (IOException exception) {
            throw new BaseException(ErrorMessageSignal.FILE_STORAGE_ERROR, ErrorMessageContant.FILE_STORAGE_ERROR_MSG);
        }

        try {
            Files.createDirectories(targetPath.getParent());  //创建目录
            // 补充：真实文件保存到本地磁盘，数据库只保存 storageObjectKey，不把二进制塞进数据库。
            file.transferTo(targetPath.toFile()); // 真正把上传的文件保存到磁盘
        } catch (IOException exception) {
            throw new BaseException(ErrorMessageSignal.FILE_STORAGE_ERROR, ErrorMessageContant.FILE_STORAGE_ERROR_MSG);
        }

        LocalDateTime now = LocalDateTime.now();
        ImageFileEntity imageEntity = ImageFileEntity.builder()
                .caseId(caseId.longValue())  //longValue() 转换为 long
                .originalFilename(originalFilename)
                .fileType(normalizeContentType(file.getContentType(), extension))  //getContentType() 获取文件类型
                .fileSize(file.getSize())
                .storageBucket(LOCAL_STORAGE_BUCKET)
                .storageObjectKey(objectKey)
                // 补充：previewUrl 先不落库，插入拿到 id 后再设置，避免自己猜数据库自增 ID。
                .imageWidth(imageWidth)
                .imageHeight(imageHeight)
                .status(ImageStatus.UPLOADED)
                .uploadedBy(userid.longValue())
                .uploadedAt(now)
                .updatedAt(now)
                .build();
        // MyBatis-Plus 会把生成的 id 回填到 imageEntity.id
        imageMapper.insert(imageEntity);
        imageEntity.setPreviewUrl(buildPreviewUrl(imageEntity.getId()));
        imageMapper.updateById(imageEntity);

        return toVO(imageEntity);
    }

    @Override
    public Path getImagePreviewPath(Long imageId) {
        ImageFileEntity imageEntity = getAvailableImageEntity(imageId);
        Path imagePath = resolveStoragePath(imageEntity.getStorageObjectKey());

        if (!Files.exists(imagePath) || !Files.isRegularFile(imagePath)) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.IMAGE_STORAGE_PATH_ERROR);
        }

        return imagePath;
    }

    @Override
    public ImageFileItemVO getImageById(Long imageId) {
        return toVO(getAvailableImageEntity(imageId));
    }

    @Override
    public boolean deleteImage(Long imageId) {
        ImageFileEntity imageEntity = getAvailableImageEntity(imageId);

        // TODO 任务表完成后：删除前查询该图像是否绑定 RUNNING/SUCCESS 等任务；存在业务冲突时返回 40900。
        imageEntity.setStatus(ImageStatus.DELETED);
        imageEntity.setDeletedAt(LocalDateTime.now());
        imageEntity.setUpdatedAt(LocalDateTime.now());
        imageMapper.updateById(imageEntity);
        return true;
    }

    private void validateCaseCanUse(Integer caseId) {
        CaseEntity caseEntity = caseMapper.selectById(caseId);
        if (caseEntity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.CASE_NOT_EXISTS);
        }
        if (caseEntity.getStatus() == CaseStatus.DELETED) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.CASE_DELETED_MSG);
        }
        if (caseEntity.getStatus() == CaseStatus.ARCHIVED) {
            throw new BaseException(ErrorMessageSignal.CONFLICT, "已归档病例不允许上传或管理图像");
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.IMAGE_EMPTY_FILE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.IMAGE_SIZE_ERROR);
        }

        String originalFilename = cleanOriginalFilename(file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        String contentType = file.getContentType();
        boolean extensionAllowed = ALLOWED_EXTENSIONS.contains(extension);
        boolean contentTypeAllowed = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));

        if (!extensionAllowed || !contentTypeAllowed) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.IMAGE_TYPE_ERROR);
        }
    }

    private ImageFileEntity getAvailableImageEntity(Long imageId) {
        ImageFileEntity imageEntity = imageMapper.selectById(imageId);
        if (imageEntity == null) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.IMAGE_NOT_EXISTS);
        }
        if (imageEntity.getStatus() == ImageStatus.DELETED) {
            throw new BaseException(ErrorMessageSignal.NOT_FOUND, ErrorMessageContant.IMAGE_DELETED_MSG);
        }

        return imageEntity;
    }

    private ImageFileItemVO toVO(ImageFileEntity entity) {
        UserEntity userEntity = userMapper.selectById(entity.getUploadedBy());
        String uploadedByName = userEntity == null ? null : userEntity.getRealName();

        return ImageFileItemVO.builder()
                .id(entity.getId())
                .caseId(entity.getCaseId())
                .originalFilename(entity.getOriginalFilename())
                .fileType(entity.getFileType())
                .fileSize(entity.getFileSize())
                .storageBucket(entity.getStorageBucket())
                .storageObjectKey(entity.getStorageObjectKey())
                .previewUrl(StringUtils.hasText(entity.getPreviewUrl()) ? entity.getPreviewUrl() : buildPreviewUrl(entity.getId()))
                .imageWidth(entity.getImageWidth())
                .imageHeight(entity.getImageHeight())
                .status(entity.getStatus())
                .uploadedBy(entity.getUploadedBy())
                .uploadedByName(uploadedByName)
                .uploadedAt(entity.getUploadedAt())
                .build();
    }

    private String cleanOriginalFilename(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        if (!StringUtils.hasText(filename) || filename.contains("..")) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.IMAGE_TYPE_ERROR);
        }
        return filename;
    }

    // 获取文件扩展名
    private String getExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.'); //lastIndexOf()方法返回指定字符串中最后一次出现的索引位置，如果没有找到匹配的字符串，则返回-1。
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {  //-1 表示字符串末尾，即最后一个字符的索引位置。
            // 说明没有后缀，或者后缀为空，属于非法格式
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.IMAGE_TYPE_ERROR);
        }
        return filename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT); // substring()方法返回指定范围的子字符串。
    }

    private String buildObjectKey(Integer caseId, String extension) {
        return "cases/" + caseId + "/" + UUID.randomUUID() + "." + extension;
    }

    private Path resolveStoragePath(String objectKey) {
        Path targetPath = imageRootPath.resolve(objectKey).normalize();   //resolve()方法用于将给定的路径字符串拼接到当前路径对象中，并返回一个新的路径对象。
        if (!targetPath.startsWith(imageRootPath)) {
            throw new BaseException(ErrorMessageSignal.PARAM_ERROR, ErrorMessageContant.IMAGE_STORAGE_PATH_ERROR);
        }
        return targetPath;
    }

    private String buildPreviewUrl(Long imageId) {
        return "/api/images/" + imageId + "/preview";
    }

    private String normalizeContentType(String contentType, String extension) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        if ("png".equals(extension)) {
            return "image/png";
        }
        if ("tif".equals(extension) || "tiff".equals(extension)) {
            return "image/tiff";
        }
        return "image/jpeg";
    }
}
