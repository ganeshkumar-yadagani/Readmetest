package com.tmobile.deep.internalscheduler.service;

import com.tmobile.deep.internalscheduler.service.dto.DeepEmailTemplateDto;
import com.tmobile.deep.internalscheduler.util.DeepMyEmailTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class EmailServiceTest {

    @Test
    void testSendEmailWithFallbackPath() throws Exception {
        DeepMyEmailTemplate template = Mockito.mock(DeepMyEmailTemplate.class);
        EmailService emailService = new EmailService(template);
        String errorResponse = "{"timestamp":"2024-01-01T00:00:00","status":500,"message":"Internal error","path":"/mock"}";

        assertDoesNotThrow(() -> {
            emailService.sendEmail("from@mail.com", "to@mail.com", "Fail Subject", errorResponse, "/mock");
        });
    }

    @Test
    void testSendEmailWithInvalidJson() throws Exception {
        DeepMyEmailTemplate template = Mockito.mock(DeepMyEmailTemplate.class);
        EmailService emailService = new EmailService(template);
        String invalidJson = "not a json";

        assertDoesNotThrow(() -> {
            emailService.sendEmail("from@mail.com", "to@mail.com", "Fail Subject", invalidJson, "/unknown");
        });
    }
}