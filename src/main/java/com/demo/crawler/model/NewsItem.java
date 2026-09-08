package com.demo.crawler.model;

/**
 * 新闻条目模型：对应新华网法治专题列表中的一条数据。
 * 核心字段为标题 title 与发布时间 pubTime，另附带文档 ID 与原文链接。
 *
 * 可使用Lombook，考虑到idea版本与maven版本可能在不同机子上的兼容性
 * 保证任何 IDE / 命令行环境均可直接编译运行。
 */
public class NewsItem {

    /** 文档唯一 ID（用于去重） */
    private Long docId;

    /** 新闻标题 */
    private String title;

    /** 发布时间，格式如 2021-11-21 14:28:37 */
    private String pubTime;

    /** 原文链接 */
    private String linkUrl;

    public NewsItem() {
    }

    public NewsItem(Long docId, String title, String pubTime, String linkUrl) {
        this.docId = docId;
        this.title = title;
        this.pubTime = pubTime;
        this.linkUrl = linkUrl;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPubTime() {
        return pubTime;
    }

    public void setPubTime(String pubTime) {
        this.pubTime = pubTime;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    @Override
    public String toString() {
        return "NewsItem{docId=" + docId
                + ", title='" + title + '\''
                + ", pubTime='" + pubTime + '\''
                + ", linkUrl='" + linkUrl + '\'' + '}';
    }
}