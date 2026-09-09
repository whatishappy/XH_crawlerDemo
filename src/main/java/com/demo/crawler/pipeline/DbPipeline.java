package com.demo.crawler.pipeline;

import com.demo.crawler.mapper.LegalNewsMapper;
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
 *
 * 降级设计（数据库不可用时不影响爬虫）：
 *  1. {@link #checkAvailable()} 用 SELECT 1 探测数据库连通性；
 *  2. 一旦探测失败或首次入库失败，置 degraded 熔断标志，
 *     后续所有数据直接跳过入库（不反复尝试、不刷屏）；
 *  3. 爬虫与 CSV/控制台输出不受影响——结果始终能回到控制台。
 */
@Component
public class DbPipeline implements Pipeline {

    private static final Logger log = LoggerFactory.getLogger(DbPipeline.class);

    private final LegalNewsMapper mapper;

    /** 熔断标志：数据库不可用后置 true，跳过后续全部入库 */
    private volatile boolean degraded = false;

    /** 统计：新插入条数 */
    private int inserted;

    /** 统计：因唯一索引冲突被跳过的条数 */
    private int skipped;

    public DbPipeline(LegalNewsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 探测数据库是否可用；不可用时自动进入降级模式。
     *
     * @return true 表示可用；false 表示已降级（后续入库被跳过）
     */
    public boolean checkAvailable() {
        try {
            mapper.ping();
            degraded = false;
            return true;
        } catch (Exception e) {
            degraded = true;
            log.warn("数据库连接不可用（{}），本次运行将降级为仅控制台/CSV 输出，不影响爬虫执行", e.getMessage());
            return false;
        }
    }

    /** 是否已降级（数据库不可用） */
    public boolean isDegraded() {
        return degraded;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(ResultItems resultItems, Task task) {
        // 已熔断：跳过全部入库
        if (degraded) {
            return;
        }
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
                // 首次入库失败即熔断：数据库整体不可用，避免逐条刷警告
                degraded = true;
                log.warn("数据库入库失败，降级为仅控制台/CSV 输出（{}）；其余数据不再尝试入库", e.getMessage());
                return;
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
