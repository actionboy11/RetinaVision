package com.example.retinavision.common;

import java.time.LocalDateTime;

public interface Deletable {
    LocalDateTime getDeletedAt();   // 返回删除时间，null 表示未删除
}
