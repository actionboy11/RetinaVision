package com.example.retinavision.analysis.application.port.out;

import com.example.retinavision.analysis.application.model.SourceImage;

public interface SourceImageReader {

    SourceImage getRequired(long imageFileId);
}
