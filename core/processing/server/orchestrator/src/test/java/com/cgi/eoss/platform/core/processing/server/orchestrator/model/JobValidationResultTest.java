package com.cgi.eoss.platform.core.processing.server.orchestrator.model;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.JobInputsValidator;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class JobValidationResultTest {

    @Mock
    private JobInputsValidator jobInputsValidator;

    @Test
    public void testAnd_ReturnsValidValidationResult_WhenChainingTwoValidValidationResult(){

        Job job = new Job();
        job.setId(1L);
        when(jobInputsValidator.validate(job, null)).thenReturn(
                new JobValidationResult(new Job(), null, true, null)
        );
        JobValidationResult validValidationResult = new JobValidationResult(new Job(), null, true, null);

        JobValidationResult actualValidationResult = validValidationResult.and(jobInputsValidator);

        Job expectedJob = new Job();
        expectedJob.setId(1L);
        Assertions.assertThat(actualValidationResult.isValid()).isTrue();
        Assertions.assertThat(actualValidationResult.getJob()).isEqualTo(expectedJob);
        Assertions.assertThat(actualValidationResult.getJobInputs()).isNull();
        Assertions.assertThat(actualValidationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testAnd_ReturnsInvalidValidationResult_WhenChainingAInvalidValidationResultAndAnValidValidationResult(){

        Job job = new Job();
        job.setId(1L);
        when(jobInputsValidator.validate(job, null)).thenReturn(
                new JobValidationResult(new Job(), null, true, null)
        );
        JobValidationResult validValidationResult = new JobValidationResult(new Job(), null, false, null);

        JobValidationResult actualValidationResult = validValidationResult.and(jobInputsValidator);

        Job expectedJob = new Job();
        job.setId(1L);
        Assertions.assertThat(actualValidationResult.isValid()).isFalse();
        Assertions.assertThat(actualValidationResult.getJob()).isEqualTo(expectedJob);
        Assertions.assertThat(actualValidationResult.getJobInputs()).isNull();
        Assertions.assertThat(actualValidationResult.getErrorMessage()).isNull();
        verify(jobInputsValidator, never()).validate(expectedJob, null);

    }

}
