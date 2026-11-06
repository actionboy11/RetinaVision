package com.example.retinavision.port.analysis;

import com.example.retinavision.pojo.analysis.dto.SourceImage;

public interface SourceImageReader {

    SourceImage getRequired(long imageFileId);
}
