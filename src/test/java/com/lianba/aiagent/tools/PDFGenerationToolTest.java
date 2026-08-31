package com.lianba.aiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PDFGenerationToolTest {

    @Test
    void generatePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "编程导航原创项目.pdf";
        String content = "编程导航原创项目 https://github.com/shenzhou-ruofeng";
        String result = tool.generatePDF(fileName, content, null);
        assertNotNull(result);
    }
}