package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.K8sJobParams.Subsetting;
import com.cgi.eoss.platform.rpc.InputBinding;
import com.cgi.eoss.platform.rpc.Job;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.JobSpec;
import com.cgi.eoss.platform.rpc.OutputBinding;
import com.cgi.eoss.platform.rpc.ResourceRequest;
import com.cgi.eoss.platform.rpc.ResourceSpec;
import com.cgi.eoss.platform.rpc.UserMount;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.utils.JobParamComparator;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.utils.WorkflowComponentUtils;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Arguments;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.ConfigMap;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Container;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.EnvVar;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.ImagePullSecret;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Inputs;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Metadata;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Parameter;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Resources;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.SecretKeyRef;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Spec;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Step;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Template;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.TemplateMetadata;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.ValueFrom;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Volume;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.VolumeClaimTemplate;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.VolumeClaimVolumeSource;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.VolumeMount;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.VolumeRequests;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.VolumeResources;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.VolumeSpec;
import com.cgi.eoss.platform.core.processing.worker.kubernetes.workflow.model.Workflow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1VolumeResourceRequirements;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Core implementation of {@link LegacyWorkflowBuilder} that constructs an Argo legacy {@link Workflow}
 * composed of three sequential stages: input downloading, service processing, and output uploading.
 */
@AllArgsConstructor
public class CoreLegacyWorkflowBuilder implements LegacyWorkflowBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new GuavaModule());

    private static final String WORKER_WORKING_DIR = "/home/worker/workDir";

    private static final String PLATFORM_JOB_ID_LABEL = "platform/jobid";
    private static final String PLATFORM_INTERNAL_JOB_ID_LABEL = "platform/intjobid";
    private static final String PLATFORM_WORKFLOW_STEP_LABEL = "platform/workflow-step";
    private static final String PLATFORM_WORKFLOW_TYPE_LABEL = "platform/workflow-type";
    private static final String INPUT_BASE_PATH = "input-basepath";
    private static final String INPUT_INPUTS = "input-inputs";
    private static final String INPUT_JOB_ID = "input-jobId";
    private static final String OUTPUT_BASE_PATH = "output-basepath";
    private static final String OUTPUT_JOB_ID = "output-jobId";
    private static final String OUTPUT_INTERNAL_JOB_ID = "output-internal-jobId";
    private static final String OUTPUT_OUTPUTS = "output-outputs";
    private static final String INPUT_DOWNLOADER_CONFIG_MAP_VOLUME_NAME = "input-downloader-config-map-volume";
    private static final String OUTPUT_UPLOADER_CONFIG_MAP_VOLUME_NAME = "output-uploader-config-map-volume";
    private static final String APPLICATION_CONFIG_DIRECTORY = "/config";
    private static final String APPLICATION_CONFIG_LOCATION = "optional:file:" + APPLICATION_CONFIG_DIRECTORY + "/";

    private static final String DEFAULT_STORAGE = "10240Mi";
    private final LegacyWorkflowProperties workflowProperties;

    @Override
    public Workflow getLegacyWorkflow(JobSpec jobSpec, String existingClaimName) {
        // A legacy workflow is made of input- user docker - output
        Workflow workflow = new Workflow();
        Metadata metadata = new Metadata();

        Job job = jobSpec.getJob();
        metadata.getLabels().put(PLATFORM_JOB_ID_LABEL, job.getId());
        metadata.getLabels().put(PLATFORM_INTERNAL_JOB_ID_LABEL, job.getIntJobId());
        metadata.getLabels().put(PLATFORM_WORKFLOW_TYPE_LABEL, "job");

        metadata.setGenerateName("workflow-");
        workflow.setMetadata(metadata);
        Spec spec = new Spec();
        spec.setEntrypoint("workflowTemplate");
        ImagePullSecret externalRegistryImagePullSecret = new ImagePullSecret(workflowProperties.getExternalRegistryImagePullSecretName());
        spec.getImagePullSecrets().add(externalRegistryImagePullSecret);
        ImagePullSecret internalPlatformRegistryImagePullSecret = new ImagePullSecret(workflowProperties.getInternalPlatformRegistryImagePullSecretName());
        spec.getImagePullSecrets().add(internalPlatformRegistryImagePullSecret);
        if (Strings.isNullOrEmpty(existingClaimName)) {
            VolumeClaimTemplate volumeClaimTemplate = new VolumeClaimTemplate();
            volumeClaimTemplate.getMetadata().put("name", "workerhome");
            VolumeSpec volumeSpec = new VolumeSpec();
            volumeSpec.getAccessModes().add("ReadWriteOnce");
            VolumeResources resources = new VolumeResources();
            VolumeRequests volumeRequests = new VolumeRequests();
            if (jobSpec.hasResourceRequest() && jobSpec.getResourceRequest().getStorage() > 0) {
                volumeRequests.setStorage(jobSpec.getResourceRequest().getStorage() + "Mi");
            } else {
                volumeRequests.setStorage(DEFAULT_STORAGE);
            }
            resources.setRequests(volumeRequests);
            volumeSpec.setResources(resources);
            volumeClaimTemplate.setSpec(volumeSpec);
            spec.getVolumeClaimTemplates().add(volumeClaimTemplate);
        } else {
            Volume volume = new Volume();
            volume.setName("workerhome");
            VolumeClaimVolumeSource persistentVolumeClaim = new VolumeClaimVolumeSource();
            persistentVolumeClaim.setClaimName(existingClaimName);
            volume.setPersistentVolumeClaim(persistentVolumeClaim);
            spec.getVolumes().add(volume);
        }

        manageInputDownloaderConfigMapVolume(spec);
        manageOutputUploaderConfigMapVolume(spec);

        for (UserMount userMount : jobSpec.getUserMountList()) {
            String pvcClaimName = String.format("%s-user-mounts-pvc", userMount.getName());
            Volume userMountsVolume = new Volume();
            userMountsVolume.setName(pvcClaimName);
            VolumeClaimVolumeSource persistentVolumeClaim = new VolumeClaimVolumeSource();
            persistentVolumeClaim.setClaimName(pvcClaimName);
            userMountsVolume.setPersistentVolumeClaim(persistentVolumeClaim);
            spec.getVolumes().add(userMountsVolume);
        }

        Arguments inputArguments = new Arguments();
        inputArguments.setParameters(
                ImmutableList.of(
                        new Parameter(INPUT_INPUTS, getSerializedInputsList(jobSpec.getInputsList())),
                        new Parameter(INPUT_BASE_PATH, WORKER_WORKING_DIR),
                        new Parameter(INPUT_JOB_ID, job.getId()))
        );

        VolumeMount workerHomeVolumeMount = new VolumeMount();
        workerHomeVolumeMount.setName("workerhome");
        workerHomeVolumeMount.setMountPath(WORKER_WORKING_DIR);

        List<VolumeMount> inputDownloaderVolumeMountsList = new ArrayList<>();
        inputDownloaderVolumeMountsList.add(workerHomeVolumeMount);
        manageInputDownloaderConfigMapVolumeMount(inputDownloaderVolumeMountsList);

        Arguments outputArguments = new Arguments();
        outputArguments.setParameters(getOutputParameters(jobSpec));
        Template workflowTemplate = createWorkflowTemplate(inputArguments, outputArguments);
        spec.getTemplates().add(workflowTemplate);
        String userId = job.hasUserUUID() ? job.getUserUUID() : job.getUserId();
        spec.getTemplates().add(createInputTemplate(inputDownloaderVolumeMountsList, userId,  job.getId(), job.getIntJobId()));
        Resources resources = getResourcesForJob(jobSpec);

        Template processingTemplate
                = createProcessingTemplate(jobSpec, workerHomeVolumeMount, resources);
        if (jobSpec.hasResourceRequest() && jobSpec.getResourceRequest().hasSharedMemory()){
            WorkflowComponentUtils.addSharedMemory(
                    jobSpec.getResourceRequest().getSharedMemory(), spec, processingTemplate.getContainer()
            );
        }
        spec.getTemplates().add(processingTemplate);

        List<VolumeMount> outputUploaderVolumeMountsList = new ArrayList<>();
        outputUploaderVolumeMountsList.add(workerHomeVolumeMount);
        manageOutputUploaderConfigMapVolumeMount(outputUploaderVolumeMountsList);
        spec.getTemplates().add(createOutputTemplate(outputUploaderVolumeMountsList,jobSpec));
        workflow.setSpec(spec);

        return workflow;
    }

    @Override
    public V1PersistentVolumeClaim getPersistentVolumeClaim(JobSpec jobSpec) {
        /* TODO Temporary precreate the persistent volume claim because of bug in Argo - to be changed on argo 2.4
         *  https://github.com/argoproj/argo/issues/1130
         */
        String volumeRequests;
        if (jobSpec.hasResourceRequest() && jobSpec.getResourceRequest().getStorage() > 0) {
            volumeRequests = jobSpec.getResourceRequest().getStorage() + "Mi";
        } else {
            volumeRequests = DEFAULT_STORAGE;
        }
        V1PersistentVolumeClaim claim = new V1PersistentVolumeClaim();
        claim.setApiVersion("v1");
        claim.setKind("PersistentVolumeClaim");
        claim.setMetadata(new V1ObjectMeta());
        claim.getMetadata().setGenerateName("workflow-pvc-");
        claim.getMetadata().setNamespace(workflowProperties.getNamespace());
        claim.getMetadata().setLabels(ImmutableMap.of(PLATFORM_JOB_ID_LABEL, jobSpec.getJob().getId()));
        claim.setSpec(new V1PersistentVolumeClaimSpec()
                .addAccessModesItem("ReadWriteOnce")
                .resources(new V1VolumeResourceRequirements().requests(ImmutableMap.of("storage", new Quantity(volumeRequests)))));

        if (!Strings.isNullOrEmpty(workflowProperties.getPersistentVolumeClaimStorageClass())) {
            claim.getSpec().setStorageClassName(workflowProperties.getPersistentVolumeClaimStorageClass());
        }
        return claim;
    }

    @Override
    public String getLabelSelector(String jobId) {
        return PLATFORM_JOB_ID_LABEL + "=" + jobId;
    }

    private void manageInputDownloaderConfigMapVolumeMount(List<VolumeMount> volumeMounts) {
        if (StringUtils.isBlank(workflowProperties.getInputDownloaderConfigMapName())) {
            return;
        }

        volumeMounts.add(createConfigMapVolumeMount(INPUT_DOWNLOADER_CONFIG_MAP_VOLUME_NAME));
    }

    private void manageInputDownloaderConfigMapVolume(Spec spec) {

        if(StringUtils.isBlank(workflowProperties.getInputDownloaderConfigMapName())){
            return;
        }

        Volume inputDownloaderConfigMapVolume = new Volume();
        inputDownloaderConfigMapVolume.setName(INPUT_DOWNLOADER_CONFIG_MAP_VOLUME_NAME);
        inputDownloaderConfigMapVolume.setConfigMap(new ConfigMap(workflowProperties.getInputDownloaderConfigMapName()));
        spec.getVolumes().add(inputDownloaderConfigMapVolume);

    }

    private void manageOutputUploaderConfigMapVolumeMount(List<VolumeMount> volumeMounts) {

        if(StringUtils.isBlank(workflowProperties.getOutputUploaderConfigMapName())){
            return;
        }

        volumeMounts.add(createConfigMapVolumeMount(OUTPUT_UPLOADER_CONFIG_MAP_VOLUME_NAME));
    }

    private static VolumeMount createConfigMapVolumeMount(String volumeName) {
        VolumeMount configMapVolumeMount = new VolumeMount();
        configMapVolumeMount.setName(volumeName);
        configMapVolumeMount.setMountPath(APPLICATION_CONFIG_DIRECTORY);
        return configMapVolumeMount;
    }

    private void manageOutputUploaderConfigMapVolume(Spec spec) {

        if(StringUtils.isBlank(workflowProperties.getOutputUploaderConfigMapName())){
            return;
        }

        Volume outputUploaderConfigMapVolume = new Volume();
        outputUploaderConfigMapVolume.setName(OUTPUT_UPLOADER_CONFIG_MAP_VOLUME_NAME);
        outputUploaderConfigMapVolume.setConfigMap(new ConfigMap(workflowProperties.getOutputUploaderConfigMapName()));
        spec.getVolumes().add(outputUploaderConfigMapVolume);

    }

    private Resources getResourcesForJob(JobSpec jobSpec) {
        ResourceRequest resourceRequest = jobSpec.getResourceRequest();
        if (!hasResources(resourceRequest)) {
            return null;
        }
        Resources resources = new Resources();
        if (!Strings.isNullOrEmpty(resourceRequest.getCpus())) {
            resources.getRequests().put("cpu", resourceRequest.getCpus());
        }

        if (!Strings.isNullOrEmpty(resourceRequest.getRam())) {
            resources.getRequests().put("memory", resourceRequest.getRam());
        }

        if (resourceRequest.hasLimits()) {
            String ram = resourceRequest.getLimits().getRam();
            String cpu = resourceRequest.getLimits().getCpu();
            if (!Strings.isNullOrEmpty(ram)) {
                resources.getLimits().put("memory", ram);
            }
            if (!Strings.isNullOrEmpty(cpu)) {
                resources.getLimits().put("cpu", cpu);
            }
        }

        if (!Strings.isNullOrEmpty(resourceRequest.getGpus())) {
            resources.getRequests().put("nvidia.com/gpu", resourceRequest.getGpus());
            resources.getLimits().put("nvidia.com/gpu", resourceRequest.getGpus());
        }
        return resources;
    }

    private boolean hasResources(ResourceRequest resourceRequest) {
        return !Strings.isNullOrEmpty(resourceRequest.getCpus())
                || !Strings.isNullOrEmpty(resourceRequest.getRam())
                || hasResourceSpec(resourceRequest.getLimits())
                || !Strings.isNullOrEmpty(resourceRequest.getGpus());
    }

    private boolean hasResourceSpec(ResourceSpec resourceSpec) {
        return resourceSpec != null && !Strings.isNullOrEmpty(resourceSpec.getRam());
    }

    private Template createWorkflowTemplate(Arguments inputArguments, Arguments outputArguments) {
        Template workflowTemplate = new Template();
        workflowTemplate.setName("workflowTemplate");
        Step inputStep = new Step();
        inputStep.setName("input");
        inputStep.setTemplate("input");
        inputStep.setArguments(inputArguments);
        List<Step> inputSteps = new ArrayList<>();
        inputSteps.add(inputStep);
        Step processingStep = new Step();
        processingStep.setName("processing");
        processingStep.setTemplate("processing");
        Step outputStep = new Step();
        outputStep.setName("output");
        outputStep.setTemplate("output");
        outputStep.setArguments(outputArguments);
        List<Step> processingSteps = new ArrayList<>();
        processingSteps.add(processingStep);
        List<Step> outputSteps = new ArrayList<>();
        outputSteps.add(outputStep);
        workflowTemplate.setSteps(new ArrayList<>());
        workflowTemplate.getSteps().add(inputSteps);
        workflowTemplate.getSteps().add(processingSteps);
        workflowTemplate.getSteps().add(outputSteps);
        return workflowTemplate;
    }

    private Template createInputTemplate(List<VolumeMount> volumeMounts, String userId, String jobId, String intJobId) {
        Template inputTemplate = new Template();
        inputTemplate.setName("input");
        inputTemplate.setInputs(new Inputs(ImmutableList.of(new Parameter(INPUT_BASE_PATH, null), new Parameter(INPUT_INPUTS, null), new Parameter(INPUT_JOB_ID, null))));
        inputTemplate.setMetadata(new TemplateMetadata());
        inputTemplate.getMetadata().getLabels().put(PLATFORM_WORKFLOW_STEP_LABEL, "input-download");
        inputTemplate.getMetadata().getLabels().put(PLATFORM_JOB_ID_LABEL, jobId);
        inputTemplate.getMetadata().getLabels().put(PLATFORM_INTERNAL_JOB_ID_LABEL, intJobId);
        Container inputDownloader = new Container();
        inputDownloader.setImage(workflowProperties.getInputDownloader().getImageName());
        inputDownloader.getEnv().add(new EnvVar("BASE_PATH", "{{inputs.parameters.input-basepath}}"));
        inputDownloader.getEnv().add(new EnvVar("INPUTS", "{{inputs.parameters.input-inputs}}"));
        inputDownloader.getEnv().add(new EnvVar("JOB_ID", "{{inputs.parameters.input-jobId}}"));
        inputDownloader.getEnv().add(new EnvVar("JOB_OWNER", userId));
        inputDownloader.getEnv().add(new EnvVar("SPRING_APPLICATION_NAME", "inputdownloader"));
        addApplicationConfigLocationEnv(inputDownloader, workflowProperties.getInputDownloaderConfigMapName());
        inputDownloader.getEnv().add(new EnvVar("TEMPFOLDER", "{{inputs.parameters.input-basepath}}/tmp"));

        workflowProperties.getInputDownloader().getEnvs().stream()
                .filter(environment -> environment.getValueFrom() != null)
                .map(this::createEnvVar)
                .forEach(inputDownloader.getEnv()::add);

        inputTemplate.setContainer(inputDownloader);
        inputDownloader.getVolumeMounts().addAll(volumeMounts);
        return inputTemplate;
    }

    private Template createProcessingTemplate(JobSpec jobSpec, VolumeMount volumeMount, Resources resources) {
        Template processingTemplate = new Template();
        processingTemplate.setName("processing");
        processingTemplate.setMetadata(new TemplateMetadata());
        processingTemplate.getMetadata().getLabels().put(PLATFORM_JOB_ID_LABEL, jobSpec.getJob().getId());
        processingTemplate.getMetadata().getLabels().put(PLATFORM_INTERNAL_JOB_ID_LABEL, jobSpec.getJob().getIntJobId());
        processingTemplate.getMetadata().getLabels().put(PLATFORM_WORKFLOW_STEP_LABEL, "processing");
        Container processingContainer = new Container();
        processingContainer.setImage(buildProcessingImage(jobSpec.getService().getDockerImageTag()));
        processingContainer.getVolumeMounts().add(volumeMount);
        for (UserMount userMount : jobSpec.getUserMountList()) {
            VolumeMount mount = new VolumeMount();
            mount.setName(String.format("%s-user-mounts-pvc", userMount.getName()));
            mount.setMountPath(userMount.getTargetPath());
            mount.setSubPath(userMount.getMountPath());
            mount.setReadOnly("ro".equals(userMount.getType()));
            processingContainer.getVolumeMounts().add(mount);
        }

        for (Entry<String, String> entry : jobSpec.getEnvironmentVariablesMap().entrySet()) {
            processingContainer.getEnv().add(new EnvVar(entry.getKey(), entry.getValue()));
        }

        workflowProperties.getProcessing().getEnvs().stream()
                .filter(environment -> environment.getValueFrom() != null)
                .map(this::createEnvVar)
                .forEach(processingContainer.getEnv()::add);

        if (isCwl(jobSpec.getService().getDescriptorType())) {
            addCWLParameters(jobSpec, processingContainer, volumeMount);
        }

        processingContainer.setWorkingDir(WORKER_WORKING_DIR);
        processingContainer.setResources(resources);
        processingTemplate.setContainer(processingContainer);
        return processingTemplate;
    }

    private void addCWLParameters(JobSpec jobSpec, Container processingContainer, VolumeMount mount) {
        if (!jobSpec.getDockerCommand().isEmpty()) {
            processingContainer.setCommand(ImmutableList.of(jobSpec.getDockerCommand()));
        }

        List<String> args = new ArrayList<>();

        if (!jobSpec.getDockerArgumentsList().isEmpty()) {
            args.addAll(jobSpec.getDockerArgumentsList());
        }

        args.addAll(getParamValuesSortedByPosition(jobSpec.getInputsList(), mount));

        processingContainer.setArgs(args);
    }

    private Template createOutputTemplate(List<VolumeMount> volumeMounts, JobSpec jobSpec) {
        Template outputTemplate = new Template();
        outputTemplate.setName("output");
        outputTemplate.setInputs(new Inputs(getOutputTemplateParameters(jobSpec)));
        outputTemplate.setMetadata(new TemplateMetadata());
        outputTemplate.getMetadata().getLabels().put(PLATFORM_WORKFLOW_STEP_LABEL, "output-upload");
        outputTemplate.getMetadata().getLabels().put(PLATFORM_JOB_ID_LABEL, jobSpec.getJob().getId());
        Container outputUploader = new Container();
        outputUploader.setImage(workflowProperties.getOutputUploader().getImageName());
        outputUploader.getEnv().add(new EnvVar("BASE_PATH", "{{inputs.parameters.output-basepath}}"));
        outputUploader.getEnv().add(new EnvVar("JOB_ID", "{{inputs.parameters.output-jobId}}"));
        outputUploader.getEnv().add(new EnvVar("SPRING_APPLICATION_NAME", "outputuploader"));
        addApplicationConfigLocationEnv(outputUploader, workflowProperties.getOutputUploaderConfigMapName());
        if (isCwl(jobSpec.getService().getDescriptorType()) &&
                hasStageOutParameters(getStageOutParameters(jobSpec.getOutputsList()))) {
            outputUploader.getEnv().add(new EnvVar("OUTPUTS", "{{inputs.parameters.output-outputs}}"));
        }

        workflowProperties.getOutputUploader().getEnvs().stream()
                .filter(environment -> environment.getValueFrom() != null)
                .map(this::createEnvVar)
                .forEach(outputUploader.getEnv()::add);


        outputUploader.getVolumeMounts().addAll(volumeMounts);
        outputTemplate.setContainer(outputUploader);
        return outputTemplate;

    }

    private static void addApplicationConfigLocationEnv(Container container, String configMapName) {
        if (StringUtils.isNotBlank(configMapName)) {
            container.getEnv().add(
                    new EnvVar("SPRING_CONFIG_ADDITIONAL_LOCATION", APPLICATION_CONFIG_LOCATION)
            );
        }
    }

    private EnvVar createEnvVar(LegacyWorkflowProperties.Environment environment) {

        LegacyWorkflowProperties.Secret secret = environment.getValueFrom().getSecret();
        return new EnvVar(environment.getName(), new ValueFrom(new SecretKeyRef(secret.getName(), secret.getKey())));
    }

    private static String getSerializedInputsList(List<JobParam> inputsList) {

        K8sJobParams k8sJobParams = new K8sJobParams();
        inputsList.forEach(jobParam -> addJobParamToK8sParams(jobParam, k8sJobParams));

        return serialize(k8sJobParams.getJobParams());
    }

    private static void addJobParamToK8sParams(JobParam jobParam, K8sJobParams k8sJobParams) {

        if (jobParam.hasSubsetting()) {
            k8sJobParams.put(jobParam.getParamName(), jobParam.getType(), jobParam.getParamValueList(), mapSubsettingFromJobParam(jobParam.getSubsetting()));
        } else {
            k8sJobParams.put(jobParam.getParamName(), jobParam.getType(), jobParam.getParamValueList());
        }
    }

    private static Subsetting mapSubsettingFromJobParam(com.cgi.eoss.platform.rpc.Subsetting jobParamSubsetting) {
        return Subsetting.builder()
                .aoi(jobParamSubsetting.getAoi())
                .format(jobParamSubsetting.getFormat())
                .build();
    }

    private static String serialize(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private List<String> getParamValuesSortedByPosition(List<JobParam> inputsList, VolumeMount mount) {
        return inputsList.stream()
                .filter(JobParam::hasInputBinding)
                .sorted(new JobParamComparator())
                .flatMap(jp -> getParamValues(jp, mount))
                .collect(Collectors.toList());
    }

    private static Stream<String> getParamValues(JobParam jobParam, VolumeMount volumeMount) {
        Stream<String> paramValues = jobParam.getParamValueList().stream();
        if (isStacDirectoryParameter(jobParam)) {
            paramValues = Stream.of(
                    String.format("%s/stageIn/%s", volumeMount.getMountPath(), jobParam.getParamName())
            );
        }
        if (isFileParameter(jobParam)) {
            paramValues = Stream.of(
                    String.format(WORKER_WORKING_DIR.concat("/inDir/%s"), jobParam.getParamName())
            );
        }
        InputBinding inputBinding = jobParam.getInputBinding();
        if (inputBinding.hasPrefix()) {
            String prefix = inputBinding.getPrefix().concat(" ");
            return paramValues.map(prefix::concat).collect(Collectors.toList()).stream();
        }
        return paramValues;
    }

    private static List<Parameter> getOutputParameters(JobSpec jobSpec) {
        List<Parameter> parameterList = new ArrayList<>();
        List<JobParam> outputsList = jobSpec.getOutputsList();
        parameterList.add(new Parameter(OUTPUT_BASE_PATH, getOutputBasePath(outputsList)));
        parameterList.add(new Parameter(OUTPUT_JOB_ID, jobSpec.getJob().getId()));
        parameterList.add(new Parameter(OUTPUT_INTERNAL_JOB_ID, jobSpec.getJob().getIntJobId()));

        List<JobParam> stageOutParams = getStageOutParameters(outputsList);

        if (isCwl(jobSpec.getService().getDescriptorType()) && hasStageOutParameters(stageOutParams)) {
            parameterList.add(new Parameter(OUTPUT_OUTPUTS, getSerializedOutputList(stageOutParams)));
        }

        return parameterList;
    }

    private static String getSerializedOutputList(List<JobParam> outputsList) {
        Map<String, String> outputs = new HashMap<>();
        for (JobParam output : outputsList) {
            outputs.put(output.getParamName(), WORKER_WORKING_DIR.concat("/" + output.getOutputBinding().getGlob()));
        }
        return serialize(outputs);
    }

    private static List<Parameter> getOutputTemplateParameters(JobSpec jobSpec) {
        List<Parameter> params = new ArrayList<>(ImmutableList.of(
                new Parameter(OUTPUT_BASE_PATH, null),
                new Parameter(OUTPUT_JOB_ID, null),
                new Parameter(OUTPUT_INTERNAL_JOB_ID, null)));
        if (isCwl(jobSpec.getService().getDescriptorType()) &&
                hasStageOutParameters(getStageOutParameters(jobSpec.getOutputsList()))) {
            params.add(new Parameter(OUTPUT_OUTPUTS, null));
        }
        return params;
    }

    private static List<JobParam> getStageOutParameters(List<JobParam> outputsList) {
        return outputsList.stream()
                .filter(jobParam -> jobParam.hasOutputBinding() && isStacDirectoryParameter(jobParam))
                .collect(Collectors.toList());
    }

    private static String getOutputBasePath(List<JobParam> outputsList) {
        String outputPath = outputsList.stream()
                .filter(CoreLegacyWorkflowBuilder::isFileParameter)
                .filter(JobParam::hasOutputBinding)
                .map(JobParam::getOutputBinding).map(OutputBinding::getGlob)
                .findFirst()
                .orElse("/outDir");
        if (outputPath.startsWith("/")) {
            return WORKER_WORKING_DIR.concat(outputPath);
        }
        return WORKER_WORKING_DIR.concat("/" + outputPath);
    }

    private static boolean isCwl(String descriptorType) {
        return "CWL".equalsIgnoreCase(descriptorType);
    }

    private static boolean hasStageOutParameters(List<JobParam> stageOutParameters) {
        return !stageOutParameters.isEmpty();
    }

    private static boolean isStacDirectoryParameter(JobParam jobParam) {
        return "STAC".equalsIgnoreCase(jobParam.getType());
    }

    private static boolean isFileParameter(JobParam jobParam) {
        return "URL".equals(jobParam.getType());
    }

    private String buildProcessingImage(String dockerImageTag) {
        String prefix = workflowProperties.getInternalPlatformRegistryPrefix();
        return Strings.isNullOrEmpty(prefix) ? dockerImageTag : prefix + "/" + dockerImageTag;
    }
}