package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class WorkflowTest {

    @Test
    public void testWorkflowWithStatusFromMap() {

        Metadata metadata = new Metadata();
        Spec spec = new Spec();
        Integer code = 1;
        String message = "abc";

        Map<String, Object> status = new HashMap<>();
        status.put("phase", "one");

        Workflow workflow = new Workflow(metadata, spec, status, code, message);
        assertThat(workflow.getStatus().getPhase(), is("one"));
        assertThat(workflow.getStatusAsString(), nullValue());
    }

    @Test
    public void testWorkflowWithStatusFromString() {

        Metadata metadata = new Metadata();
        Spec spec = new Spec();
        Integer code = 1;
        String message = "abc";

        String status = "Failure";

        Workflow workflow = new Workflow(metadata, spec, status, code, message);
        assertThat(workflow.getStatus(), nullValue());
        assertThat(workflow.getStatusAsString(), is("Failure"));
    }

}
