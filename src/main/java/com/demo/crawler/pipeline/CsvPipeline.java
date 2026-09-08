package com.demo.crawler.pipeline;

import com.demo.crawler.model.NewsItem;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 导出 Pipeline：把各页抓取结果按 docId 聚合，
 * Spider 运行结束后调用 {@link #flush()} 导出为 UTF-8（带 BOM）CSV，
 * Excel 直接打开不乱码。
 */
@Component
public class CsvPipeline implements Pipeline {

    /** 导出目录（相对项目根目录） */
    private static final String OUTPUT_DIR = "output";

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

    /** 聚合后的全部数据（供控制台/接口使用） */
    public List<NewsItem> collectedItems() {
        return new ArrayList<>(collected.values());
    }

    /** Spider 运行结束后调用：导出 CSV，返回文件路径。 */
    public Path flush() throws IOException {
        return export(collectedItems());
    }

    /** 将指定数据导出为 CSV（REST 接口也复用它）。 */
    public Path export(List<NewsItem> items) throws IOException {
        Path dir = Paths.get(OUTPUT_DIR);
        Files.createDirectories(dir);
        String fileName = "xinhua_legal_news_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".csv";
        Path file = dir.resolve(fileName);

        StringBuilder sb = new StringBuilder();
        // 带 BOM，Excel 识别为 UTF-8
        sb.append('\uFEFF');
        sb.append("标题,时间,链接\n");
        for (NewsItem item : items) {
            sb.append(escape(item.getTitle())).append(',')
                    .append(escape(item.getPubTime())).append(',')
                    .append(escape(item.getLinkUrl())).append('\n');
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹。 */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}