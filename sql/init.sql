CREATE DATABASE IF NOT EXISTS xinhua_crawler
    DEFAULT CHARACTER SET utf8mb4;      -- utf8mb4 才能存 emoji 和生僻字

USE xinhua_crawler;

CREATE TABLE IF NOT EXISTS legal_news (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    doc_id     BIGINT       NOT NULL COMMENT '新华网文档ID（唯一）',
    title      VARCHAR(500) NOT NULL COMMENT '新闻标题',
    pub_time   VARCHAR(32)  NOT NULL COMMENT '发布时间',
    link_url   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原文链接',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
    PRIMARY KEY (id),
                                          UNIQUE KEY uk_doc_id (doc_id)  -- 设置唯一索引去重
) ENGINE = InnoDB COMMENT = '新华网法治专题新闻';