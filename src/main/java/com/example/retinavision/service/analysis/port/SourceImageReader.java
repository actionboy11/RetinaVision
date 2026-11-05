package com.example.retinavision.service.analysis.port;

import com.example.retinavision.pojo.analysis.dto.SourceImage;

public interface SourceImageReader {

    SourceImage getRequired(long imageFileId);
}
