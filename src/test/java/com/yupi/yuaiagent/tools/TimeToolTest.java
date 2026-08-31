package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

/**
 * 时间工具单元测试
 */
class TimeToolTest {

    private final TimeTool timeTool = new TimeTool();

    @Test
    void getCurrentDateTime() {
        String result = timeTool.getCurrentDateTime("Asia/Shanghai");
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("Asia/Shanghai"));
        Assertions.assertTrue(result.contains("当前时间"));
    }

    @Test
    void getCurrentDateTimeWithDefaultTimeZone() {
        String result = timeTool.getCurrentDateTime(null);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("当前时间"));
    }

    @Test
    void getCurrentDateTimeWithInvalidTimeZone() {
        String result = timeTool.getCurrentDateTime("Not/AZone");
        Assertions.assertTrue(result.startsWith("Error"));
    }

    @Test
    void getDaysUntilFutureDate() {
        String futureDate = LocalDate.now().plusDays(10).toString();
        String result = timeTool.getDaysUntil(futureDate);
        Assertions.assertTrue(result.contains("还有 10 天"));
    }

    @Test
    void getDaysUntilToday() {
        String result = timeTool.getDaysUntil(LocalDate.now().toString());
        Assertions.assertTrue(result.contains("就是今天"));
    }

    @Test
    void getDaysUntilPastDate() {
        String pastDate = LocalDate.now().minusDays(3).toString();
        String result = timeTool.getDaysUntil(pastDate);
        Assertions.assertTrue(result.contains("已经过去 3 天"));
    }

    @Test
    void getDaysUntilInvalidDate() {
        String result = timeTool.getDaysUntil("2026/08/19");
        Assertions.assertTrue(result.startsWith("Error"));
    }
}
