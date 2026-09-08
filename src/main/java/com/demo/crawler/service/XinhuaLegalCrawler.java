package com.demo.crawler.service;

import com.demo.crawler.model.NewsItem;
import com.demo.crawler.pipeline.ConsolePipeline;
import com.demo.crawler.pipeline.CsvPipeline;
import com.demo.crawler.pipeline.DbPipeline;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.downloader.HttpClientDownloader;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.proxy.Proxy;
import us.codecraft.webmagic.proxy.SimpleProxyProvider;
import us.codecraft.webmagic.scheduler.Scheduler;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新华网法治专题栏目爬虫 —— 基于 WebMagic 框架实现。
 * 栏目页面  http://www.xinhuanet.com/legal/ej.htm?page=fzzt 中的列表由前端
 * xpage 组件通过 JSONP 接口异步加载（Ajax 请求），接口地址为：
 *   https://dawa.news.cn/nodeart/page?nid=11227931&pgnum={页码}&cnt=10&attr=&tp=1&orderby=1&callback=cb"
 * 其中 nid=11227931 是"法治专题"栏目的栏目 ID。
 * <p>
 *
 *   1. 基于 WebMagic 框架：PageProcessor + Spider + Pipeline 完成抓取与输出；
 *   2. 熟悉 Ajax 请求：直接请求数据接口而非页面 HTML；
 *   3. 正则表达式：剥离 JSONP 的 callback 包裹；
 *   4. JSON 数据格式：Jackson 解析 JSON 列表，提取标题 + 时间；
 *   5. HTTP 协议：Site 配置 UA / Referer / 超时 / 重试 / 代理；
 *   6. Pipeline 化输出：控制台 / CSV / MySQL 三种输出解耦。
 */
@Service
public class XinhuaLegalCrawler implements PageProcessor, ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(XinhuaLegalCrawler.class);

    /** 数据接口模板（pgnum 为页码），直接命中栏目的 Ajax 数据接口 */
    private static final String API_URL_TEMPLATE =
            "https://dawa.news.cn/nodeart/page?nid=11227931&pgnum=%d&cnt=10&attr=&tp=1&orderby=1&callback=cb";

    /** 题目要求：至少抓取 3 页 */
    private static final int MAX_PAGES = 3;

    /** JSONP 包裹正则：cb({...}) 或 callback({...})，取最外层括号内内容 */
    private static final Pattern JSONP_PATTERN = Pattern.compile("^[^(]*\\((.*)\\)\\s*$", Pattern.DOTALL);
    /*太久没写正则表达式了，
    在java中
    \\ 转义 \
    \\\\ 转义 \\
    */

    /** 从 URL 中提取页码 */
    private static final Pattern PG_NUM_PATTERN = Pattern.compile("pgnum=(\\d+)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** WebMagic 站点配置：UA / Header / 超时 / 重试 */
    private final Site site;

    /** 代理下载器，通过 ProxyProvider 配置本地转发端口 */
    private final HttpClientDownloader proxyDownloader;

    /** 控制台展示 Pipeline */
    private final ConsolePipeline consolePipeline;

    /** CSV 导出 Pipeline */
    private final CsvPipeline csvPipeline;

    /** MySQL 入库 Pipeline */
    private final DbPipeline dbPipeline;

    /**
     * Spring 注入代理配置（application.properties 中 crawler.proxy.* 前缀）
     * 使用@Vaule 注解设定默认值
     * 与三条输出 Pipeline。
     */
    public XinhuaLegalCrawler(
            @Value("${crawler.proxy.enabled:true}") boolean proxyEnabled,
            @Value("${crawler.proxy.host:127.0.0.1}") String proxyHost,
            @Value("${crawler.proxy.port:10215}") int proxyPort,
            ConsolePipeline consolePipeline,
            CsvPipeline csvPipeline,
            DbPipeline dbPipeline) {

        //网站身份
        Site s = Site.me()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setTimeOut(15000)
                .setRetryTimes(2)   //设置重试次数
                .setCharset("UTF-8")
                .setSleepTime(500)
                .addHeader("Referer", "http://www.xinhuanet.com/legal/ej.htm?page=fzzt")
                .addHeader("Accept", "application/json, text/javascript, */*; q=0.01");
        /*如果开启代理，则需要开启proxy*/
        if (proxyEnabled) {
            this.proxyDownloader = new HttpClientDownloader();
            this.proxyDownloader.setProxyProvider(
                    SimpleProxyProvider.from(new Proxy(proxyHost, proxyPort)));
            log.info("已启用 HTTP 代理：{}:{}", proxyHost, proxyPort);
        } else {
            /*未检测到代理，直接连接目标url*/
            this.proxyDownloader = null;
            log.info("未启用代理，直连目标服务器");
        }
        this.site = s;
        /*初始化保存通道，分布解耦分为控制台、csv保存、数据库保存
        * 避免其中一项报错，导致其他无法执行
        * */
        this.consolePipeline = consolePipeline;
        this.csvPipeline = csvPipeline;
        this.dbPipeline = dbPipeline;
    }

    /**
     * WebMagic 页面处理器：拿到 JSONP 原始文本 → 剥离 callback → 解析 JSON，
     * 提取标题、时间、链接；结果放入 ResultItems 供各 Pipeline 消费。
     */

    /*执行爬虫逻辑process*/
    @Override
    public void process(Page page) {
        String body = page.getJson().get();
        try {
            //通过Jsoup解析<body>标签中内容，保存为NewsItem的List集合
            List<NewsItem> items = parseJsonp(body);
            // 关键：把解析结果放进 ResultItems，Console/Csv/Db Pipeline 从这里取数
            page.putField("newsList", items);
            log.info("第 {} 页抓取成功，共 {} 条", currentPage(page), items.size());
        } catch (Exception e) {
            log.warn("第 {} 页解析失败：{}", currentPage(page), e.getMessage());
        }
    }

    /** WebMagic 站点配置 */
    @Override
    public Site getSite() {
        return site;
    }

    /**
     * 抓取前 {@link #MAX_PAGES} 页数据，Spider 自动调度各 URL，
     * 三条 Pipeline 依次消费结果（控制台 / CSV / MySQL），返回聚合后的数据列表。
     * 如果数据量大的话，则需要使用Kafaka中间件
     */

    /*执行逻辑*/
    public List<NewsItem> crawl() {
        //管理URL，分析可知每一页的page+1，前面部分不变
        String[] urls = new String[MAX_PAGES];
        for (int i = 0; i < MAX_PAGES; i++) {
            urls[i] = String.format(API_URL_TEMPLATE, i + 1);
        }

        //Spider作为爬虫容器——把WebMagic的四大组件组织起来
        //Spider 从 Scheduler 拿 URL → 交给 Downloader 下载 → 交给 PageProcessor 解析 → 交给 Pipeline 持久化
        Spider spider = Spider.create(this)
                .addUrl(urls)
                .thread(1)
                .addPipeline(consolePipeline)
                .addPipeline(csvPipeline)
                .addPipeline(dbPipeline);
        //判断是否设置代理
        if (proxyDownloader != null) {
            spider.setDownloader(proxyDownloader);
        }

        spider.run();
        // 聚合结果以 CsvPipeline 为准（按 docId 去重）

        consolePipeline.flush();          // 打印控制台表格
        return csvPipeline.collectedItems();
    }

    /**
     * 解析 JSONP 返回体。JSONP 形如 cb({...})：先用正则剥离 callback 包裹，
     * 再按 JSON 解析。若服务端直接返回纯 JSON（无括号包裹），也能正确解析。
     */
    List<NewsItem> parseJsonp(String jsonp) throws Exception {
        String json = unwrapJsonp(jsonp);
        JsonNode root = objectMapper.readTree(json);
        List<NewsItem> result = new ArrayList<>();
        JsonNode list = root.path("data").path("list");
        for (JsonNode node : list) {
            NewsItem item = new NewsItem();
            item.setDocId(node.path("DocID").asLong());
            item.setTitle(decodeHtmlEntities(node.path("Title").asText()));
            item.setPubTime(node.path("PubTime").asText());
            item.setLinkUrl(node.path("LinkUrl").asText());
            result.add(item);
        }
        return result;
    }

    /** 剥离 JSONP 的 callback 包裹：取第一对最外层括号之间的内容。 */

    //使用静态方法，给单元测试预留，搞不准那天获取jsonp用不了了
    static String unwrapJsonp(String jsonp) {
        Matcher matcher = JSONP_PATTERN.matcher(jsonp.trim());
        return matcher.matches() ? matcher.group(1) : jsonp.trim();
    }

    /** 从当前请求 URL 提取页码，用于日志。 */
    private static int currentPage(Page page) {
        Matcher matcher = PG_NUM_PATTERN.matcher(page.getUrl().get());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /** 转义常见的 HTML 实体，保证标题可读。 */
    private static String decodeHtmlEntities(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&lsquo;", "‘")
                .replace("&rsquo;", "’")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
    /**
     * 应用启动完成后自动执行一次抓取（ApplicationRunner 回调）。
     * 只做入库与日志输出，不自动生成 CSV 文件；CSV 仍通过 /api/news/csv 手动触发。
     * 抓取失败不影响应用启动（异常已捕获并记录日志）。
     */
    /*Spring Boot 应用启动时，
    Bean 的创建是有先后顺序的，而 "自动抓取" 这件事必须发生在 "所有东西都就绪之后"

    main() 启动 SpringApplication
   │
   ├─ 创建容器、注册 Bean（XinhuaLegalCrawler、三个 Pipeline、DataSource...）
   ├─ 初始化 Tomcat，开始监听 8080
   ├─ 容器刷新完成（refresh 完成，所有 @Autowired/@Value 都注好了）
   │
   └─ 到这里，才轮到 ApplicationRunner.run() 执行
    */
    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("应用启动完成，开始自动爬取新华网法治专题数据...");
            List<NewsItem> items = crawl();
            log.info("自动爬取完成，共抓取 {} 条数据", items.size());
        } catch (Exception e) {
            log.error("自动爬取失败：{}", e.getMessage(), e);
        }
    }
}