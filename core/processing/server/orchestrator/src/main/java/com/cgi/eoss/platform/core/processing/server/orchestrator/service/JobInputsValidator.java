package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.orchestrator.model.JobValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates related job inputs **/
@Slf4j
@AllArgsConstructor
public class JobInputsValidator implements JobValidationResultProducer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<String> alwaysAllowedValues;

    @Override
    public JobValidationResult validate(Job job, JobInputs jobInputs) {

        LOG.info("Beginning job inputs validation for job: {}", job.getExtId());

        List<String> validationOutputs = new ArrayList<>();

        getMissingInputIds(job, jobInputs).ifPresent(validationOutputs::add);
        getCardinalityUnmatchedInputsIds(job, jobInputs).ifPresent(validationOutputs::add);
        getUnmatchingInputIds(job, jobInputs).ifPresent(validationOutputs::add);

        boolean areValidJobInputs = validationOutputs.isEmpty();
        return  buildValidationResult(
                job,
                jobInputs,
                areValidJobInputs,
                areValidJobInputs ? null : validationOutputs.get(0)
        );

    }

    private Optional<String> getMissingInputIds(Job job, JobInputs jobInputs) {

        LOG.info("Searching for missing inputs for job: {}", job.getExtId());
        Multimap<String,String> jobInputsMultimap = jobInputs.getValuesMap();

        if (jobInputsMultimap == null){
            return Optional.empty();
        }

        PlatformServiceDescriptor platformServiceDescriptor = job.getConfig().getService().getServiceDescriptor();
        Set<String> platformServiceInputsIds = new HashSet<>();
        for(PlatformServiceDescriptor.Parameter parameter : platformServiceDescriptor.getDataInputs()){
            platformServiceInputsIds.add(parameter.getId());
        }

        Set<String> jobInputsIds = new HashSet<>(jobInputsMultimap.keySet());
        jobInputsIds.removeIf(alwaysAllowedValues::contains);
        jobInputsIds.removeAll(platformServiceInputsIds);

        return jobInputsIds.isEmpty()
                ? Optional.empty()
                : Optional.of("Missing required input(s): " + String.join(",", jobInputsIds)
        );

    }

    private Optional<String> getCardinalityUnmatchedInputsIds(Job job, JobInputs explodedInputs) {

        LOG.info("Validating inputs cardinality for job: {}", job.getExtId());

        Set<String> unmatchedCardinalityInputs = new HashSet<>();
        for(PlatformServiceDescriptor.Parameter serviceInputParameter : job.getConfig().getService().getServiceDescriptor().getDataInputs()) {
            if (!isCardinalityMatchingForJobInputs(serviceInputParameter, explodedInputs)) {
                unmatchedCardinalityInputs.add(serviceInputParameter.getId());
            }
        }
        return unmatchedCardinalityInputs.isEmpty()
                ? Optional.empty()
                : Optional.of("Invalid number of inputs for input(s): " + String.join(",", unmatchedCardinalityInputs)
        );

    }

    private boolean isCardinalityMatchingForJobInputs(PlatformServiceDescriptor.Parameter serviceInputParameter, JobInputs explodedInputs) {

        String inputId = serviceInputParameter.getId();
        Multimap<String, String> jobInputsMultimap = explodedInputs.getValuesMap();
        Collection<String> jobInputsCollection = jobInputsMultimap != null
                ? jobInputsMultimap.get(inputId)
                : null;

        return areInputsRespectingCardinalityConstraints(
                inputId,
                serviceInputParameter.getPlatformMetadata() != null
                        ? serviceInputParameter.getPlatformMetadata().getOrDefault("type", "")
                        : "",
                jobInputsCollection,
                serviceInputParameter.getMinOccurs(),
                serviceInputParameter.getMaxOccurs()

        );

    }

    private boolean areInputsRespectingCardinalityConstraints(
            String serviceInputId,
            String serviceInputType,
            Collection<String> inputCollection,
            int minOccurs,
            int maxOccurs
    ) {

        int inputCollectionSize = inputCollection.size();

        return evaluateNullInput(inputCollection, minOccurs)
                && evaluateAlwaysValidInputCases(serviceInputId, serviceInputType, maxOccurs, inputCollectionSize)
                && evaluateRangeCondition(inputCollection, minOccurs, maxOccurs, inputCollectionSize);

    }

    private boolean evaluateAlwaysValidInputCases(String serviceInputId, String serviceInputType, int maxOccurs, int inputCollectionSize) {

        if (alwaysAllowedValues.contains(serviceInputId) || serviceInputType.equalsIgnoreCase("STAC")){
            return inputCollectionSize < 2;
        }

        return maxOccurs != 1 || inputCollectionSize <= 1;

    }

    private static boolean evaluateNullInput(Collection<String> inputCollection, int minOccurs) {

        if (inputCollection == null || inputCollection.isEmpty()) {
            return minOccurs == 0;
        }

        return true;

    }

    private static boolean evaluateRangeCondition(Collection<String> inputCollection, int minOccurs, int maxOccurs, int inputCollectionSize) {

        if (minOccurs > 0){
            return inputCollectionSize >= minOccurs && inputCollectionSize <= maxOccurs
                    && notBlankValuesCount(inputCollection) >= minOccurs;
        }

        return true;

    }

    private static int notBlankValuesCount(Collection<String> inputCollection) {

        int notBlankElementsCount = 0;
        for(String input : inputCollection){
            if (input != null && !input.trim().isEmpty()){
                notBlankElementsCount++;
            }
        }
        return notBlankElementsCount;

    }

    private Optional<String> getUnmatchingInputIds(Job job, JobInputs jobInputs) {

        LOG.debug("Checking input values types for job: {}", job.getExtId());

        List<String> unmatchingInputIds = job.getConfig().getService().getServiceDescriptor().getDataInputs()
                .stream()
                .filter(descriptorParameter -> !inputsMatchParameterType(jobInputs.getValuesMap().get(descriptorParameter.getId()), descriptorParameter))
                .map(PlatformServiceDescriptor.Parameter::getId)
                .collect(Collectors.toList());

        return unmatchingInputIds.isEmpty()
                ? Optional.empty()
                : Optional.of("Value does not match type for input(s): " + String.join(",",unmatchingInputIds));

    }

    private boolean inputsMatchParameterType(Collection<String> inputValues, PlatformServiceDescriptor.Parameter descriptorParameter) {

        return inputValues.stream().allMatch(input -> matchesInputType(input, descriptorParameter));

    }

    private boolean matchesInputType(String value, PlatformServiceDescriptor.Parameter input) {

        if (PlatformServiceDescriptor.Parameter.DataNodeType.LITERAL.equals(input.getData())) {
            return validateLiteralInput(value, input);
        }
        return true;

    }

    private boolean validateLiteralInput(String value, PlatformServiceDescriptor.Parameter input) {

        Map<String, String> defaultAttrs = input.getDefaultAttrs();
        String dataType = defaultAttrs.get("dataType");
        if (dataType == null) {
            LOG.error("Malformed input data: dataType attribute is not present in the platform defaultAttrs.");
            return false;
        }

        if (alwaysAllowedValues.contains(input.getId())){
            return isCollectionOrSpecInputAValidJSON(value);
        }

        return checkSpecificLiteralInput(value, input, dataType, defaultAttrs);

    }

    private static boolean checkSpecificLiteralInput(String value, PlatformServiceDescriptor.Parameter input, String dataType, Map<String, String> defaultAttrs) {

        boolean isMandatory = input.getMinOccurs() > 0;
        boolean validationResult = false;

        switch (dataType) {

            case "string": {
                validationResult = isValidString(value, input, defaultAttrs, isMandatory);
                break;
            }
            case "integer": {
                validationResult = isValidInteger(value, isMandatory);
                break;
            }
            case "double": {
                validationResult = isValidFloat(value, isMandatory);
                break;
            }
            case "boolean": {
                validationResult = isValidBoolean(value, isMandatory);
                break;
            }
            default: {
                LOG.error("Malformed input data: received invalid dataType attribute {} in the platform defaultAttrs.", dataType);
            }
        }

        return validationResult;
    }

    private static boolean isValidString(String value, PlatformServiceDescriptor.Parameter input, Map<String, String> defaultAttrs, boolean isMandatory) {
        return isMandatory ? isValidStringDataType(value, input.getPlatformMetadata(), defaultAttrs) : (isValidStringDataType(value, input.getPlatformMetadata(), defaultAttrs) || org.apache.commons.lang3.StringUtils.isBlank(value));
    }

    private static boolean isValidFloat(String value, boolean isMandatory) {
        return isMandatory ? isValidFloatDataType(value) : (isValidFloatDataType(value) || org.apache.commons.lang3.StringUtils.isBlank(value));
    }

    private static boolean isValidInteger(String value, boolean isMandatory) {
        return isMandatory ? isValidIntegerDataType(value) : (isValidIntegerDataType(value) || org.apache.commons.lang3.StringUtils.isBlank(value));
    }

    private static boolean isValidBoolean(String value, boolean isMandatory) {
        return isMandatory ? isValidBooleanDataType(value) : (isValidBooleanDataType(value) || org.apache.commons.lang3.StringUtils.isBlank(value));
    }

    private static boolean isValidStringDataType(String value, Map<String, String> platformMetadata, Map<String, String> defaultAttrs) {

        String dataFormat = platformMetadata != null
                ? platformMetadata.get("format")
                : null;
        String format = (dataFormat != null && !dataFormat.trim().isEmpty())
                ? dataFormat
                : "OTHER";

        boolean validationResult = false;

        switch (format) {
            case "DATE": {
                validationResult = isDate(value);
                break;
            }
            case "AOI": {
                validationResult = isAoi(value);
                break;
            }
            case "OTHER": {
                validationResult = checkOtherFormatStringDataType(value, platformMetadata, defaultAttrs);
                break;
            }
            case "CATALOGUE": {
                validationResult = isUri(value);
                break;
            }
            default: {
                LOG.error("Malformed string input data: received invalid format attribute {} in the platform metadata.", format);
            }

        }

        return validationResult;

    }

    private static boolean isValidIntegerDataType(String value) {
        return isInteger(value) || isLong(value);
    }

    private static boolean isValidFloatDataType(String value) {
        return isDouble(value) || isFloat(value);
    }

    private static boolean isValidBooleanDataType(String value) {
        return Boolean.TRUE.toString().equalsIgnoreCase(value) || Boolean.FALSE.toString().equalsIgnoreCase(value);
    }

    private static boolean isCollectionOrSpecInputAValidJSON(String collectionInput){

        try {

            OBJECT_MAPPER.readTree(collectionInput);

        }
        catch (Exception parsingException){

            LOG.error("JSON parsing error - JSON input could not be parsed: {}", parsingException.getMessage());
            return false;

        }

        return true;

    }

    private static boolean checkOtherFormatStringDataType(String value, Map<String, String> platformMetadata,  Map<String, String> defaultAttrs) {

        String allowedValues = defaultAttrs.get("allowedValues");

        if (allowedValues != null) {
            return  StringUtils.commaDelimitedListToSet(StringUtils.trimAllWhitespace(allowedValues)).contains(value);

        }

        if (isDownloadableString(platformMetadata)) {
            return isUri(value);
        }

        return true;
    }

    private static boolean isDownloadableString(Map<String, String> platformMetadata) {

        if(platformMetadata == null){
            return true;
        }

        return platformMetadata.get("preventUrlDownload") == null
                || !Boolean.parseBoolean(platformMetadata.get("preventUrlDownload"));

    }

    private static boolean isDate(String value) {

        try {
            DateTime.parse(value);
        } catch (IllegalArgumentException illegalArgumentException) {

            LOG.error("Date parsing error - {} could not be parsed: {}", value, illegalArgumentException.getMessage());
            return false;

        }
        return true;

    }

    private static boolean isAoi(String value) {

        try {

            new WKTReader().read(value);

        } catch (ParseException parseException) {

            LOG.error("AOI parsing error - {} could not be parsed: {}", value, parseException.getMessage());
            return false;

        }
        return true;

    }

    private static boolean isUri(String value) {

        URI convertedValue;
        try {

            convertedValue = new URI(value);

        } catch (URISyntaxException uriSyntaxException) {

            LOG.error("URI parsing error - {} could not be parsed: {}", value, uriSyntaxException.getMessage());
            return false;

        }
        return isWellFormattedUri(convertedValue);

    }

    private static boolean isWellFormattedUri(URI convertedValue) {

        return org.apache.commons.lang3.StringUtils.isNotBlank(convertedValue.getScheme())
                && (
                org.apache.commons.lang3.StringUtils.isNotBlank(convertedValue.getHost())
                        || org.apache.commons.lang3.StringUtils.isNotBlank(convertedValue.getPath())
        );

    }

    private static boolean isInteger(String value) {

        try {

            Integer.parseInt(value);

        } catch(NumberFormatException numberFormatException) {

            LOG.error("Integer parsing error - {} could not be parsed: {}", value, numberFormatException.getMessage());
            return false;

        }
        return true;

    }

    private static boolean isDouble(String value) {

        try {

            Double.parseDouble(value);

        } catch(NumberFormatException numberFormatException) {

            LOG.error("Double parsing error - {} could not be parsed: {}", value, numberFormatException.getMessage());
            return false;

        }
        return true;

    }

    private static boolean isFloat(String value) {

        try {

            Float.parseFloat(value);

        } catch(NumberFormatException numberFormatException) {

            LOG.error("Float parsing error - {} could not be parsed: {}", value, numberFormatException.getMessage());
            return false;

        }
        return true;

    }

    private static boolean isLong(String value) {

        try {

            Long.parseLong(value);

        } catch(NumberFormatException numberFormatException) {

            LOG.error("Long parsing error - {} could not be parsed: {}", value, numberFormatException.getMessage());
            return false;

        }
        return true;

    }

    private static JobValidationResult buildValidationResult(Job job, JobInputs jobInputs, boolean isValid, String errorMessage) {
        
        if (!isValid) {
            LOG.error("Job inputs validation failed: {}", errorMessage);
        }
        return new JobValidationResult(job, jobInputs, isValid, errorMessage);
        
    }

}
