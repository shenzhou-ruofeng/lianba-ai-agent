package com.lianba.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.lianba.aiagent.common.RetryUtils;
import com.lianba.aiagent.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * 资源下载工具
 */
@Slf4j
public class ResourceDownloadTool {

    @Tool(description = "Download a resource from a given HTTP/HTTPS URL. IMPORTANT: The URL MUST be a full HTTP or HTTPS URL, NOT a filename or local path. If a user mentions a filename like 'xxx.pdf', do NOT try to pass it as URL here — it will fail. Only use this tool for downloadable web resources with http:// or https:// protocol.")
    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url, @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 使用 Guava Retrying 重试下载，提升网络请求健壮性
            RetryUtils.executeWithRetry("downloadResource",
                    () -> HttpUtil.downloadFile(url, new File(filePath)));
            return "Resource downloaded successfully to: " + filePath;
        } catch (Exception e) {
            log.error("Error downloading resource from {}: {}", url, e.getMessage());
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
