package com.demo.crawler.mapper;

import com.demo.crawler.model.NewsItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 新闻数据 Mapper（由于测试比较简单，可直接写在注解中）。
 *
 * 使用 INSERT IGNORE：当 doc_id 在唯一索引 uk_doc_id 上已存在时，
 * 该行自动跳过（返回影响行数 0），天然实现"入库去重"。
 */
@Mapper
public interface LegalNewsMapper {

    /**
     * 插入一条新闻；若 doc_id 已存在则忽略。
     *
     * @return 影响行数：1 表示新插入，0 表示已存在被跳过
     */
    @Insert("INSERT IGNORE INTO legal_news(doc_id, title, pub_time, link_url) "
            + "VALUES(#{docId}, #{title}, #{pubTime}, #{linkUrl})")
    int insertIgnore(NewsItem item);
}