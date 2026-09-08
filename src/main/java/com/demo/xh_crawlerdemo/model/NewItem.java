package com.demo.xh_crawlerdemo.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewItem {
    private Long docId;        // 文档ID(已设置唯一索引，可去重，如果数据量大，可以考虑使用redis)
    private String title;      // 标题
    private String pubTime;    // 时间
    private String linkUrl;    // 链接

}
