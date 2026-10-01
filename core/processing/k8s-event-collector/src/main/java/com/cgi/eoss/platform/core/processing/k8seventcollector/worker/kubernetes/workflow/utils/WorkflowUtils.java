package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.utils;


import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.Workflow;
import com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model.WorkflowList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for the Workflow objects
 *
 * @author cantaveneraf
 *
 */
@Slf4j
public final class WorkflowUtils {

    private static final Integer ERROR_410 = 410;
    private static final Pattern ERROR_410_MESSAGE_PATTERN = Pattern.compile("too old resource version: [0-9]+ \\(([0-9]+)\\)");

    private static final Gson GSON = new Gson();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    private WorkflowUtils() {
    }

    /**
     * Retrieves the next resource version from the workflow metadata.
     * If the workflow has error code 410, the version is extracted from the error message string:
     * Example: For Message "too old resource version: 1111 (2222)" the version will be "2222"
     *
     * @param workflow
     *            The workflow object from which the version is extracted
     *
     * @return
     *         The version of the workflow object, or the empty string if no version is found.
     */
    public static String nextResourceVersion(Workflow workflow) {
        String resourceVersion = workflow.getMetadata().getResourceVersion();

        if (ERROR_410.equals(workflow.getCode())) {
            resourceVersion = parseVersionFromGoneErrorMessage(workflow.getMessage());
        }

        LOG.info("Next Workflow resource version: '{}'", resourceVersion);
        return Strings.nullToEmpty(resourceVersion);
    }

    /**
     * Deserialize a generic object representing a workflow
     *
     * @param gsonObject
     *            The object to deserialize
     * @return
     *         The deserialized workflow
     */
    public static Workflow deserializeWorkflow(Object gsonObject) {
        String workflowJson = GSON.toJson(gsonObject);
        LOG.info("Workflow json: {}", workflowJson);
        return deserializeWorkflow(workflowJson);
    }

    /**
     * Deserialize a generic object representing a workflow list
     *
     * @param gsonObject
     *            The object to deserialize
     * @return
     *         The deserialized workflow list
     */
    public static WorkflowList deserializeWorkflowList(Object gsonObject) {
        return deserializeWorkflowList(GSON.toJson(gsonObject));
    }

    /**
     * Deserialize a JSON string representing a workflow list
     *
     * @param value
     *            The JSON string to deserialize
     * @return
     *         The deserialized workflow list
     */
    public static WorkflowList deserializeWorkflowList(String value) {
        return deserialize(value, WorkflowList.class);
    }

    /**
     * Deserialize a JSON string representing a workflow
     *
     * @param value
     *            The JSON string to deserialize
     * @return
     *         The deserialized workflow
     */
    public static Workflow deserializeWorkflow(String value) {
        return deserialize(value, Workflow.class);
    }

    private static String parseVersionFromGoneErrorMessage(String message) {
        LOG.info("Parse resource version from gone error message: '{}'", message);
        Matcher m = ERROR_410_MESSAGE_PATTERN.matcher(message);
        return m.find() ? m.group(1) : "";
    }

    private static <T> T deserialize(String content, Class<T> valueType) {
        try {
            return OBJECT_MAPPER.readValue(content, valueType);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
