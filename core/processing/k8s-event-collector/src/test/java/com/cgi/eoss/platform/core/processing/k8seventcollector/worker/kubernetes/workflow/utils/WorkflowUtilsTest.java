package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.utils;


import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Metadata;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Spec;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Status;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Workflow;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.WorkflowList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class WorkflowUtilsTest {

    private static final Metadata EMPTY_METADATA = new Metadata();

    @Test
    public void testNextResourceVersionFromMetadata() {
        Metadata metadata = new Metadata();
        metadata.setResourceVersion("1234");

        Spec spec = null;
        Status status = null;
        Integer code = null;
        String message = null;

        Workflow workflow = new Workflow(metadata, spec, status, code, message);
        assertThat(WorkflowUtils.nextResourceVersion(workflow), is("1234"));
    }

    @Test
    public void testNextResourceVersionFromErrorMessage410() {

        Metadata metadata = new Metadata();

        Spec spec = null;
        Status status = null;
        Integer code = 410;
        String message = "too old resource version: 7196991 (7201786)";

        Workflow workflow = new Workflow(metadata, spec, status, code, message);
        assertThat(WorkflowUtils.nextResourceVersion(workflow), is("7201786"));
    }

    @Test
    public void testNextResourceVersionFromErrorMessage404() {

        Metadata metadata = new Metadata();

        Spec spec = null;
        Status status = null;
        Integer code = 404;
        String message = "too old resource version: 7196991 (7201786)";

        Workflow workflow = new Workflow(metadata, spec, status, code, message);

        assertThat(WorkflowUtils.nextResourceVersion(workflow), is(""));
    }

    @Test
    public void testNextResourceVersionFromErrorMessageWronglyFormatted() {

        Metadata metadata = new Metadata();

        Spec spec = null;
        Status status = null;
        Integer code = 410;
        String message = "too old resource version: 7196991 7201786";

        Workflow workflow = new Workflow(metadata, spec, status, code, message);

        assertThat(WorkflowUtils.nextResourceVersion(workflow), is(""));
    }

    @Test
    public void testDeserializeWorkflowWithError410AndStatusAsString() {

        final String json = "{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\",\"message\":\"too old resource version: 9978791 (9984644)\",\"reason\":\"Gone\",\"code\":410}";

        Workflow workflow = WorkflowUtils.deserializeWorkflow(json);

        assertThat(workflow.getCode(), is(410));
        assertThat(workflow.getKind(), is("Workflow"));
        assertThat(workflow.getMessage(), is("too old resource version: 9978791 (9984644)"));
        assertThat(workflow.getMetadata(), is(EMPTY_METADATA));
        assertThat(workflow.getSpec(), nullValue());
        assertThat(workflow.getStatusAsString(), is("Failure"));
        assertThat(workflow.getStatus(), nullValue());
    }

    @Test
    public void testDeserializeWorkflowWithError410AndStatusAsObject() {
        final String json = "{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":{ \"phase\": \"one\", \"finishedAt\": \"2020-07-30T07:57:31Z\", \"startedAt\": \"2020-07-30T07:56:56Z\"},\"message\":\"too old resource version: 9978791 (9984644)\",\"reason\":\"Gone\",\"code\":410}";

        Workflow workflow = WorkflowUtils.deserializeWorkflow(json);

        assertThat(workflow.getCode(), is(410));
        assertThat(workflow.getKind(), is("Workflow"));
        assertThat(workflow.getMessage(), is("too old resource version: 9978791 (9984644)"));
        assertThat(workflow.getMetadata(), is(EMPTY_METADATA));
        assertThat(workflow.getSpec(), nullValue());
        assertThat(workflow.getStatusAsString(), nullValue());
        assertThat(workflow.getStatus().getPhase(), is("one"));
        assertThat(workflow.getStatus().getFinishedAt(), is(LocalDateTime.of(2020, Month.JULY, 30, 07, 57, 31).toInstant(ZoneOffset.UTC)));
        assertThat(workflow.getStatus().getStartedAt(), is(LocalDateTime.of(2020, Month.JULY, 30, 07, 56, 56).toInstant(ZoneOffset.UTC)));
    }

    @Test
    public void testDeserializeWorkflowListFromString() {
        final String json = "{\"items\" : [{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":{ \"phase\": \"one\"},\"message\":\"too old resource version: 9978791 (9984644)\",\"reason\":\"Gone\",\"code\":410}] }";

        WorkflowList workflowList = WorkflowUtils.deserializeWorkflowList(json);

        assertThat(workflowList.getItems().size(), is(1));

        Workflow workflow = workflowList.getItems().get(0);
        assertThat(workflow.getCode(), is(410));
        assertThat(workflow.getKind(), is("Workflow"));
        assertThat(workflow.getMessage(), is("too old resource version: 9978791 (9984644)"));
        assertThat(workflow.getMetadata(), is(EMPTY_METADATA));
        assertThat(workflow.getSpec(), nullValue());
        assertThat(workflow.getStatusAsString(), nullValue());
        assertThat(workflow.getStatus().getPhase(), is("one"));
    }

    @Test
    public void testDeserializeWorkflowFromMap() {

        Map<String, Object> gsonObject = new HashMap<>();
        gsonObject.put("kind", "Status");
        gsonObject.put("metadata", ImmutableMap.of());
        gsonObject.put("status", ImmutableMap.of("phase", "one"));
        gsonObject.put("message", "too old resource version: 9978791 (9984644)");
        gsonObject.put("code", 410);

        Workflow workflow = WorkflowUtils.deserializeWorkflow(gsonObject);

        assertThat(workflow.getCode(), is(410));
        assertThat(workflow.getKind(), is("Workflow"));
        assertThat(workflow.getMessage(), is("too old resource version: 9978791 (9984644)"));
        assertThat(workflow.getMetadata(), is(EMPTY_METADATA));
        assertThat(workflow.getSpec(), nullValue());
        assertThat(workflow.getStatusAsString(), nullValue());
        assertThat(workflow.getStatus().getPhase(), is("one"));
    }

    @Test
    public void testDeserializeWorkflowListFromMap() {

        Map<String, Object> item = new HashMap<>();
        item.put("kind", "Status");
        item.put("metadata", ImmutableMap.of());
        item.put("status", ImmutableMap.of("phase", "one"));
        item.put("message", "too old resource version: 9978791 (9984644)");
        item.put("code", 410);

        Map<String, Object> gsonObject = new HashMap<>();
        gsonObject.put("items", ImmutableList.of(item));

        WorkflowList workflowList = WorkflowUtils.deserializeWorkflowList(gsonObject);

        assertThat(workflowList.getItems().size(), is(1));

        Workflow workflow = workflowList.getItems().get(0);
        assertThat(workflow.getCode(), is(410));
        assertThat(workflow.getKind(), is("Workflow"));
        assertThat(workflow.getMessage(), is("too old resource version: 9978791 (9984644)"));
        assertThat(workflow.getMetadata(), is(EMPTY_METADATA));
        assertThat(workflow.getSpec(), nullValue());
        assertThat(workflow.getStatusAsString(), nullValue());
        assertThat(workflow.getStatus().getPhase(), is("one"));
    }
}
