package com.tmobile.deep.internalscheduler.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CronUtilsTest {

    @Test
    void shouldConvertValidUnixToQuartz() {
        String result = CronUtils.convertUnixToQuartz("*/5 * * * *");
        assertEquals("0 */5 * ? * *", result);
    }

    @Test
    void shouldThrowExceptionForInvalidUnixCron() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            CronUtils.convertUnixToQuartz("*/5 * *");
        });
        assertTrue(exception.getMessage().contains("Invalid Unix Cron Expression"));
    }
}