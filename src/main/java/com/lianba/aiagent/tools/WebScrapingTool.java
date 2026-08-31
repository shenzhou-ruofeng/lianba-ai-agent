package com.lianba.aiagent.tools;

import com.lianba.aiagent.common.RetryUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

/**
 * 智能网页抓取工具 - 按需提取文本、图片、链接，避免抓取完整HTML导致上下文溢出
 */
@Slf4j
public class WebScrapingTool {

    private static final int MAX_TEXT_LENGTH = 8000;
    private static final int MAX_IMAGES = 30;
    private static final int MAX_LINKS = 50;
    private static final int CONNECTION_TIMEOUT_MS = 15000;
    private static final int MAX_ATTEMPTS = 3;

    /**
     * 构建带完整浏览器指纹的 Jsoup 连接，提高反爬成功率
     */
    private org.jsoup.Connection buildConnection(String url) {
        return Jsoup.connect(url)
                .timeout(CONNECTION_TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("DNT", "1")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .ignoreHttpErrors(true)
                .followRedirects(true);
    }

    /**
     * 带重试的网页抓取（基于 Guava Retrying：最多 3 次尝试 + 指数退避）
     */
    private Document fetchWithRetry(String url, String operation) throws IOException {
        try {
            return RetryUtils.executeWithRetry(operation, MAX_ATTEMPTS, () -> buildConnection(url).get());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(operation + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * 抓取网页纯文本内容（自动去除HTML标签），自动截断过长内容
     */
    @Tool(description = "Extract clean text content from a web page (HTML tags removed, auto-truncated). Use this to read/analyze page content.")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document document = fetchWithRetry(url, "scrapeWebPage");

            String title = document.title();
            // 移除无意义标签
            document.select("script, style, noscript, iframe, nav, footer, header, aside").remove();
            String text = document.text();

            StringBuilder result = new StringBuilder();
            result.append("页面标题: ").append(title).append("\n\n");

            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH)
                        + "\n\n... (内容已截断，原文共 " + text.length() + " 字符)";
            }
            result.append(text);

            log.info("Scraped text from {}: {} chars", url, text.length());
            return result.toString();
        } catch (Exception e) {
            log.error("Error scraping web page {}: {}", url, e.getMessage());
            return "抓取网页失败: " + e.getMessage();
        }
    }

    /**
     * 只提取网页中的图片URL（不抓取整个HTML），用于PDF生成或图片下载场景
     */
    @Tool(description = "Extract only image URLs from a web page (not the full HTML). Use this for PDF generation or image downloading — returns compact URL list.")
    public String scrapeWebPageImages(@ToolParam(description = "URL of the web page to extract images from") String url) {
        try {
            Document document = fetchWithRetry(url, "scrapeWebPageImages");

            Elements images = document.select("img[src]");

            StringBuilder result = new StringBuilder();
            result.append("从 ").append(url).append(" 提取到 ").append(images.size()).append(" 张图片:\n\n");

            int count = 0;
            for (Element img : images) {
                if (count >= MAX_IMAGES) {
                    result.append("\n... (共 ").append(images.size())
                            .append(" 张，仅显示前 ").append(MAX_IMAGES).append(" 张)");
                    break;
                }
                String src = img.attr("abs:src");
                String alt = img.attr("alt");
                if (src.isEmpty()) continue;

                result.append(count + 1).append(". ").append(src);
                if (!alt.isEmpty()) {
                    result.append(" | 描述: ").append(alt);
                }
                result.append("\n");
                count++;
            }

            if (count == 0) {
                result.append("该页面未找到任何图片");
            }

            log.info("Extracted {} images from {}", count, url);
            return result.toString();
        } catch (Exception e) {
            log.error("Error extracting images from {}: {}", url, e.getMessage());
            return "提取图片失败: " + e.getMessage();
        }
    }

    /**
     * 只提取网页中的超链接，用于页面导航和发现相关资源
     */
    @Tool(description = "Extract only hyperlinks from a web page. Use this to discover related pages or navigate a website.")
    public String scrapeWebPageLinks(@ToolParam(description = "URL of the web page to extract links from") String url) {
        try {
            Document document = fetchWithRetry(url, "scrapeWebPageLinks");

            Elements links = document.select("a[href]");

            StringBuilder result = new StringBuilder();
            result.append("从 ").append(url).append(" 提取到 ").append(links.size()).append(" 个链接:\n\n");

            int count = 0;
            for (Element link : links) {
                if (count >= MAX_LINKS) {
                    result.append("\n... (共 ").append(links.size())
                            .append(" 个，仅显示前 ").append(MAX_LINKS).append(" 个)");
                    break;
                }
                String href = link.attr("abs:href");
                String text = link.text().trim();
                if (href.isEmpty() || text.isEmpty()) continue;

                result.append(count + 1).append(". [").append(text).append("](").append(href).append(")\n");
                count++;
            }

            log.info("Extracted {} links from {}", count, url);
            return result.toString();
        } catch (Exception e) {
            log.error("Error extracting links from {}: {}", url, e.getMessage());
            return "提取链接失败: " + e.getMessage();
        }
    }
}
