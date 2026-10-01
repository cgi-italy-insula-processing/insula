package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.rpc.GrpcUtil;
import com.cgi.eoss.platform.rpc.InputBinding;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.Subsetting;
import com.cgi.eoss.platform.rpc.OutputBinding;
import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for creating {@link JobParam} objects from service descriptor parameters and job config parameters.
 */
public final class JobParamsCreator {

    private static final String JOB_PARAM_TYPE_URL = "URL";
    private static final String JOB_PARAM_TYPE_OTHER = "OTHER";
    private static final String JOB_PARAM_TYPE_STAC = "STAC";
    private static final String JOB_PARAM_NAME_INPUT = "input";
    private static final String JOB_PARAM_NAME_COLLECTION = "collection";
    private static final String JOB_PARAM_VALUE_TRUE = "true";
    private static final String JOB_PARAM_VALUE_FALSE = "false";
    private static final String JOB_PARAM_TYPE_KEY = "type";
    private static final String JOB_PARAM_FORMAT_KEY = "format";
    private static final String PREVENT_URL_DOWNLOAD = "preventUrlDownload";
    private static final String PARALLEL_INPUTS_LEGACY_KEY = "parallelInputs";

    /**
     * Creates a list of gRPC jobParams from the provided service descriptor parameters and job inputs.
     *
     * @param serviceDescriptorParameters List of service descriptor parameters.
     * @param jobInputs                  the job inputs.
     * @return the list of {@link JobParam}
     */

    public static List<JobParam> createJobParams(List<PlatformServiceDescriptor.Parameter> serviceDescriptorParameters,
                                                 JobInputs jobInputs) {

        List<JobParam> jobParams = new ArrayList<>();
        Multimap<String, String> jobParameters = jobInputs.getValuesMap();
        getCollectionJobParam(jobParameters).ifPresent(jobParams::add);

        for (String jobInputId : jobParameters.keySet()) {
            JobInput jobInput = jobInputs.getInputs().get(jobInputId);

            findServiceDescriptorParameterById(jobInputId, serviceDescriptorParameters)
                    .ifPresent(descriptorParam -> jobParams.add(
                            GrpcUtil.createJobParam(
                                    jobInputId,
                                    getParamValues(jobInputId, jobInput, jobParameters),
                                    getParamType(descriptorParam),
                                    getInputBinding(descriptorParam),
                                    getSubsetting(descriptorParam, jobParameters))
                    ));
        }

        mapInputJobParam(jobParams, jobInputs, serviceDescriptorParameters).ifPresent(jobParams::add);

        return jobParams;
    }

    /**
     * Creates a list of gRPC jobParams representing outputs based on a list of parameters taken from a service descriptor.
     * An element will appear on the list if and only if it has a field outputBinding set.
     *
     * @param serviceDescriptorOutputs output parameters coming from a service descriptor.
     * @return the list of jobParam
     */
    public static List<JobParam> createOutputJobParams(List<PlatformServiceDescriptor.Parameter> serviceDescriptorOutputs) {
        return serviceDescriptorOutputs.stream()
                .filter(output -> output.getOutputBinding() != null)
                .map(output -> GrpcUtil.createOutputJobParam(output.getId(),
                        getParamTypeFromMetadata(output.getPlatformMetadata(), output.getDefaultAttrs()),
                        createOutputBinding(output.getOutputBinding())))
                .collect(Collectors.toList());
    }

    private static Optional<JobParam> getCollectionJobParam(Multimap<String, String> jobConfigParams) {

        Collection<String> jobOutputCollection = jobConfigParams.get(JOB_PARAM_NAME_COLLECTION);
        return jobOutputCollection.isEmpty()
                ?Optional.empty()
                :Optional.of(GrpcUtil.createJobParam(JOB_PARAM_NAME_COLLECTION, jobOutputCollection, JOB_PARAM_TYPE_OTHER));
    }

    private static Optional<PlatformServiceDescriptor.Parameter> findServiceDescriptorParameterById(
            String parameterId, List<PlatformServiceDescriptor.Parameter> serviceParameters) {

        if (serviceParameters == null) {
            return Optional.empty();
        }

        return serviceParameters.stream().filter(
                parameter -> parameter.getId().equals(parameterId)
        ).findFirst();
    }

    private static Optional<JobParam> mapInputJobParam(List<JobParam> jobParams, JobInputs jobInputs, List<PlatformServiceDescriptor.Parameter> serviceDescriptorParams) {

        Multimap<String, String> jobConfigParams = jobInputs.getValuesMap();
        JobInput jobInput = jobInputs.getInputs().get(JOB_PARAM_NAME_INPUT);

        if (containsInputJobParam(jobParams) || jobInput == null) {
            return Optional.empty();
        }
        if (isStacInput(jobInput)) {
            return Optional.of(GrpcUtil.createJobParam(JOB_PARAM_NAME_INPUT, getParamValues(JOB_PARAM_NAME_INPUT, jobInput, jobConfigParams), JOB_PARAM_TYPE_STAC));
        }

        PlatformServiceDescriptor.Parameter descriptorParam = findServiceDescriptorParameterById(PARALLEL_INPUTS_LEGACY_KEY, serviceDescriptorParams)
                .orElse(null);

        return Optional.of(GrpcUtil.createJobParam(
                JOB_PARAM_NAME_INPUT,
                getParamValues(JOB_PARAM_NAME_INPUT, jobInput, jobConfigParams),
                getParamType(descriptorParam),
                getInputBinding(descriptorParam),
                getSubsetting(descriptorParam, jobConfigParams)
        ));
    }

    private static String getParamType(PlatformServiceDescriptor.Parameter descriptorParam) {
        if (descriptorParam == null || descriptorParam.getPlatformMetadata() == null) {
            return JOB_PARAM_TYPE_URL;
        }
        return getParamTypeFromMetadata(descriptorParam.getPlatformMetadata(), descriptorParam.getDefaultAttrs());
    }

    private static String getParamTypeFromMetadata(Map<String, String> platformMetadata, Map<String, String> defaultAttrs) {
        if (isStacType(platformMetadata)) {
            return JOB_PARAM_TYPE_STAC;
        }
        if (isCatalogueFormat(platformMetadata) || isDownloadable(platformMetadata, defaultAttrs)) {
            return JOB_PARAM_TYPE_URL;
        }
        return JOB_PARAM_TYPE_OTHER;
    }

    private static Collection<String> getParamValues(String jobInputId, JobInput jobInput, Multimap<String, String> jobParameters) {
        return isStacInput(jobInput)
                ? jobInput.getInternalReferences()
                : jobParameters.get(jobInputId);
    }

    private static InputBinding getInputBinding(PlatformServiceDescriptor.Parameter descriptorParam) {
        if (descriptorParam == null || descriptorParam.getInputBinding() == null) {
            return null;
        }
        return createInputBinding(descriptorParam.getInputBinding());
    }

    private static Subsetting getSubsetting(PlatformServiceDescriptor.Parameter descriptorParam, Multimap<String, String> jobConfigParams) {
        if (descriptorParam == null || descriptorParam.getSubsetting() == null) {
            return null;
        }
        return findJobConfigParameterById(descriptorParam.getSubsetting().getAoiInputRef(), jobConfigParams)
                .map(aoiInputId -> createSubsetting(aoiInputId, jobConfigParams, descriptorParam.getSubsetting().getFormat()))
                .orElse(null);
    }

    private static Optional<String> findJobConfigParameterById(String id, Multimap<String, String> jobConfigParams) {
        return jobConfigParams.keySet().stream()
                .filter(key -> key.equals(id))
                .findFirst();
    }

    private static Subsetting createSubsetting(String aoiInputId, Multimap<String, String> jobConfigParams, String format) {
        List<String> aoiInputValues = new ArrayList<>(jobConfigParams.get(aoiInputId));
        return Subsetting.newBuilder().setAoi(aoiInputValues.get(0)).setFormat(format).build();
    }

    private static boolean isStacInput(JobInput jobInput) {
        return JobInput.Type.STAC.equals(jobInput.getType());
    }

    private static boolean containsInputJobParam(List<JobParam> jobParams){
        return jobParams.stream().anyMatch(jobParam -> JOB_PARAM_NAME_INPUT.equals(jobParam.getParamName()));
    }

    private static InputBinding createInputBinding(PlatformServiceDescriptor.InputBinding inputBinding) {
        InputBinding.Builder inputBindingBuilder = InputBinding.newBuilder().setPosition(inputBinding.getPosition());
        String prefix = inputBinding.getPrefix();
        if (prefix != null) {
            inputBindingBuilder.setPrefix(prefix);
        }
        return inputBindingBuilder.build();
    }

    private static OutputBinding createOutputBinding(PlatformServiceDescriptor.OutputBinding outputBinding) {
        return OutputBinding.newBuilder().setGlob(outputBinding.getGlob()).build();
    }

    private static boolean isDownloadable(Map<String, String> platformMetadata, Map<String, String> defaultAttrs) {
        return JOB_PARAM_VALUE_FALSE.equals(platformMetadata.getOrDefault(PREVENT_URL_DOWNLOAD, JOB_PARAM_VALUE_TRUE)) ||
                isUrl(platformMetadata, defaultAttrs);
    }

    private static boolean isUrl(Map<String, String> platformMetadata, Map<String, String> defaultAttrs) {
        if (platformMetadata.isEmpty()) {
            return true;
        }
        return "string".equals(defaultAttrs.get("dataType")) &&
                !defaultAttrs.containsKey("allowedValues") &&
                "OTHER".equals(platformMetadata.get(JOB_PARAM_FORMAT_KEY)) &&
                JOB_PARAM_VALUE_FALSE.equals(platformMetadata.getOrDefault(PREVENT_URL_DOWNLOAD, JOB_PARAM_VALUE_FALSE));
    }

    private static boolean isStacType(Map<String, String> platformMetadata) {
        return JOB_PARAM_TYPE_STAC.equalsIgnoreCase(platformMetadata.get(JOB_PARAM_TYPE_KEY));
    }

    private static boolean isCatalogueFormat(Map<String, String> platformMetadata) {
        return
                "CATALOGUE".equals(platformMetadata.get(JOB_PARAM_FORMAT_KEY)) &&
                JOB_PARAM_VALUE_FALSE.equals(platformMetadata.getOrDefault(PREVENT_URL_DOWNLOAD, JOB_PARAM_VALUE_FALSE));
    }
}
