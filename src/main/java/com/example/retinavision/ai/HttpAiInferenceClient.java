package com.example.retinavision.ai;

import com.example.retinavision.ai.dto.AiInferenceResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.file.Path;

@Component
public class HttpAiInferenceClient implements AiInferenceClient {
    private static final String SEGMENTATION_PATH = "/v1/inference/vessel-segmentation";

    private final RestClient restClient;
    private final URI baseUri;

    public HttpAiInferenceClient(AiServiceProperties properties) {
        this.baseUri = URI.create(requireBaseUrl(properties.getBaseUrl()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(baseUri.toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public AiInferenceResponse segment(Path imagePath, String originalFilename, String contentType) {
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", new FileSystemResource(imagePath))
                    .filename(originalFilename)
                    .contentType(parseContentType(contentType));
            AiInferenceResponse response = restClient.post()
                    .uri(SEGMENTATION_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(AiInferenceResponse.class);
            if (response == null || response.getMaskUrl() == null || response.getResultJson() == null) {
                throw new AiInferenceException("AI 服务返回了不完整的推理结果");
            }
            return response;
        } catch (AiInferenceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiInferenceException("调用 AI 血管分割服务失败", exception);
        }
    }

    @Override
    public byte[] downloadMask(String maskUrl) {
        URI maskUri = validateArtifactUri(maskUrl);
        try {
            byte[] mask = restClient.get().uri(maskUri).retrieve().body(byte[].class);
            if (mask == null || mask.length == 0) {
                throw new AiInferenceException("AI 服务返回了空的分割结果图");
            }
            return mask;
        } catch (AiInferenceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiInferenceException("下载 AI 分割结果图失败", exception);
        }
    }

    private URI validateArtifactUri(String maskUrl) {
        if (maskUrl == null || maskUrl.isBlank()) {
            throw new AiInferenceException("AI 分割结果图地址为空");
        }
        URI resolved = baseUri.resolve(maskUrl);
        if (!baseUri.getScheme().equalsIgnoreCase(resolved.getScheme())
                || !baseUri.getAuthority().equalsIgnoreCase(resolved.getAuthority())) {
            throw new AiInferenceException("AI 分割结果图地址不受信任");
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
}

