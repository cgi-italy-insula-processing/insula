package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.cgi.eoss.platform.core.processing.server.model.testutils.ProcessingCoreEntities;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CoreJobPriorityCalculatorTest {

    private final JobPriorityCalculator jobPriorityCalculator = new CoreJobPriorityCalculator();
    private final int EXPECTED_DEFAULT_PRIORITY = 1;

    @Test
    public void testCalculateJobPriority_ReturnsDefaultValue_WhenJobIsNotParent() {

        User owner = ProcessingCoreEntities.createUser().build();
        Job job = createJob(owner);
        job.setParent(false);
        assertThat(jobPriorityCalculator.calculateJobPriority(job))
                .isEqualTo(EXPECTED_DEFAULT_PRIORITY);
    }

    @Test
    public void testCalculateJobPriority_ReturnsDefaultValue_WhenJobIsParent() {

        User owner = ProcessingCoreEntities.createUser().build();
        Job job = createJob(owner);
        job.setParent(true);
        assertThat(jobPriorityCalculator.calculateJobPriority(job))
                .isEqualTo(EXPECTED_DEFAULT_PRIORITY);
    }

    @Test
    public void testCalculateJobPriority_ReturnsDefaultValue_WhenJobIsSubJob() {

        User owner = ProcessingCoreEntities.createUser().build();
        Job job = createJob(owner);
        job.setParent(false);
        job.setParentJob(createJob(owner));
        assertThat(jobPriorityCalculator.calculateJobPriority(job))
                .isEqualTo(EXPECTED_DEFAULT_PRIORITY);
    }

    @Test
    public void testCalculateJobPriority_ReturnsDefaultValue_WhenJobIsNull() {

        assertThat(jobPriorityCalculator.calculateJobPriority(null))
                .isEqualTo(EXPECTED_DEFAULT_PRIORITY);
    }

    private static Job createJob(User owner) {
        return ProcessingCoreEntities.createJob(
                        owner,
                        new JobConfig(
                                owner,
                                ProcessingCoreEntities.createPlatformService(owner).build()))
                .build();
    }
}