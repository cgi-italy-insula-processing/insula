package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.utils;

import com.cgi.eoss.cwl_v1_2.*;
import com.cgi.eoss.cwl_v1_2.Process;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.lastIndexOf;
import static org.apache.commons.lang3.StringUtils.substring;

/**
 * Class that holds utility methods to navigate CWL-related objects.
 */
public class CwlParsingUtility {

    /**
     * Gets the first Workflow object in the given CWL.
     * @param cwl the input CWL.
     * @return the first Workflow object, if present.
     */
    public static Optional<Workflow> getFirstWorkflow(List<Process> cwl) {
        return cwl.stream().filter(process -> process instanceof Workflow).findFirst()
                .map(Workflow.class::cast);
    }

    /**
     * Gets the first Command Line Tool in the given CWL.
     * @param cwl the input CWL.
     * @return the first WorkflowStep object, if present.
     */
    public static Optional<CommandLineTool> getFirstCommandLineTool(List<Process> cwl) {
        return cwl.stream().filter(process -> process instanceof CommandLineTool).findFirst()
                .map(CommandLineTool.class::cast);
    }

    /**
     * Gets the list of requirements in the given Process object.
     * @param process the Process object.
     * @return the list of requirements in the Process object if present, an empty list otherwise.
     */
    public static List<Object> getRequirements(Process process) {
        Optional<List<Object>> requirements = process.getRequirements();
        if (requirements != null && requirements.isPresent()) {
            return requirements.get();
        }
        return Collections.emptyList();
    }

    /**
     * Gets the identifier for a parameter given its URL with
     * fragment corresponding to the parameter's identifier.
     * @param parameterUrl The URL of the parameter.
     * @return the parameter's identifier.
     */
    public static String getIdentifierFromFragment(String parameterUrl) {
        String fragment = URI.create(parameterUrl).getFragment();
        return substring(fragment, lastIndexOf(fragment, "/")+1);
    }

    /**
     * Returns a textual representation of the given CWL
     * @param cwl the CWL.
     * @return a textual representation of the CWL, or default message if no Workflow is present.
     */
    public static String asString(List<Process> cwl) {
        Optional<Workflow> workflow = getFirstWorkflow(cwl);
        if (workflow.isPresent()) {
            Optional<String> workflowId = workflow.get().getId();
            return workflowId.map(CwlParsingUtility::getFileUriFromId).orElse("Missing Workflow ID");
        }
        return "Missing Workflow";
    }

    /**
     * Returns a textual representation of the given Workflow.
     * @param workflow the Workflow.
     * @return a textual representation of the Workflow, or default message if missing.
     */
    public static String asString(Workflow workflow) {
        Optional<String> workflowId = workflow.getId();
        return workflowId.map(CwlParsingUtility::getIdentifierFromFragment).orElse("Missing Workflow ID");
    }

    /**
     * Returns a textual representation of the given Workflow Step.
     * @param workflowStep the Workflow Step.
     * @return a textual representation of the Workflow Step, or default message if missing.
     */
    public static String asString(WorkflowStep workflowStep) {
        Optional<String> workflowId = workflowStep.getId();
        return workflowId.map(CwlParsingUtility::getIdentifierFromFragment).orElse("Missing Workflow Step ID");
    }

    /**
     * Returns a textual representation of the given CommandLineTool.
     * @param commandLineTool the CommandLineTool.
     * @return a textual representation of the CommandLineTool, or default message if missing.
     */
    public static String asString(CommandLineTool commandLineTool) {
        Optional<String> commandLineToolId = commandLineTool.getId();
        return commandLineToolId.map(CwlParsingUtility::getIdentifierFromFragment).orElse("Missing Command Line Tool ID");
    }

    /**
     * Checks whether the given Class is of the ArraySchema type.
     * @param typeClazz the input class.
     * @return true if it's an ArraySchema, false otherwise.
     */
    public static boolean isArrayType(Class<?> typeClazz) {
        return ArraySchema.class.isAssignableFrom(typeClazz);
    }

    /**
     * Gets the CWLType from the given ArraySchema.
     * @param arraySchema the input ArraySchema object.
     * @return the CWLType class of the given ArraySchema.
     */
    public static CWLType getTypeFromArray(ArraySchema arraySchema) {
        return (CWLType) arraySchema.getItems();
    }

    /**
     * Checks whether the given Workflow contains the ScatterFeatureRequirement.
     * @param workflow the input Workflow object.
     * @return true if the Workflow contains a ScatterFeatureRequirement, false otherwise.
     */
    public static boolean isScatter(Workflow workflow) {
        List<Object> requirements = CwlParsingUtility.getRequirements(workflow);
        return requirements.stream().anyMatch(requirement -> requirement instanceof ScatterFeatureRequirement);
    }

    /**
     * Finds the first WorkflowStepInput whose ID matches the given CommandInputParameter ID in a list of
     * WorkflowStepInput objects.
     * @param commandInputParameterId the given CommandInputParameter ID.
     * @param workflowStepInputs the list of WorkflowStepInput objects.
     * @return the matching WorkflowStepInput.
     */
    public static Optional<WorkflowStepInput> findMatchingWorkflowStepInput(String commandInputParameterId,
                                                                             List<Object> workflowStepInputs) {
        return workflowStepInputs.stream()
                .map(WorkflowStepInput.class::cast)
                .filter(in ->
                        getIdentifierFromFragment(commandInputParameterId).equals(getIdentifierFromFragment(in.getId().orElse(null))))
                .findFirst();
    }

    /**
     * Extracts and returns a stream of lists containing only {@link Directory} objects
     * from a stream of {@link InitialWorkDirRequirement} instances.
     *
     * @param initialWorkDirRequirements the input stream of {@link InitialWorkDirRequirement} objects
     * @return a stream of {@code List<Directory>} extracted from valid listings
     */

    public static Stream<List<Directory>> extractDirectoryListings(
            Stream<InitialWorkDirRequirement> initialWorkDirRequirements){
        return initialWorkDirRequirements
                .filter(initialWorkDirRequirement -> initialWorkDirRequirement.getListing() instanceof List)
                .map(initialWorkDirRequirement -> (List<?>) initialWorkDirRequirement.getListing())
                .filter(listing -> listing.stream().allMatch(item -> item instanceof Directory))
                .map(listing -> (List<Directory>) listing);
    }


    /**
     * Returns the last part of a string after the last occurrence of a specified separator character.
     *
     * @param input the string to be analyzed
     * @param separator the character used as the separator
     * @return the substring after the last occurrence of the separator,
     *          or the original string if the separator is not found or the input is {@code null} or empty
     */

    public static String getSubstringAfterLastSeparator(String input, char separator) {
        String result = input;

        if (input != null && !input.isEmpty()) {
            int lastIndex = input.lastIndexOf(separator);
            if (lastIndex != -1) {
                result = input.substring(lastIndex + 1);
            }
        }

        return result;
    }

    private static String getFileUriFromId(String id) {
        return substring(id, 0, lastIndexOf(id, "#"));
    }
}
