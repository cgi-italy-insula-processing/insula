package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.mappers;

import com.cgi.eoss.cwl_v1_2.*;
import com.cgi.eoss.cwl_v1_2.Process;
import com.cgi.eoss.cwl_v1_2.utils.YamlUtils;
import com.cgi.eoss.cwl_v1_2.WorkflowInputParameter;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.Cwl;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDockerBuildInfo;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.utils.CwlParsingUtility;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.utils.CwlParsingUtility.*;

/**
 * Class that holds the logic to map a given CWL to the {@link PlatformService} object.
 */
@Log4j2
public class CwlToPlatformServiceMapper {

    private static final String DATA_TYPE = "dataType";

    private static final String OTHER = "OTHER";

    private static final String FORMAT = "format";

    private static final String PREVENT_URL_DOWNLOAD = "preventUrlDownload";

    private static final String STRING_DATA_TYPE = "string";

    private static final int MIB_PER_GIB = 1024;

    /**
     * Maps the given CWL object attributes into a {@link PlatformService} object.
     *
     * @param cwl the input CWL to be mapped.
     * @return the PlatformService object with attributes mapped from CWL.
     */
    public static PlatformService toPlatformService(List<Process> cwl) {
        PlatformService platformService = initializePlatformService();

        Workflow workflow = getFirstWorkflow(cwl).orElseThrow(() ->
                new IllegalStateException("No Workflow found in CWL list"));
        LOG.info("Workflow retrieved: {}", workflow.getId());

        mapWorkflowAttributes(workflow, platformService);

        CommandLineTool clt = getFirstCommandLineTool(cwl).orElseThrow(() ->
                new IllegalStateException("No CommandLineTool found in CWL list"));

        mapCommandLineToolAttributes(clt, platformService);
        applyScatterInputOccurrences(workflow, platformService.getServiceDescriptor());
        setDockerBuildInfoWithCompletedStatus(platformService);

        LOG.info("Successful mapping of CWL into PlatformService with name {}", platformService.getName());

        return platformService;
    }

    /**
     * Extracts a list of {@link UserMount} objects from the first {@link CommandLineTool}
     * found in the given CWL process list.
     *
     * @param cwl the list of CWL {@link Process} objects, expected to contain at least one {@link CommandLineTool}
     * @return a list of {@link UserMount} objects derived from the initial work directory requirements
     */

    public static List<UserMount> mapInitialWorkDirRequirementDirectoryToUserMounts(List<Process> cwl) {
        List<Object> requirements = getRequirements(getFirstCommandLineTool(cwl).get());
        List<UserMount> userMounts = new ArrayList<>();
        mapInitialWorkDirRequirements(requirements.stream()
                .filter(InitialWorkDirRequirement.class::isInstance)
                .map(processRequirement -> (InitialWorkDirRequirement) processRequirement), userMounts);
        return userMounts;
    }

    /**
     * Maps extra-schema properties coming from a CWL into the {@link PlatformService} object.
     *
     * @param cwlAsString     the CWL as a string.
     * @param cwlUrl          the CWL URL.
     * @param platformService the PlatformService object to which the extra-schema properties will be mapped.
     */
    public static void mapExtraSchemaAttributes(String cwlAsString, URI cwlUrl, PlatformService platformService) {
        PlatformServiceDescriptor serviceDescriptor = platformService.getServiceDescriptor();
        Map<String, Object> cwlAsMap = YamlUtils.mapFromString(cwlAsString);
        serviceDescriptor.setVersion((String) cwlAsMap.getOrDefault("s:softwareVersion", "N/A"));
        platformService.setCwl(new Cwl(cwlUrl, cwlAsString));
    }

    private static void mapInitialWorkDirRequirements(
            Stream<InitialWorkDirRequirement> initialWorkDirRequirements, List<UserMount> userMounts) {

        CwlParsingUtility.extractDirectoryListings(initialWorkDirRequirements)
                .forEach(
                        directories -> userMounts.addAll(mapListingToUserMount(directories))
                );
    }

    private static List<UserMount> mapListingToUserMount(List<Directory> directories) {
        List<UserMount> userMounts = new ArrayList<>();
        for (Directory directory : directories) {
            UserMount userMount = new UserMount();
            userMount.setName(
                    CwlParsingUtility.getSubstringAfterLastSeparator(directory.getLocation().get(), '/')
            );
            userMount.setMountPath(directory.getBasename().get());
            userMounts.add(userMount);
        }
        return userMounts;
    }

    private static PlatformService initializePlatformService() {
        PlatformService platformService = new PlatformService();
        platformService.setServiceDescriptor(PlatformServiceDescriptor.builder()
                .dataInputs(new ArrayList<>()).dataOutputs(new ArrayList<>())
                .build());
        return platformService;
    }

    private static void mapWorkflowAttributes(Workflow workflow, PlatformService platformService) {
        PlatformServiceDescriptor serviceDescriptor = platformService.getServiceDescriptor();
        String workflowId = getIdentifierFromFragment(workflow.getId().get());
        platformService.setName(workflowId);
        serviceDescriptor.setId(workflowId);
        workflow.getLabel().ifPresent(serviceDescriptor::setTitle);
        String workflowDoc = (String) workflow.getDoc();
        platformService.setDescription(workflowDoc);
        serviceDescriptor.setDescription(workflowDoc);
        if (isScatter(workflow)) {
            platformService.setType(PlatformService.Type.PARALLEL_PROCESSOR);
            mapScatterFeature(workflow, serviceDescriptor);
        } else {
            platformService.setType(PlatformService.Type.PROCESSOR);
        }
    }

    private static void mapScatterFeature(Workflow workflow, PlatformServiceDescriptor serviceDescriptor) {
        WorkflowStep workflowStep = (WorkflowStep) workflow.getSteps().get(0);
        String scatterInputId = (String) workflowStep.getScatter();
        if (scatterInputId != null) {
            WorkflowStepInput matchingScatterInput = findMatchingWorkflowStepInput(
                    scatterInputId, workflowStep.getIn()).get();
            serviceDescriptor.setParallelInputsKey(getIdentifierFromFragment(matchingScatterInput.getId().get()));
        }
    }

    private static void mapCommandLineToolAttributes(CommandLineTool commandLineTool, PlatformService platformService) {
        PlatformServiceDescriptor serviceDescriptor = platformService.getServiceDescriptor();
        List<String> arguments = buildArgumentsList(commandLineTool.getArguments());
        List<String> allCommands = mapObjectToListString(commandLineTool.getBaseCommand());
        if (!allCommands.isEmpty()) {
            serviceDescriptor.setDockerCommand(allCommands.remove(0));
            arguments.addAll(0, allCommands);
        }
        serviceDescriptor.setDockerArguments(arguments);
        mapCommandLineToolRequirements(getRequirements(commandLineTool), platformService);
        mapCommandLineToolInputs(commandLineTool.getInputs(), serviceDescriptor.getDataInputs());
        mapCommandLineOutputs(commandLineTool.getOutputs(), serviceDescriptor.getDataOutputs());
    }

    private static void mapCommandLineToolRequirements(List<Object> requirements, PlatformService platformService) {
        mapResourceRequirement(requirements.stream().filter(ResourceRequirement.class::isInstance), platformService);
        mapDockerRequirement(requirements.stream().filter(DockerRequirement.class::isInstance), platformService);
        mapEnvVarRequirement(requirements.stream().filter(EnvVarRequirement.class::isInstance),
                platformService.getServiceDescriptor()
        );
    }

    private static void mapCommandLineToolInputs(
            List<Object> commandLineToolInputs, List<PlatformServiceDescriptor.Parameter> parameters) {
        for (Object commandLineToolInput : commandLineToolInputs) {
            CommandInputParameter inputParameter = (CommandInputParameter) commandLineToolInput;
            PlatformServiceDescriptor.Parameter parameter = initializeParameter();
            parameter.setId(getIdentifierFromFragment(inputParameter.getId().get()));
            Optional<String> label = inputParameter.getLabel();
            if (label != null && label.isPresent()) {
                parameter.setTitle(label.get());
            } else {
                parameter.setTitle(parameter.getId());
            }
            Optional<CommandLineBinding> inputBinding = inputParameter.getInputBinding();
            if (inputBinding != null && inputBinding.isPresent()) {
                mapInputBindingAttributes(inputBinding.get(), parameter);
            }
            parameter.setDescription((String) inputParameter.getDoc());
            String defaultValue = (String) inputParameter.getDefault();
            if (defaultValue != null) {
                parameter.getDefaultAttrs().put("value", defaultValue);
            }
            mapType(inputParameter.getType(), extractParameterFormat(inputParameter.getFormat(), inputParameter.getId().get()), parameter);
            parameters.add(parameter);
        }
    }

    private static void mapInputBindingAttributes(CommandLineBinding commandLineBinding,
                                                  PlatformServiceDescriptor.Parameter parameter) {
        PlatformServiceDescriptor.InputBinding.InputBindingBuilder inputBindingBuilder =
                PlatformServiceDescriptor.InputBinding.builder();
        Integer position = (Integer) commandLineBinding.getPosition();
        if (position != null) {
            inputBindingBuilder.position(position);
        }
        Optional<String> prefix = commandLineBinding.getPrefix();
        if (prefix != null && prefix.isPresent()) {
            inputBindingBuilder.prefix(prefix.get());
        }
        parameter.setInputBinding(inputBindingBuilder.build());
    }

    private static void mapCommandLineOutputs(
            List<Object> commandLineToolOutputs, List<PlatformServiceDescriptor.Parameter> parameters) {
        for (Object commandLineOutput : commandLineToolOutputs) {
            CommandOutputParameter outputParameter = (CommandOutputParameter) commandLineOutput;
            PlatformServiceDescriptor.Parameter parameter = initializeParameter();
            parameter.setId(getIdentifierFromFragment(outputParameter.getId().get()));
            Optional<String> label = outputParameter.getLabel();
            if (label != null && label.isPresent()) {
                parameter.setTitle(label.get());
            }
            String description = (String) outputParameter.getDoc();
            if (description != null) {
                parameter.setDescription(description);
            }
            Optional<CommandOutputBinding> outputBinding = outputParameter.getOutputBinding();
            if (outputBinding != null && outputBinding.isPresent()) {
                parameter.setOutputBinding(PlatformServiceDescriptor.OutputBinding.builder()
                        .glob((String) outputBinding.get().getGlob()).build());
            }
            mapType(outputParameter.getType(), extractParameterFormat(outputParameter.getFormat(), parameter.getId()), parameter);
            parameters.add(parameter);
        }
    }

    private static void mapType(Object type, String format, PlatformServiceDescriptor.Parameter parameter) {
        if (List.class.isAssignableFrom(type.getClass())) {
            boolean isOptionalParameter = ((List<Object>) type).stream().anyMatch(CWLType.NULL::equals);
            ((List<Object>) type).forEach(t -> executeTypeMapping(t, format, parameter, isOptionalParameter));
            return;
        }
        executeTypeMapping(type, format, parameter, false);
    }

    private static void executeTypeMapping(Object type, String format, PlatformServiceDescriptor.Parameter parameter, boolean isOptional) {
        Class typeClazz = type.getClass();
        if (CWLType.class.isAssignableFrom(typeClazz)) {
            mapCwlType((CWLType) type, format, parameter);
            setOccurrences(parameter, isOptional ? 0 : 1, 1);
        } else if (isArrayType(typeClazz)) {
            mapCwlType(getTypeFromArray((ArraySchema) type), format, parameter);
            setOccurrences(parameter, isOptional ? 0 : 1, 100);
        } else if (EnumSchema.class.isAssignableFrom(typeClazz)) {
            EnumSchema enumType = (EnumSchema) type;
            parameter.setData(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL);
            Map<String, String> defaultAttrs = parameter.getDefaultAttrs();
            defaultAttrs.put(DATA_TYPE, STRING_DATA_TYPE);
            defaultAttrs.put("allowedValues", enumType.getSymbols().stream()
                    .map(CwlParsingUtility::getIdentifierFromFragment).collect(Collectors.joining(",")));
            Map<String, String> platformMetadata = parameter.getPlatformMetadata();
            platformMetadata.put(FORMAT, OTHER);
            platformMetadata.put(PREVENT_URL_DOWNLOAD, "true");
            setOccurrences(parameter, isOptional ? 0 : 1, 1);
        }
    }

    private static void mapCwlType(CWLType type, String format, PlatformServiceDescriptor.Parameter parameter) {
        Map<String, String> defaultAttrs = parameter.getDefaultAttrs();
        Map<String, String> platformMetadata = parameter.getPlatformMetadata();
        switch (type) {
            case BOOLEAN:
                parameter.setData(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL);
                defaultAttrs.put(DATA_TYPE, "boolean");
                platformMetadata.put(FORMAT, OTHER);
                break;
            case FLOAT:
            case DOUBLE:
                parameter.setData(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL);
                defaultAttrs.put(DATA_TYPE, "double");
                platformMetadata.put(FORMAT, OTHER);
                break;
            case INT:
            case LONG:
                parameter.setData(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL);
                defaultAttrs.put(DATA_TYPE, "integer");
                platformMetadata.put(FORMAT, OTHER);
                break;
            case STRING:
                parameter.setData(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL);
                defaultAttrs.put(DATA_TYPE, STRING_DATA_TYPE);
                platformMetadata.put(PREVENT_URL_DOWNLOAD, "true");
                platformMetadata.put(FORMAT, OTHER);
                break;
            case FILE:
                parameter.setData(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL);
                defaultAttrs.put(DATA_TYPE, STRING_DATA_TYPE);
                platformMetadata.put(PREVENT_URL_DOWNLOAD, "false");
                platformMetadata.put(FORMAT, mapCwlFileFormat(format));
                break;
            case DIRECTORY:
                parameter.setData(PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL);
                defaultAttrs.put(DATA_TYPE, STRING_DATA_TYPE);
                platformMetadata.put(PREVENT_URL_DOWNLOAD, "false");
                platformMetadata.put("type", "STAC");
                platformMetadata.put(FORMAT, "CATALOGUE");
                break;
            case NULL:
            default:
                break;
        }
        parameter.setPlatformMetadata(platformMetadata);
    }

    private static String mapCwlFileFormat(String format){
        if (Objects.equals(format, "geotiff")) {
            return "GEOTIFF";
        } else if (Objects.equals(format, "shapefile")) {
            return "SHAPEFILE";
        } else {
            return OTHER;
        }
    }

    private static void setOccurrences(PlatformServiceDescriptor.Parameter parameter, int min, int max) {
        parameter.setMinOccurs(min);
        parameter.setMaxOccurs(max);
    }

    private static void mapDockerRequirement(Stream<Object> dockerRequirements, PlatformService platformService) {
        dockerRequirements
                .findFirst()
                .flatMap(requirement -> ((DockerRequirement) requirement).getDockerPull())
                .ifPresent(platformService::setDockerTag);
    }

    private static void mapResourceRequirement(Stream<Object> resourceRequirements, PlatformService platformService) {
        resourceRequirements
                .findFirst()
                .ifPresent(requirement -> platformService.setRequiredResources(PlatformServiceResources.builder()
                        .ram(((ResourceRequirement) requirement).getRamMin() + "Mi")
                        .cpus(((Integer) ((ResourceRequirement) requirement).getCoresMin()).toString())
                        .storage(mapStorage((ResourceRequirement) requirement))
                        .build()));
    }

    private static String mapStorage(ResourceRequirement requirement) {
        int totalMiB = safeGetPositiveInt(requirement.getOutdirMin()) + safeGetPositiveInt(requirement.getTmpdirMin());
        if (totalMiB == 0) {
            return null;
        }
        // Rounding up
        int storageGiB = (totalMiB + MIB_PER_GIB - 1) / MIB_PER_GIB;
        return String.valueOf(storageGiB);
    }

    private static void mapEnvVarRequirement(Stream<Object> envVarRequirements,
                                             PlatformServiceDescriptor serviceDescriptor) {
        Map<String, String> descriptorEnvs = Optional.ofNullable(serviceDescriptor.getEnvironmentVariables())
                .orElse(new HashMap<>());
        Optional<EnvVarRequirement> envVarRequirement = envVarRequirements
                .findFirst()
                .map(EnvVarRequirement.class::cast);
        envVarRequirement.ifPresent(requirement ->
                mapEnvironmentVariables(requirement, descriptorEnvs));
        serviceDescriptor.setEnvironmentVariables(descriptorEnvs);
    }

    private static void mapEnvironmentVariables(EnvVarRequirement envVarRequirement,
                                                Map<String, String> descriptorEnvs) {
        for (Object envDef : envVarRequirement.getEnvDef()) {
            EnvironmentDefImpl environmentVariable = (EnvironmentDefImpl) envDef;
            descriptorEnvs.put(
                    environmentVariable.getEnvName(),
                    String.valueOf(environmentVariable.getEnvValue())
            );
        }
    }

    private static PlatformServiceDescriptor.Parameter initializeParameter() {
        return PlatformServiceDescriptor.Parameter.builder().defaultAttrs(new HashMap<>())
                .platformMetadata(new HashMap<>()).build();
    }

    private static void applyScatterInputOccurrences(Workflow workflow,
                                                     PlatformServiceDescriptor serviceDescriptor) {
        if (!isScatter(workflow)) {
            return;
        }

        Optional<WorkflowStepInput> scatterStepInput = resolveScatterStepInput(workflow);
        if (!scatterStepInput.isPresent()) {
            return;
        }
        Optional<WorkflowInputParameter> workflowInputOptional =
                resolveScatterWorkflowInput(workflow, scatterStepInput.get());
        if (!workflowInputOptional.isPresent()) {
            return;
        }
        WorkflowInputParameter workflowInput = workflowInputOptional.get();

        PlatformServiceDescriptor.Parameter occurrences = initializeParameter();
        mapType(workflowInput.getType(), extractParameterFormat(workflowInput.getFormat(), workflowInput.getId().get()), occurrences);

        String parallelToolInputId = serviceDescriptor.getParallelInputsKey();

        serviceDescriptor.getDataInputs().stream()
                .filter(p -> parallelToolInputId.equals(p.getId()))
                .findFirst()
                .ifPresent(p -> setOccurrences(p, occurrences.getMinOccurs(), occurrences.getMaxOccurs()));
    }

    private static Optional<WorkflowStepInput> resolveScatterStepInput(Workflow workflow) {
        WorkflowStep workflowStep = (WorkflowStep) workflow.getSteps().get(0);
        if (!(workflowStep.getScatter() instanceof String)) {
            return Optional.empty();
        }
        return findMatchingWorkflowStepInput((String) workflowStep.getScatter(), workflowStep.getIn());
    }

    private static Optional<WorkflowInputParameter> resolveScatterWorkflowInput(Workflow workflow,
                                                                                WorkflowStepInput scatterStepInput) {
        if (!(scatterStepInput.getSource() instanceof String)) {
            return Optional.empty();
        }
        return findMatchingWorkflowInput((String) scatterStepInput.getSource(), workflow.getInputs());
    }


    private static Optional<WorkflowInputParameter> findMatchingWorkflowInput(String stepSource, List<Object> workflowInputs) {
        if (workflowInputs == null) {
            return Optional.empty();
        }
        return workflowInputs.stream()
                .filter(WorkflowInputParameter.class::isInstance)
                .map(WorkflowInputParameter.class::cast)
                .filter(in -> stepSource.equals(in.getId().orElse(null)))
                .findFirst();
    }


    private static void setDockerBuildInfoWithCompletedStatus(PlatformService platformService) {
        PlatformServiceDockerBuildInfo dockerBuildInfo = new PlatformServiceDockerBuildInfo();
        dockerBuildInfo.setDockerBuildStatus(PlatformServiceDockerBuildInfo.Status.COMPLETED);
        platformService.setDockerBuildInfo(dockerBuildInfo);
    }

    private static List<String> buildArgumentsList(Optional<List<Object>> arguments) {
        List<String> stringList = new ArrayList<>();
        if (arguments == null) {
            return stringList;
        }
        arguments.ifPresent(list -> list.forEach(v -> stringList.add(String.valueOf(v))));
        return stringList;
    }

    private static List<String> mapObjectToListString(Object baseCommand) {
        List<String> baseCommandArguments = new ArrayList<>();
        if (baseCommand instanceof List) {
            baseCommandArguments.addAll(mapObjectListToStringList((List<Object>) baseCommand));
        } else if (baseCommand != null) {
            baseCommandArguments.add(String.valueOf(baseCommand));
        }
        return baseCommandArguments;
    }

    private static List<String> mapObjectListToStringList(List<Object> baseCommandAndArguments) {
        if (baseCommandAndArguments.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> baseCommandTail = new ArrayList<>();
        baseCommandAndArguments.forEach(o ->
                baseCommandTail.add(String.valueOf(o))
        );
        return baseCommandTail;
    }

    private static int safeGetPositiveInt(Object value) {
        return (value instanceof Integer && (Integer) value >= 0) ? (Integer) value : 0;
    }

    /**
     * @param formatobj can only be type 'String' or 'null', if not null it's a string similar to: .../workflow#main/parameter_id/format
     * @param id the parameter_id
     * @return the extracted format string
     * @throws IllegalStateException if the format string does not contain the parameter id as expected
     */
    private static String extractParameterFormat(Object formatobj, String id){
        if (formatobj == null) {
            return null;
        }

        String marker = id + "/";
        String format = (String) formatobj;
        if (format.contains(marker)) {
            return format.substring(format.lastIndexOf(marker) + marker.length()).trim().toLowerCase();
        } else {
            throw new IllegalStateException("Unexpected format string that does not contain parameter id.");
        }
    }

}