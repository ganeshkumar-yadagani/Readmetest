package com.tmobile.deep.internalscheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmobile.deep.internalscheduler.model.CronJob;
import com.tmobile.deep.internalscheduler.service.JobSchedulerService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
public class JobControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private JobSchedulerService jobSchedulerService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void shouldScheduleJobSuccessfully() throws Exception {
        CronJob job = new CronJob("my-test-job", "*/5 * * * *", false, Collections.singletonList("https://mock.api/job"));
        mockMvc.perform(post("/schedule-job")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(job)))
            .andExpect(status().isOk())
            .andExpect(content().string("Job scheduled successfully"));
    }

    @Test
    void shouldReturnBadRequestForInvalidCronExpression() throws Exception {
        CronJob job = new CronJob("invalid-job", "invalid cron", false, Collections.singletonList("https://bad.url"));
        mockMvc.perform(post("/schedule-job")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(job)))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Invalid Unix cron expression: invalid cron"));
    }

    @Test
    void shouldReturnBadRequestForInvalidQuartzCron() throws Exception {
        CronJob job = new CronJob("bad-quartz", "0 0 31 2 *", false, Collections.singletonList("https://test"));
        mockMvc.perform(post("/schedule-job")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(job)))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid Quartz cron expression")));
    }

    @Test
    void shouldHandleExceptionFromServiceLayer() throws Exception {
        CronJob job = new CronJob("fail-job", "*/5 * * * *", false, Collections.singletonList("https://fail.api"));
        Mockito.doThrow(new RuntimeException("Service failed")).when(jobSchedulerService).scheduleJob(Mockito.any());
        mockMvc.perform(post("/schedule-job")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(job)))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Error scheduling job")));
    }
}