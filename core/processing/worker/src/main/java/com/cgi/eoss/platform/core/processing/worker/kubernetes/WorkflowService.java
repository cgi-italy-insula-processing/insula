package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.LegacyWorkflowBuilder;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Volume;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Workflow;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.model.WorkflowInfo;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.WorkflowList;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@AllArgsConstructor
public class WorkflowService {

    private static final int HTTP_NOT_FOUND = 404;
    private static final Gson GSON = new Gson();
    private static final int K8S_TIMEOUT_SECONDS = 120;
    private static final String WORKFLOW_OBJECT_VERSION = "v1alpha1";
    private static final String WORKFLOW_OBJECT_GROUP = "argoproj.io";
    private static final String WORKFLOW_OBJECT_PLURAL = "workflows";

    private final CoreV1Api coreV1Api;
    private final CustomObjectsApi customObjectsApi;
    private final String workflowNamespace;

    private final LegacyWorkflowBuilder legacyWorkflowBuilder;

    private final ObjectMapper objectMapper;


    public void createLegacyWorkflow(JobSpec jobSpec) throws IOException, ApiException {
        V1PersistentVolumeClaim claim = legacyWorkflowBuilder.getPersistentVolumeClaim(jobSpec);
        String existingClaimName = coreV1Api.createNamespacedPersistentVolumeClaim(workflowNamespace, claim, null, null, null, null)
                    .getMetadata().getName();
        Workflow w = legacyWorkflowBuilder.getLegacyWorkflow(jobSpec, existingClaimName);
        checkUserMountPvcExists(w.getSpec().getVolumes());
        customObjectsApi.createNamespacedCustomObject(WORKFLOW_OBJECT_GROUP, WORKFLOW_OBJECT_VERSION, workflowNamespace, WORKFLOW_OBJECT_PLURAL, w, "false", null, null, null);
    }

    public void deleteWorkflowForJob(String jobId) throws ApiException, IOException {
        String labelSelector = legacyWorkflowBuilder.getLabelSelector(jobId);

        Object object = customObjectsApi.listNamespacedCustomObject(WORKFLOW_OBJECT_GROUP, WORKFLOW_OBJECT_VERSION,
                    workflowNamespace, WORKFLOW_OBJECT_PLURAL, null, null, null, null, labelSelector, null, null, null, null, Boolean.FALSE);
        String workflowListJson = GSON.toJson(object);
        WorkflowList workflowList = objectMapper.readValue(workflowListJson, WorkflowList.class);
        for (Workflow workflow : workflowList.getItems()) {
            try {
                customObjectsApi.deleteNamespacedCustomObject(WORKFLOW_OBJECT_GROUP, WORKFLOW_OBJECT_VERSION, workflowNamespace, WORKFLOW_OBJECT_PLURAL, workflow.getMetadata().getName(), null, null, null, null, new V1DeleteOptions());
            } catch (JsonSyntaxException e) {
                // Ignore exception - see https://github.com/kubernetes-client/java/issues/86
            }
        }
    }

    public void cleanUpWorkflowForJob(String jobId) throws ApiException, IOException {
        // Delete job

        String labelSelector = legacyWorkflowBuilder.getLabelSelector(jobId);
        V1PersistentVolumeClaimList pvcs = coreV1Api.listNamespacedPersistentVolumeClaim(workflowNamespace, null, null, null, null, labelSelector, null, null, null, null, K8S_TIMEOUT_SECONDS, false);
        for (V1PersistentVolumeClaim pvc : pvcs.getItems()) {
            try {
                coreV1Api.deleteNamespacedPersistentVolumeClaim(pvc.getMetadata().getName(), workflowNamespace, null, null, null, null, null, null, new V1DeleteOptions());
            } catch (JsonSyntaxException e) {
                // Ignore exception - see https://github.com/kubernetes-client/java/issues/86
            }
        }
    }

    private void checkUserMountPvcExists(List<Volume> volumes) throws IOException {
        for (Volume volume : volumes) {
            String volumeName = volume.getName();
            if (volumeName != null && volumeName.contains("user-mounts-pvc")) {
                verifyPersistentVolumeClaimExists(volumeName);
            }
        }
    }

    private void verifyPersistentVolumeClaimExists(String volumeName) throws IOException {
        try {
            coreV1Api.readNamespacedPersistentVolumeClaim(volumeName, workflowNamespace, null);
        } catch (ApiException e) {
            if (e.getCode() == HTTP_NOT_FOUND) {
                throw new IOException("PVC '" + volumeName + "' not found. Failing job.");
            }
            throw new IOException("Error checking PVC '" + volumeName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve workflow status info for a given jobId status, mapping the information as a {@link WorkflowInfo} object.
     * Could be retrieved zero or more elements for a given jobId
     * @param jobId the job id related to the workflow
     * @return A list containing the retrieved {@link WorkflowInfo}
     * @throws ApiException if an exception occurs querying the target Api
     * @throws IOException if an exception occurs reading the Api response
     */
    public List<WorkflowInfo> getWorkflowInfo(String jobId) throws IOException, ApiException {

        String labelSelector = legacyWorkflowBuilder.getLabelSelector(jobId);

        Object object = customObjectsApi.listNamespacedCustomObject(WORKFLOW_OBJECT_GROUP, WORKFLOW_OBJECT_VERSION,
                workflowNamespace, WORKFLOW_OBJECT_PLURAL,
                null, null,null, null,
                labelSelector,
                null, null, null, null,
                Boolean.FALSE);

        String workflowListJson = GSON.toJson(object);

        WorkflowList workflowList;
        try {
            workflowList = objectMapper.readValue(workflowListJson, WorkflowList.class);
        } catch (JsonProcessingException e) {
            String errorMessage = "An exception occurred reading api response for jobId: " + jobId;
            LOG.error(errorMessage, e);
            throw new IOException(errorMessage, e);
        }

        if (workflowList.getItems() == null || workflowList.getItems().isEmpty()) {
            LOG.info("Retrieved workflowList is empty");
            return Collections.emptyList();
        }
        return workflowList.getItems().stream()
                .map(WorkflowService::mapStatusToWorkflowInfo)
                .collect(Collectors.toList());
    }

    private static WorkflowInfo mapStatusToWorkflowInfo(Workflow mostRecentWorkflow) {
        WorkflowInfo workflowInfo = new WorkflowInfo();
        if(mostRecentWorkflow.getStatus() != null) {
            workflowInfo.setStartedAt(mostRecentWorkflow.getStatus().getStartedAt());
            workflowInfo.setFinishedAt(mostRecentWorkflow.getStatus().getFinishedAt());
            workflowInfo.setStatus(mostRecentWorkflow.getStatus().getPhase());
        }
        return workflowInfo;
    }

}
