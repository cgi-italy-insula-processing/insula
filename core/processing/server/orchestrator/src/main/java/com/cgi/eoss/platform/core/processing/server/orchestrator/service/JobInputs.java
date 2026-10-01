package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 *<p>Class that holds the list of inputs of a Job and exposes methods to
 * retrieve and process them.</p>>
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class JobInputs {

    private static final String LEGACY_PARALLEL_INPUTS_KEY = "parallelInputs";

    private final Map<String, JobInput> inputs;

    private final String jobId;

    private final String userName;

    /**
     * <p>Constructor to create a JobInputs object from a JobConfig object and a jobId.</p>
     * @param jobId the ext id of the Job to which the inputs belong.
     * @param userName the name of the user the job belongs to.
     * @param jobConfig the JobConfig object containing the list of Parameters and Inputs.
     */
    public JobInputs(String jobId, String userName, JobConfig jobConfig) {
        this.jobId = jobId;
        this.userName = userName;
        this.inputs = buildInputs(jobConfig);
    }

    /**
     * <p>Method to get a specific input of a Job as a JobInput object.</p>
     * @param inputId the id of the input to be retrieved.
     * @return the JobInput object representing the input.
     */
    public JobInput get(String inputId) { return inputs.get(inputId); }

    /**
     * <p>Method to get all the inputs of a Job as a Multimap.</p>
     * @return a Multimap object containing all the inputs of the Job.
     */
    public Multimap<String, String> getValuesMap() {
        Multimap<String, String> valuesMap = HashMultimap.create();
        for (String inputId : inputs.keySet()) {
            JobInput jobInput = inputs.get(inputId);
            valuesMap.putAll(inputId, jobInput.getValues());
        }
        return valuesMap;
    }

    /**
     * Returns a copy of this instance with a specific input flagged as parallel and its
     * value replaced with a single parallel input value.
     * @param parallelInputsKey the parallel input's key.
     * @param parallelInputValue the parallel input's value.
     * @return a copy of an existing instance of this class with one of its inputs parallelized.
     */
    public JobInputs cloneWithParallelizedInput(String parallelInputsKey, String parallelInputValue) {
        Map<String, JobInput> newInputs = new HashMap<>(getInputs());
        JobInput currentJobInput = newInputs.get(parallelInputsKey);
        String inputKey = getParallelInputKey(parallelInputsKey);
        JobInput parallelizedJobInput = currentJobInput.toBuilder()
                .id(inputKey)
                .parallelInput(true)
                .values(Collections.singletonList(parallelInputValue))
                .build();
        updateParallelInputs(newInputs, parallelizedJobInput, inputKey, parallelInputsKey);
        return this.toBuilder().inputs(newInputs).build();
    }

    /**
     * Returns a copy of this instance with a specific input flagged as parallel and its
     * value replaced with a List of parallel input values.
     * @param parallelInputsKey the parallel input's key.
     * @param parallelInputValues the parallel input list's value.
     * @return a copy of an existing instance of this class with one of its inputs parallelized.
     */
    public JobInputs cloneWithParallelizedInputs(String parallelInputsKey, List<String> parallelInputValues) {
        Map<String, JobInput> newInputs = new HashMap<>(getInputs());
        JobInput currentJobInput = newInputs.get(parallelInputsKey);
        String inputKey = getParallelInputKey(parallelInputsKey);
        JobInput parallelizedJobInput = currentJobInput.toBuilder()
                .id(inputKey)
                .parallelInput(true)
                .values(parallelInputValues)
                .build();
        updateParallelInputs(newInputs, parallelizedJobInput, inputKey, parallelInputsKey);
        return this.toBuilder().inputs(newInputs).build();
    }

    /**
     * Returns a copy of the current instance of Job Inputs with
     * its STAC type values without fragments.
     * @return the JobInputs with fragments removed on STAC type inputs values.
     */
    public JobInputs withoutFragments() {
        Map<String, JobInput> stacJobInputs = fetchStacInputs();

        Map<String, JobInput> newJobInputs = new HashMap<>(this.getInputs());
        for (Map.Entry<String, JobInput> inputEntry: stacJobInputs.entrySet()) {
            JobInput jobInput = inputEntry.getValue();
            newJobInputs.replace(
                    inputEntry.getKey(),
                    jobInput.toBuilder().values(jobInput.getValuesWithoutFragments()).build()
            );
        }

        return this.toBuilder().inputs(newJobInputs).build();
    }

    /**
     * Checks if any of the Job Inputs is of STAC type.
     * @return true if at least one of the inputs is of STAC type, false otherwise.
     */
    public boolean containStacInput() {
        return this.getInputs().values()
                .stream().anyMatch(jobInput -> JobInput.Type.STAC.equals(jobInput.getType()));
    }

    /**
     * Returns a copy of this instance with an additional input, whose type is resolved
     * from the service descriptor of the given JobConfig. The current instance is not modified.
     * @param inputId the id (key) of the input to add.
     * @param values the values of the input to add.
     * @param jobConfig the JobConfig whose service descriptor provides the input type.
     * @return a new JobInputs instance including the added input.
     * @throws IllegalArgumentException if an input with the given id is already present.
     */
    public JobInputs cloneWithNewInput(String inputId, List<String> values, JobConfig jobConfig) {
        if (getInputs().containsKey(inputId)) {
            throw new IllegalArgumentException(
                    "Cannot add input '" + inputId + "': an input with the same id already exists");
        }
        JobInput.Type type = findParameterById(getServiceInputParameters(jobConfig), inputId)
                .map(JobInput::getTypeFromParameter)
                .orElse(JobInput.Type.OTHER);
        Map<String, JobInput> newInputs = new HashMap<>(getInputs());
        newInputs.put(inputId, buildJobInputFrom(inputId, values, type));
        return this.toBuilder().inputs(newInputs).build();
    }

    private Map<String, JobInput> fetchStacInputs() {
        return this.getInputs().entrySet().stream()
                .filter(entry -> JobInput.Type.STAC.equals(entry.getValue().getType()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, JobInput> buildInputs(JobConfig jobConfig) {
        Map<String, JobInput> jobInputs = new HashMap<>();
        Multimap<String, String> jobConfigInputs = jobConfig.getInputs();
        List<PlatformServiceDescriptor.Parameter> descriptorParameters = getServiceInputParameters(jobConfig);
        for (String inputId : jobConfigInputs.keySet()) {
            Optional<PlatformServiceDescriptor.Parameter> descriptorParameter = findParameterById(descriptorParameters, inputId);
            JobInput.Type type = JobInput.Type.OTHER;
            if (descriptorParameter.isPresent()) {
                type = JobInput.getTypeFromParameter(descriptorParameter.get());
            }
            jobInputs.put(inputId, buildJobInputFrom(inputId, new ArrayList<>(jobConfigInputs.get(inputId)), type));
        }
        return jobInputs;
    }

    private static List<PlatformServiceDescriptor.Parameter> getServiceInputParameters(JobConfig jobConfig) {
        return Optional.ofNullable(jobConfig)
                .map(JobConfig::getService)
                .map(PlatformService::getServiceDescriptor)
                .map(PlatformServiceDescriptor::getDataInputs)
                .orElse(Collections.emptyList());
    }

    private static Optional<PlatformServiceDescriptor.Parameter> findParameterById(List<PlatformServiceDescriptor.Parameter> parameters,
                                                                         String inputId) {
        return parameters.stream()
                .filter(p -> inputId.equals(p.getId()))
                .findFirst();
    }

    private static JobInput buildJobInputFrom(String id, List<String> values, JobInput.Type type) {
        return JobInput.builder()
                .id(id)
                .values(values)
                .type(type)
                .parallelInput(false)
                .build();
    }

    private static String getParallelInputKey(String parallelInputsKey) {
        return LEGACY_PARALLEL_INPUTS_KEY.equals(parallelInputsKey) ? "input" : parallelInputsKey;
    }

    private static void updateParallelInputs(Map<String, JobInput> inputs, JobInput input,
                                             String newInputKey, String parallelInputsKey) {
        if (parallelInputsKey.equals(newInputKey)) {
            inputs.replace(newInputKey, input);
            return;
        }
        inputs.remove(parallelInputsKey);
        inputs.put(newInputKey, input);
    }

}