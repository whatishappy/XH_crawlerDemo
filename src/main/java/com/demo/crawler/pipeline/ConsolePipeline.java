package com.demo.crawler.pipeline;

import com.demo.crawler.model.NewsItem;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 控制台展示 Pipeline：把抓取结果按表格打印，并附带按年份统计。
 * Spider 运行结束后调用 {@link #flush()} 输出。
 */
@Component
public class ConsolePipeline implements Pipeline {

    /** 跨页聚合（按 docId 去重） */
    private final Map<Long, NewsItem> collected = new LinkedHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public void process(ResultItems resultItems, Task task) {
        Object value = resultItems.get("newsList");
        if (!(value instanceof List)) {
            return;
        }
        List<NewsItem> items = (List<NewsItem>) value;
        for (NewsItem item : items) {
            collected.putIfAbsent(item.getDocId(), item);
        }
    }

    /** Spider 运行结束后调用：打印表格与统计。 */
    public void flush() {
        List<NewsItem> items = new ArrayList<>(collected.values());
        System.out.println();
        System.out.println("===============================================================");
        System.out.println("  新华网·法治专题 爬虫结果（标题 + 时间）");
        System.out.println("===============================================================");
        if (items.isEmpty()) {
            System.out.println("  （未抓取到任何数据）");
            return;
        }

        int titleWidth = items.stream()
                .mapToInt(item -> item.getTitle().length())
                .max().orElse(20);
        titleWidth = Math.min(titleWidth, 60);
        titleWidth = Math.max(titleWidth, 6);

        String sep = "  +" + "-".repeat(titleWidth + 2) + "+" + "-".repeat(23) + "+";
        System.out.println(sep);
        System.out.printf("  | %-" + titleWidth + "s | %-21s |%n", "标题", "时间");
        System.out.println(sep);

        for (NewsItem item : items) {
            String title = truncate(item.getTitle(), titleWidth);
            System.out.printf("  | %-" + titleWidth + "s | %-21s |%n", title, item.getPubTime());
        }
        System.out.println(sep);
        System.out.printf("  共 %d 条（完整字段见 CSV 导出与 MySQL 入库）%n", items.size());

        Map<String, Long> byYear = items.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getPubTime().substring(0, 4),
                        TreeMap::new,
                        Collectors.counting()));
        System.out.println("  --- 按年份分布 ---");
        for (Map.Entry<String, Long> entry : byYear.entrySet()) {
            System.out.printf("  %s 年：%d 条%n", entry.getKey(), entry.getValue());
        }
        System.out.println("===============================================================");
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 1) + "…";
    }
}