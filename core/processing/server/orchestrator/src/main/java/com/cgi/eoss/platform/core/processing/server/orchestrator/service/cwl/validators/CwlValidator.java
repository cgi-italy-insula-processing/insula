package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.validators;

import com.cgi.eoss.cwl_v1_2.*;
import com.cgi.eoss.cwl_v1_2.Process;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.utils.CwlParsingUtility;
import com.google.common.collect.ImmutableList;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.utils.CwlParsingUtility.*;

/**
 * Class that checks whether a given CWL object is valid and can be processed on Insula.
 * A CWL is deemed to be valid if and only if:
 *  - it contains exactly one Workflow - which has exactly one WorkflowStep - and exactly one CommandLineTool;
 *  - the Workflow contains only one step, and it references the CommandLineTool in the CWL;
 *  - the CommandLineTool contains only one requirement of type DockerRequirement, and it contains a DockerPull;
 *  - All the requirements in the CommandLineTool are supported;
 *  - inputs and outputs of Workflow, WorkflowStep and CommandLineTool match.
 * If a CWL contains a ScatterFeatureRequirement, it is deemed valid if:
 *  - the ScatterMethod is 'dotproduct';
 *  - the "scatter" input matching the Workflow input is of type array;
 *  - the outputs are of type array.
 */
@Log4j2
public class CwlValidator {

    private static final List<Class<?>> SUPPORTED_COMMAND_LINE_TOOL_REQUIREMENTS = ImmutableList.of(
            DockerRequirement.class, ResourceRequirement.class, NetworkAccess.class,
            EnvVarRequirement.class, InitialWorkDirRequirement.class);

    private static final List<CWLType> SUPPORTED_OUTPUT_TYPES = ImmutableList.of(
            CWLType.FILE, CWLType.DIRECTORY);

    private static final List<ScatterMethod> SUPPORTED_SCATTER_METHODS = ImmutableList.of(ScatterMethod.DOTPRODUCT);

    /**
     * Validates the CWL provided as input.
     * @param cwl the CWL to be validated.
     * @return The {@link CwlValidationResult} object holding the results of the validation.
     */
    public static CwlValidationResult validate(List<Process> cwl) {
        if (!containsExactlyOneItemOfType(cwl, Workflow.class)) {
            return buildCwlValidationResult(false,
                    "CWL " + asString(cwl) + " has an unexpected number of Workflows: it must contain exactly one.");
        }
        if(!containsExactlyOneItemOfType(cwl, CommandLineTool.class)) {
            return buildCwlValidationResult(false,
                    "CWL " + asString(cwl) + " has an unexpected number of CommandLineTools: it must contain exactly one.");
        }
        return validateCwl(cwl);
    }

    private static boolean containsExactlyOneItemOfType(List<Process> target, Class<?> clazz) {
        return target.stream().filter(o -> clazz.isAssignableFrom(o.getClass())).count() == 1;
    }

    private static CwlValidationResult validateCwl(List<Process> cwl) {
        Workflow workflow = getFirstWorkflow(cwl).get();
        CommandLineTool commandLineTool = getFirstCommandLineTool(cwl).get();
        CwlValidationResult workflowStepValidation = validateWorkflowStep(workflow, commandLineTool);
        if (!workflowStepValidation.isValid()) {
            return workflowStepValidation;
        }
        CwlValidationResult cltRequirementsValidation = validateCommandLineToolRequirements(commandLineTool);
        if (!cltRequirementsValidation.isValid()) {
            return cltRequirementsValidation;
        }
        WorkflowStep workflowStep = (WorkflowStep) workflow.getSteps().get(0);
        CwlValidationResult inputsAndOutputsValidation = validateInputsAndOutputs(workflow, workflowStep, commandLineTool);
        if (!inputsAndOutputsValidation.isValid()) {
            return inputsAndOutputsValidation;
        }
        LOG.info("CWL {} validated successfully.", asString(cwl));
        return buildCwlValidationResult(true, null);
    }

    private static CwlValidationResult validateWorkflowStep(Workflow workflow, CommandLineTool commandLineTool) {
        int numberOfSteps = workflow.getSteps().size();
        if (numberOfSteps != 1) {
            return buildCwlValidationResult(false, "CWL Workflow " + CwlParsingUtility.asString(workflow) +
                    " is not valid as it contains " + numberOfSteps + " steps: it must contain exactly 1.");
        }
        Optional<String> optionalCommandLineToolId = commandLineTool.getId();
        if (!optionalCommandLineToolId.isPresent()) {
            return buildCwlValidationResult(false, "Missing ID in CommandLineTool.");
        }
        WorkflowStep workflowStep = (WorkflowStep) workflow.getSteps().get(0);
        String commandLineToolId = optionalCommandLineToolId.get();
        if (!workflowStepReferencesCommandLineTool(commandLineToolId, workflowStep)) {
            return buildCwlValidationResult(false, "WorkflowStep " + CwlParsingUtility.asString(workflowStep) +
                    " does not reference CommandLineTool " + getIdentifierFromFragment(commandLineToolId));
        }
        if (isScatter(workflow)) {
            return validateScatterStep(workflowStep, workflow.getInputs(), commandLineTool.getInputs(), workflow.getOutputs());
        }
        return buildCwlValidationResult(true, null);
    }

    private static boolean workflowStepReferencesCommandLineTool(String commandLineToolId, WorkflowStep workflowStep) {
        return getIdentifierFromFragment(commandLineToolId).equals(getIdentifierFromFragment((String) workflowStep.getRun()));
    }

    private static CwlValidationResult validateScatterStep(WorkflowStep workflowStep, List<Object> workflowInputs,
                                                           List<Object> commandLineToolInputs, List<Object> workflowOutputs) {
        CwlValidationResult scatterInputValidation = validateScatterInput((String) workflowStep.getScatter(),
                workflowStep.getIn(), workflowInputs, commandLineToolInputs);
        if (!scatterInputValidation.isValid()) {
            return scatterInputValidation;
        }
        CwlValidationResult scatterMethodValidation = validateScatterMethod(workflowStep);
        if (!scatterMethodValidation.isValid()) {
            return scatterMethodValidation;
        }
        boolean hasArrayOutput = workflowOutputs.stream()
                .allMatch(output -> isArrayType(((WorkflowOutputParameter) output).getType().getClass()));
        if (!hasArrayOutput) {
            return buildCwlValidationResult(false, "Workflow has no array output defined.");
        }
        return buildCwlValidationResult(true, null);
    }

    private static CwlValidationResult validateScatterInput(String scatterInputId,
                                                            List<Object> workflowStepInputs,
                                                            List<Object> workflowInputs, List<Object> commandLineToolInputs) {
        Optional<WorkflowStepInput> matchingScatterStepInput = findMatchingWorkflowStepInput(scatterInputId, workflowStepInputs);
        if (!matchingScatterStepInput.isPresent()) {
            return buildCwlValidationResult(false, "Scatter input " +
                    getIdentifierFromFragment(scatterInputId) +
                    " defined in WorkflowStep not found in WorkflowStep inputs.");
        }
        Optional<WorkflowInputParameter> matchingWorkflowScatterInput = findMatchingWorkflowInput(
                (String) matchingScatterStepInput.get().getSource(), workflowInputs);
        if (!matchingWorkflowScatterInput.isPresent()) {
            return buildCwlValidationResult(false, "Scatter input " +
                    getIdentifierFromFragment(scatterInputId) +
                    " defined in WorkflowStep not found in Workflow inputs.");
        }
        if (!isArrayType(matchingWorkflowScatterInput.get().getType().getClass())) {
            return buildCwlValidationResult(false, "Scatter input " +
                    getIdentifierFromFragment(scatterInputId) +
                    " defined in WorkflowStep is not of type array.");
        }
        Optional<CommandInputParameter> matchingCommandScatterInput = findMatchingCommandInputParameter(
                scatterInputId, commandLineToolInputs);
        if (!matchingCommandScatterInput.isPresent()) {
            return buildCwlValidationResult(false, "Scatter input " +
                    getIdentifierFromFragment(scatterInputId) +
                    " defined in WorkflowStep not found in CommandLineTool inputs.");
        }
        return buildCwlValidationResult(true, null);
    }

    private static CwlValidationResult validateScatterMethod(WorkflowStep workflowStep) {
        Optional<ScatterMethod> optionalScatterMethod = workflowStep.getScatterMethod();
        if (optionalScatterMethod == null || !optionalScatterMethod.isPresent()) {
            return buildCwlValidationResult(false, "No ScatterMethod defined in WorkflowStep " +
                    asString(workflowStep));
        }
        ScatterMethod scatterMethod = optionalScatterMethod.get();
        if (!SUPPORTED_SCATTER_METHODS.contains(scatterMethod)) {
            return buildCwlValidationResult(false, "Unsupported ScatterMethod " + scatterMethod +
                    " defined in WorkflowStep " + asString(workflowStep));
        }
        return buildCwlValidationResult(true, null);
    }

    private static CwlValidationResult validateCommandLineToolRequirements(CommandLineTool commandLineTool) {
       List<Object> requirements = getRequirements(commandLineTool);
        boolean areSupportedRequirements = requirements.stream()
                .map(ProcessRequirement.class::cast).allMatch(CwlValidator::isSupportedCommandLineToolRequirement);
        if (!areSupportedRequirements) {
            return buildCwlValidationResult(false,
                    "CommandLineTool " + CwlParsingUtility.asString(commandLineTool) +
                            " contains unsupported requirements.");
        }

        for(Directory directory: getInitialWorkDirRequirementDirectories(requirements)){
            CwlValidationResult cwlValidationResult = validateInitialWorkDirRequirementDirectories(commandLineTool, directory);
            if (!cwlValidationResult.isValid()){
                return cwlValidationResult;
            }
        }

        List<DockerRequirement> dockerRequirements = getDockerRequirements(requirements);
        long numberOfDockerRequirements = dockerRequirements.size();
        if (numberOfDockerRequirements != 1) {
            return buildCwlValidationResult(false,
                    "Found " + numberOfDockerRequirements + " DockerRequirements in CommandLineTool " +
                            CwlParsingUtility.asString(commandLineTool) + ": it must contain exactly one.");
        }
        Optional<String> dockerPull = dockerRequirements.get(0).getDockerPull();
        if (dockerPull == null || !dockerPull.isPresent()) {
            return buildCwlValidationResult(false, "DockerRequirement in CommandLineTool " +
                    CwlParsingUtility.asString(commandLineTool) + " does not contain a docker pull.");
        }
        return buildCwlValidationResult(true, null);
    }

    private static CwlValidationResult validateInitialWorkDirRequirementDirectories(
            CommandLineTool commandLineTool, Directory directory) {

        Optional<String> basename = directory.getBasename();
        Optional<String> location = directory.getLocation();

        CwlValidationResult validationResult = buildCwlValidationResult(true, null);

        if (basename == null || location == null) {
            validationResult = buildCwlValidationResult(false,
                    "InitialWorkDirRequirement Directory in CommandLineTool " +
                    CwlParsingUtility.asString(commandLineTool) +
                    " must have a defined Basename and Location and not null");
        } else if (!basename.isPresent() || !location.isPresent()) {
            validationResult = buildCwlValidationResult(false,
                    "InitialWorkDirRequirement Directory in CommandLineTool " +
                    CwlParsingUtility.asString(commandLineTool) +
                    " must have a defined Basename and Location");
        } else if (basename.get().startsWith("/")) {
            validationResult = buildCwlValidationResult(false,
                    "InitialWorkDirRequirement Directory Basename in CommandLineTool " +
                    CwlParsingUtility.asString(commandLineTool) +
                    " can't start with '/' ");
        }

        return validationResult;

    }


    private static boolean isSupportedCommandLineToolRequirement(ProcessRequirement requirement) {
        return SUPPORTED_COMMAND_LINE_TOOL_REQUIREMENTS.stream().anyMatch(clazz ->
                clazz.isAssignableFrom(requirement.getClass()));
    }

    private static List<Directory> getInitialWorkDirRequirementDirectories(List<Object> requirements) {
        List<Directory> directories = new ArrayList<>();

        CwlParsingUtility.extractDirectoryListings(
                requirements.stream().filter(InitialWorkDirRequirement.class::isInstance)
                        .map(requirement -> (InitialWorkDirRequirement) requirement)
        ).forEach(directories::addAll);

        return directories;
    }

    private static List<DockerRequirement> getDockerRequirements(List<Object> requirements) {
        return requirements.stream().filter(DockerRequirement.class::isInstance)
                .map(DockerRequirement.class::cast)
                .collect(Collectors.toList());
    }

    private static CwlValidationResult validateInputsAndOutputs(Workflow workflow, WorkflowStep workflowStep,
                                                                CommandLineTool commandLineTool) {
        CwlValidationResult inputsValidation =
                inputsMatch(workflow.getInputs(), workflowStep.getIn(), commandLineTool.getInputs());
        if (!inputsValidation.isValid()) {
            return inputsValidation;
        }
        CwlValidationResult outputsValidation = outputsMatch(workflow.getOutputs(), workflowStep.getOut(),
                commandLineTool.getOutputs(), isScatter(workflow));
        if (!outputsValidation.isValid()) {
            return outputsValidation;
        }
        return buildCwlValidationResult(true, null);
    }

    private static CwlValidationResult inputsMatch(List<Object> workflowInputs, List<Object> workflowStepInputs, List<Object> commandLineToolInputs) {
        for (Object commandInputParameter : commandLineToolInputs) {
            Optional<String> commandInputParameterId = ((CommandInputParameter) commandInputParameter).getId();
            if (!commandInputParameterId.isPresent()) {
                return buildCwlValidationResult(false, "CommandLineTool input parameter has no ID.");
            }
            Optional<WorkflowStepInput> stepInput = findMatchingWorkflowStepInput(commandInputParameterId.get(), workflowStepInputs);
            if (!stepInput.isPresent()) {
                return buildCwlValidationResult(false, "WorkflowStep and CommandLineTool inputs do not match.");
            }
            String stepSource = (String) stepInput.get().getSource();
            Optional<WorkflowInputParameter> workflowInput = findMatchingWorkflowInput(stepSource, workflowInputs);
            if (!workflowInput.isPresent()) {
                return buildCwlValidationResult(false, "WorkflowStep and Workflow inputs do not match.");
            }
        }
        return buildCwlValidationResult(true, null);
    }

    private static CwlValidationResult outputsMatch(List<Object> workflowOutputs, List<Object> workflowStepOutputs,
                                                    List<Object> commandLineToolOutputs, boolean isScatter) {
        for (Object cmdOutputParameter : commandLineToolOutputs) {
            CommandOutputParameter commandOutputParameter = (CommandOutputParameter) cmdOutputParameter;
            Optional<String> commandOutputParameterId = (commandOutputParameter).getId();
            if (!commandOutputParameterId.isPresent()) {
                return buildCwlValidationResult(false, "CommandLineTool output parameter has no ID.");
            }
            Optional<String> workflowStepOutput = findMatchingWorkflowStepOutput(commandOutputParameterId.get(), workflowStepOutputs);
            if (!workflowStepOutput.isPresent()) {
                return buildCwlValidationResult(false, "WorkflowStep and CommandLineTool outputs do not match.");
            }
            Optional<WorkflowOutputParameter> optionalWorkflowOutputParameter = findMatchingWorkflowOutput(workflowStepOutput.get(), workflowOutputs);
            if (!optionalWorkflowOutputParameter.isPresent()) {
                return buildCwlValidationResult(false, "WorkflowStep and Workflow outputs do not match.");
            }
            WorkflowOutputParameter workflowOutputParameter = optionalWorkflowOutputParameter.get();
            CWLType commandOutputParameterType = (CWLType) commandOutputParameter.getType();
            boolean isSameOutputType = isScatter ?
                    isSameOutputType(commandOutputParameterType, (ArraySchema) workflowOutputParameter.getType()) :
                    isSameOutputType(commandOutputParameterType, (CWLType) workflowOutputParameter.getType());
            if (!(isSameOutputType && isSupportedOutputType(commandOutputParameterType))) {
                return buildCwlValidationResult(false, "Unsupported output type in CommandLineTool and Workflow.");
            }
        }
        return buildCwlValidationResult(true, null);
    }

    private static Optional<WorkflowInputParameter> findMatchingWorkflowInput(String stepSource,
                                                                              List<Object> workflowInputs) {
        return workflowInputs.stream()
                .map(WorkflowInputParameter.class::cast)
                .filter(in -> stepSource.equals(in.getId().orElse(null)))
                .findFirst();
    }

    private static Optional<CommandInputParameter> findMatchingCommandInputParameter(String commandInputId,
                                                                                     List<Object> commandLineToolInputs) {
        return commandLineToolInputs.stream()
                .map(CommandInputParameter.class::cast)
                .filter(in -> getIdentifierFromFragment(commandInputId)
                        .equals(getIdentifierFromFragment(in.getId().orElse(null))))
                .findFirst();
    }

    private static Optional<String> findMatchingWorkflowStepOutput(String commandOutputParameterId, List<Object> workflowStepOutputs) {
        return workflowStepOutputs.stream()
                .map(String.class::cast)
                .filter(out ->
                        getIdentifierFromFragment(commandOutputParameterId).equals((getIdentifierFromFragment(out))))
                .findFirst();
    }

    private static Optional<WorkflowOutputParameter> findMatchingWorkflowOutput(String workflowStepOutput, List<Object> workflowOutputs) {
        return workflowOutputs.stream()
                .map(WorkflowOutputParameter.class::cast)
                .filter(out -> matchesWorkflowStepOutput(out.getOutputSource(), workflowStepOutput))
                .findFirst();
    }

    private static boolean isSupportedOutputType(CWLType type) {
        return SUPPORTED_OUTPUT_TYPES.contains(type);
    }

    private static boolean isSameOutputType(CWLType commandOutputParameterType,
                                            CWLType workflowOutputParameterType) {
        return commandOutputParameterType.equals(workflowOutputParameterType);
    }

    private static boolean isSameOutputType(CWLType commandOutputParameterType,
                                            ArraySchema workflowOutputParameterType) {
        return commandOutputParameterType.equals(getTypeFromArray(workflowOutputParameterType));
    }

    private static boolean matchesWorkflowStepOutput(Object outputSource, String workflowStepOutput) {
        if (String.class.equals(outputSource.getClass())) {
            return workflowStepOutput.equals(outputSource);
        }
        return ((List<String>) outputSource).contains(workflowStepOutput);
    }

    private static CwlValidationResult buildCwlValidationResult(boolean isValid, String validationMessage) {
        if (!isValid) {
            LOG.error(validationMessage);
        }
        return CwlValidationResult.builder()
                .isValid(isValid)
                .validationMessage(validationMessage)
                .build();
    }
}
