package com.demo.crawler.web;

import com.demo.crawler.model.NewsItem;
import com.demo.crawler.pipeline.CsvPipeline;
import com.demo.crawler.service.XinhuaLegalCrawler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 数据展示 REST 接口：
 *   GET /api/news       返回 JSON 数据
 *   GET /api/news/csv   以附件形式下载 CSV 数据文件
 *
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final XinhuaLegalCrawler crawler;
    private final CsvPipeline csvPipeline;

    public NewsController(XinhuaLegalCrawler crawler, CsvPipeline csvPipeline) {
        this.crawler = crawler;
        this.csvPipeline = csvPipeline;
    }

    @GetMapping
    public List<NewsItem> list() {
        return crawler.crawl();
    }

    @GetMapping(value = "/csv", produces = "text/csv;charset=UTF-8")    //设定返回文件类型以及编码格式，否则报错406 Not Acceptable
    public ResponseEntity<byte[]> csv() {
        try {
            //执行爬虫接口
            List<NewsItem> items = crawler.crawl();
            //导出cvs
            Path file = csvPipeline.export(items);
            byte[] data = Files.readAllBytes(file);
            String fileName = file.getFileName().toString();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))  //在设置一遍返回文件类型，保险起见
                    .body(data);
        } catch (IOException e) {
            throw new IllegalStateException("CSV 导出失败：" + e.getMessage(), e);
        }
    }
}