package com.demo.crawler.pipeline;

import com.demo.crawler.mapper.LegalNewsMapper ;
import com.demo.crawler.model.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

import java.util.List;

/**
 * 数据库入库 Pipeline：把 PageProcessor 放到 ResultItems 里的
 * newsList 字段逐条写入 MySQL（MyBatis）。
 *
 * doc_id 唯一索引保证重复数据被 INSERT IGNORE 跳过，实现入库去重。
 * 数据库不可用时仅记录警告，不影响抓取与 CSV 导出。
 */
@Component
public class DbPipeline implements Pipeline {

    private static final Logger log = LoggerFactory.getLogger(DbPipeline.class);

    private final LegalNewsMapper mapper;

    /** 统计：新插入条数 */
    private int inserted;

    /** 统计：因唯一索引冲突被跳过的条数 */
    private int skipped;

    public DbPipeline(LegalNewsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(ResultItems resultItems, Task task) {
        Object value = resultItems.get("newsList");
        if (!(value instanceof List)) {
            return;
        }
        List<NewsItem> items = (List<NewsItem>) value;
        for (NewsItem item : items) {
            try {
                int rows = mapper.insertIgnore(item);
                if (rows > 0) {
                    inserted++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("入库失败（docId={}）：{}", item.getDocId(), e.getMessage());
            }
        }
    }

    public int getInserted() {
        return inserted;
    }

    public int getSkipped() {
        return skipped;
    }
}