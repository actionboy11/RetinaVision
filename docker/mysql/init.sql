-- Docker 只负责创建数据库；包括 Agent 评测中心及其校准语料在内的业务结构和数据由 Flyway 迁移维护。
CREATE DATABASE IF NOT EXISTS retina_vision
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
