package com.lianba.aiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lianba.aiagent.common.RetryUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
@Slf4j
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("engine", "baidu");
        
        // 构建带 API Key 的请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");
        
        try {
            // 使用 Hutool HttpUtil 发起 GET 请求，设置超时和重试
            String response = RetryUtils.executeWithRetry("searchWeb",
                    () -> {
                        // 设置超时时间（30 秒），GET 请求参数通过 form 拼接到 URL
                        return HttpUtil.createGet(SEARCH_API_URL)
                                .form(paramMap)
                                .addHeaders(headers)
                                .timeout(30000)  // 30 秒超时
                                .execute()
                                .body();
                    });
            
            // 解析返回结果
            JSONObject jsonObject = JSONUtil.parseObj(response);
            
            // 检查是否有 error 字段
            if (jsonObject.containsKey("error")) {
                String errorMsg = jsonObject.getStr("error");
                log.warn("SearchAPI 返回错误：{}", errorMsg);
                return "百度搜索出错：" + errorMsg;
            }
            
            // 提取 organic_results 部分
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                log.warn("Baidu search returned no organic_results for query: {}", query);
                return "百度搜索未找到与 '" + query + "' 相关的结果，请勿再次搜索，改用其他方式获取信息（如 scrapeWebPage 直接访问相关网页）。";
            }
            
            int resultCount = Math.min(organicResults.size(), 5);
            List<Object> objects = organicResults.subList(0, resultCount);
            
            // 拼接搜索结果为字符串
            StringBuilder resultBuilder = new StringBuilder();
            for (int i = 0; i < objects.size(); i++) {
                JSONObject tmpJSONObject = (JSONObject) objects.get(i);
                if (i > 0) resultBuilder.append("\n\n");
                
                // 提取关键信息
                String title = tmpJSONObject.getStr("title");
                String link = tmpJSONObject.getStr("link");
                String snippet = tmpJSONObject.getStr("snippet");
                
                resultBuilder.append(String.format("[%d] %s\n链接：%s\n摘要：%s",
                        i + 1, title, link, snippet));
            }
            
            return resultBuilder.toString();
            
        } catch (Exception e) {
            log.error("搜索失败：query={}, error={}", query, e.getMessage(), e);
            
            // 区分不同类型的错误
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                return "搜索服务超时，请稍后再试。";
            } else if (e.getMessage() != null && e.getMessage().contains("network")) {
                return "网络连接异常，请检查网络状态后重试。";
            } else {
                return "搜索失败：" + e.getMessage() + "（查询关键词：" + query + "）";
            }
        }
    }
}
