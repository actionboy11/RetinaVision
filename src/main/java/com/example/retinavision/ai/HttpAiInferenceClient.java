package com.example.retinavision.ai;

import com.example.retinavision.ai.dto.AiInferenceResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.file.Path;

@Component
public class HttpAiInferenceClient implements AiInferenceClient {
    private static final String SEGMENTATION_PATH = "/v1/inference/vessel-segmentation";
    private static final String QUALITY_PATH = "/v1/inference/image-quality-check";

    // 客户端配置
    private final RestClient restClient;
    // AI 服务基础 URI，用于验证下载的分割结果图地址
    private final URI baseUri;

    // 构造函数，初始化 RestClient 和 baseUri
    public HttpAiInferenceClient(AiServiceProperties properties) {
        //requireBaseUrl 方法确保 baseUrl 不为空，并去掉末尾的斜杠
        this.baseUri = URI.create(requireBaseUrl(properties.getBaseUrl()));
        //SimpleClientHttpRequestFactory 用于设置连接和读取超时时间
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(baseUri.toString())
                .requestFactory(requestFactory)
                .build();
    }

    // 实现 AiInferenceClient 接口，调用 AI 血管分割服务
    @Override
    public AiInferenceResponse segment(Path imagePath, String originalFilename, String contentType, String requestId) {
        try {
            //MultipartBodyBuilder 用于构建 multipart/form-data 请求体，传图片二进制
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            //part 方法添加文件部分，filename 和 contentType 用于告诉 AI 服务文件的原始名称和类型
            //FileSystemResource 将 Java 的本地文件路径包装成 Spring 能上传的资源对象，Spring 之后会读取该文件并放入 HTTP 请求体
            //parseContentType 方法将 contentType 字符串解析为 MediaType 对象，如果解析失败则使用默认的 application/octet-stream
            body.part("file", new FileSystemResource(imagePath))
                    .filename(originalFilename)
                    .contentType(parseContentType(contentType));
            //发送 HTTP 请求 ，利用 RestClient.post 方法发送 POST 请求到 AI 血管分割服务，
            // retrieve 方法发送请求并获取响应
            //body(AiInferenceResponse.class) 表示将响应体转换为 AiInferenceResponse 类型的对象
            AiInferenceResponse response = restClient.post()
                    .uri(SEGMENTATION_PATH)
                    .header("X-Request-ID", requestId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(AiInferenceResponse.class);
            if (response == null || response.getMaskUrl() == null || response.getResultJson() == null) {
                throw new AiInferenceException(
                        AiFailureCategory.INCOMPLETE_RESPONSE,
                        "AI 服务返回了不完整的推理结果"
                );
            }
            return response;
        } catch (AiInferenceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw classify(exception, AiFailureCategory.UNKNOWN, "调用 AI 血管分割服务失败");
        }
    }

    @Override
    public byte[] downloadMask(String maskUrl, String requestId) {
        URI maskUri = validateArtifactUri(maskUrl);
        try {
            //get 方法发送 GET 请求到分割结果图地址，retrieve 方法获取响应体并转换为 byte[] 数组
            byte[] mask = restClient.get()
                    .uri(maskUri)
                    .header("X-Request-ID", requestId)
                    .retrieve()
                    .body(byte[].class);
            if (mask == null || mask.length == 0) {
                throw new AiInferenceException(
                        AiFailureCategory.ARTIFACT_DOWNLOAD_FAILED,
                        "AI 服务返回了空的分割结果图"
                );
            }
            return mask;
        } catch (AiInferenceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw classify(exception, AiFailureCategory.ARTIFACT_DOWNLOAD_FAILED, "下载 AI 分割结果图失败");
        }
    }

    @Override
    public AiInferenceResponse checkQuality(Path imagePath, String originalFilename, String contentType, String requestId) {
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", new FileSystemResource(imagePath))
                    .filename(originalFilename)
                    .contentType(parseContentType(contentType));
            AiInferenceResponse response = restClient.post()
                    .uri(QUALITY_PATH)
                    .header("X-Request-ID", requestId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build()).retrieve().body(AiInferenceResponse.class);
            if (response == null || response.getResultJson() == null
                    || !"IMAGE_QUALITY_CHECK".equals(response.getResultType())) {
                throw new AiInferenceException(AiFailureCategory.INCOMPLETE_RESPONSE,
                        "AI 服务返回了不完整的图像质量结果");
            }
            return response;
        } catch (AiInferenceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw classify(exception, AiFailureCategory.UNKNOWN, "调用 AI 图像质量检测服务失败");
        }
    }

    private URI validateArtifactUri(String maskUrl) {
        if (maskUrl == null || maskUrl.isBlank()) {
            throw new AiInferenceException(
                    AiFailureCategory.UNTRUSTED_ARTIFACT_URL,
                    "AI 分割结果图地址为空"
            );
        }
        //resolve 方法将 maskUrl 解析为相对于 baseUri 的绝对 URI，如果 maskUrl 是绝对 URI，则直接返回该 URI
        URI resolved = baseUri.resolve(maskUrl);
        if (!baseUri.getScheme().equalsIgnoreCase(resolved.getScheme())
                || !baseUri.getAuthority().equalsIgnoreCase(resolved.getAuthority())) {
            throw new AiInferenceException(
                    AiFailureCategory.UNTRUSTED_ARTIFACT_URL,
                    "AI 分割结果图地址不受信任"
            );
        }
        return resolved;
    }

    private static String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("retina.ai.base-url 不能为空");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private MediaType parseContentType(String contentType) {
        try {
            return contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private AiInferenceException classify(
            Exception exception,
            AiFailureCategory fallback,
            String message) {
        if (exception instanceof HttpClientErrorException) {
            return new AiInferenceException(AiFailureCategory.AI_4XX, message, exception);
        }
        if (exception instanceof HttpServerErrorException) {
            return new AiInferenceException(AiFailureCategory.AI_5XX, message, exception);
        }
        if (exception instanceof ResourceAccessException && hasCause(exception, SocketTimeoutException.class)) {
            return new AiInferenceException(AiFailureCategory.READ_TIMEOUT, message, exception);
        }
        if (exception instanceof ResourceAccessException) {
            return new AiInferenceException(AiFailureCategory.CONNECTION_FAILED, message, exception);
        }
        return new AiInferenceException(fallback, message, exception);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

