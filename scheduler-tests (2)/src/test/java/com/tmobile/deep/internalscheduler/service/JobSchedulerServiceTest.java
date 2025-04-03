package com.tmobile.deep.internalscheduler.service;

import com.tmobile.deep.internalscheduler.model.CronJob;
import com.tmobile.deep.internalscheduler.model.JobConfig;
import com.tmobile.deep.internalscheduler.util.CronUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.quartz.*;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JobSchedulerServiceTest {

    private Scheduler scheduler;
    private JobConfig jobConfig;
    private JobSchedulerService jobSchedulerService;

    @BeforeEach
    void setup() {
        scheduler = mock(Scheduler.class);
        jobConfig = new JobConfig();
        jobSchedulerService = new JobSchedulerService(scheduler, jobConfig);
    }

    @Test
    void testScheduleJobWhenJobExists() throws Exception {
        CronJob cronJob = new CronJob("existing-job", "*/5 * * * *", false, List.of("https://api.mock"));
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(true);
        jobSchedulerService.scheduleJob(cronJob);
        verify(scheduler).deleteJob(any(JobKey.class));
        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void testValidateDuplicateJobs() {
        CronJob job1 = new CronJob("dup-job", "*/5 * * * *", false, List.of("url"));
        CronJob job2 = new CronJob("dup-job", "*/10 * * * *", true, List.of("url2"));
        jobConfig.setJobs(List.of(job1, job2));
        assertThrows(IllegalArgumentException.class, () -> {
            jobSchedulerService.initializeJobs();
        });
    }

    @Test
    void testInitializeWithEmptyJobs() throws Exception {
        jobConfig.setJobs(null);
        jobSchedulerService.initializeJobs();
        assertTrue(true); // no exception thrown
    }
}