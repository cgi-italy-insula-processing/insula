package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Class to validate platform services before they are saved in the database.
 *
 */
@NoArgsConstructor
public class PlatformServiceValidator {

    private static final Pattern VALID_MEMORY_PATTERN = Pattern.compile("^[0-9]+(gi|Gi|mi|Mi)$");
    private static final Pattern VALID_POSITIVE_INTEGER_PATTERN = Pattern.compile("^[0-9]+$");
    private static final int MAX_CWL_SERVICE_DESCRIPTION_LENGTH = 255;

    /**
     * Class that holds the validation result of a platform service.
     *
     */
    @Data
    public static class PlatformServiceValidationResult {
        private final PlatformService platformService;
        private final boolean isValid;
        private final String errorMessage;
    }

    /**
     * Validate the given platform service.
     *
     * @param service
     *            The service to be validated.
     * @return a PlatformServiceValidationResult object
     *            which contains the result of the validation.
     */
    public PlatformServiceValidationResult validate(PlatformService service) {
        PlatformServiceDescriptor serviceDescriptor = service.getServiceDescriptor();
        if (serviceDescriptor == null) {
            return failedValidation(null, "ServiceDescriptor cannot be null");
        }

        PlatformServiceValidationResult platformServiceValidationResult =
                validateCwlDescriptionLength(service);
        if (!platformServiceValidationResult.isValid()) {
            return platformServiceValidationResult;
        }

        platformServiceValidationResult = validatePlatformServiceResourcesSharedMemory(service);
        if (!platformServiceValidationResult.isValid()) {
            return platformServiceValidationResult;
        }

        platformServiceValidationResult = validateDescriptorInputsAndOutputs(service);
        if (!platformServiceValidationResult.isValid()) {
            return platformServiceValidationResult;
        }

        return validateRequiredResources(service);

    }


    /**
     * Check if it is allowed to update the given platform service entity.
     *
     * @param service
     *            The existing service already saved in the database.
     * @param entityToBeSaved
     *            The new service entity to be saved.
     * @return a PlatformServiceValidationResult object
     *            which contains the result of the validation.
     */
    public PlatformServiceValidationResult checkIfUpdateIsAllowed(PlatformService service, PlatformService entityToBeSaved) {


        if (!isStatusEqual(service, entityToBeSaved)) {
            return failedValidation(entityToBeSaved, "Status update from " + service.getStatus() + " to " + entityToBeSaved.getStatus() + " is forbidden for Service " + service.getId());
        }
        if (!isServiceDescriptorTypeEqual(service, entityToBeSaved)) {
            return failedValidation(entityToBeSaved, "Update is forbidden for Service " + service.getId() + ": only one of the two services (existing and updated) is defined through CWL.");
        }

        if(service.getServiceDescriptor() != null) {

            List<PlatformServiceDescriptor.Parameter> descriptorInputs = service.getServiceDescriptor().getDataInputs();
            List<PlatformServiceDescriptor.Parameter> descriptorOutputs = service.getServiceDescriptor().getDataOutputs();

            if (containsObjectsWithDuplicatedId(descriptorInputs)) {
                return failedValidation(service, "ServiceDescriptor data inputs must have unique ids");
            }
            if (containsObjectsWithDuplicatedId(descriptorOutputs)) {
                return failedValidation(service, "ServiceDescriptor data outputs must have unique ids");
            }

        }

        PlatformServiceValidationResult platformServiceValidationResult = validatePlatformServiceResourcesSharedMemory(entityToBeSaved);
        if (!platformServiceValidationResult.isValid()) {
            return platformServiceValidationResult;
        }

        return validateRequiredResources(entityToBeSaved);
    }

    private PlatformServiceValidationResult validateCwlDescriptionLength(PlatformService service) {
        if (!isFromCwl(service)) {
            return successfulValidation(service);
        }

        String description = service.getDescription();
        if (description != null && description.length() > MAX_CWL_SERVICE_DESCRIPTION_LENGTH) {
            return failedValidation(service,
                    "Invalid CWL metadata: description exceeds maximum length of "
                            + MAX_CWL_SERVICE_DESCRIPTION_LENGTH + " characters");
        }

        return successfulValidation(service);
    }

    private PlatformServiceValidationResult validatePlatformServiceResourcesSharedMemory(PlatformService service) {

        PlatformServiceResources requiredResources = service.getRequiredResources();
        if (areRequiredResourcesAndSharedMemoryNullOrEmpty(requiredResources)){
            return successfulValidation(service);
        }

        String sharedMemorySize = requiredResources.getSharedMemory().getSize();
        if (!isValidMemorySize(sharedMemorySize)) {
            return failedValidation(service, "Invalid shared memory size");
        }

        String ram = requiredResources.getRam();
        if (!isNullOrEmpty(ram)
                && isValidMemorySize(ram)
                && !isSharedMemorySizeSizeSmallerOrEqualToRam(ram, sharedMemorySize)  ){
            return failedValidation(service, "Shared memory size must be smaller or equal to the RAM size");
        }

        return successfulValidation(service);

    }

    private PlatformServiceValidationResult validateDescriptorInputsAndOutputs(PlatformService service) {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = service.getServiceDescriptor().getDataInputs();
        List<PlatformServiceDescriptor.Parameter> descriptorOutputs = service.getServiceDescriptor().getDataOutputs();

        if (descriptorInputs == null) {
            return failedValidation(service, "ServiceDescriptor data inputs cannot be null");
        }
        if(containsObjectsWithDuplicatedId(descriptorInputs)) {
            return failedValidation(service, "ServiceDescriptor data inputs must have unique ids");
        }
        if(containsObjectsWithDuplicatedId(descriptorOutputs)){
            return failedValidation(service, "ServiceDescriptor data outputs must have unique ids");
        }
        return validateInputsWithSubsetting(service, descriptorInputs);
    }

    private PlatformServiceValidationResult validateInputsWithSubsetting(
            PlatformService service, List<PlatformServiceDescriptor.Parameter> descriptorInputs) {

        if (inputsDoNotHaveSubsetting(descriptorInputs)) {
            return successfulValidation(service);
        }

        for (PlatformServiceDescriptor.Parameter input : descriptorInputs) {
            if (subsettingIsNullOrEmpty(input.getSubsetting())) {
                continue;
            }

            if (StringUtils.isEmpty(input.getSubsetting().getAoiInputRef()) || StringUtils.isEmpty(input.getSubsetting().getFormat())) {
                return failedValidation(service, "Subsetting must contain a reference to another input of type AOI and a format");
            }

            if (!isValidInputWithSubsetting(input, descriptorInputs)) {
                return failedValidation(service, "Each input with subsetting must be of type CATALOGUE and has to reference another input of type AOI");
            }
        }

        return successfulValidation(service);
    }

    private PlatformServiceValidationResult validateRequiredResources(PlatformService service) {

        PlatformServiceResources requiredResources = service.getRequiredResources();

        if(requiredResources == null) {
            return successfulValidation(service);
        }

        if(!isValidResourceValue(requiredResources.getStorage(), VALID_POSITIVE_INTEGER_PATTERN)){
            return failedValidation(service,"Required resources must be greater or equal than zero: storage");
        }
        if(!isValidResourceValue(requiredResources.getGpus(),VALID_POSITIVE_INTEGER_PATTERN)){
            return failedValidation(service,"Required resources must be greater or equal than zero: gpus");
        }
        if(!isValidResourceValue(parseCpuResourceValue(requiredResources), VALID_POSITIVE_INTEGER_PATTERN)){
            return failedValidation(service,"Required resources must be greater or equal than zero: cpus");
        }
        if(!isValidResourceValue(requiredResources.getRam(), VALID_MEMORY_PATTERN)){
            return failedValidation(service,"Required resources must be greater or equal than zero: ram");
        }

        return successfulValidation(service);

    }

    private static String parseCpuResourceValue(PlatformServiceResources requiredResources) {

        return requiredResources.getCpus() != null
            ? requiredResources.getCpus()
                .replaceFirst("\\.", "")
                .replaceFirst("^0+(?!$)", "")
            : null;

    }

    private boolean inputsDoNotHaveSubsetting(List<PlatformServiceDescriptor.Parameter> inputs) {
        return inputs.stream().allMatch(input -> input.getSubsetting() == null);
    }

    private boolean subsettingIsNullOrEmpty(PlatformServiceDescriptor.Subsetting subsetting) {
        return subsetting == null || (StringUtils.isEmpty(subsetting.getAoiInputRef()) && StringUtils.isEmpty(subsetting.getFormat()));
    }

    private boolean areRequiredResourcesAndSharedMemoryNullOrEmpty(PlatformServiceResources requiredResources) {
        return requiredResources == null
               || requiredResources.getSharedMemory() == null
               || isNullOrEmpty(requiredResources.getSharedMemory().getSize());
    }

    private static boolean isValidMemorySize(String memorySize){

        if (memorySize == null){
            return true;
        }

        return VALID_MEMORY_PATTERN.matcher(memorySize.trim()).matches();
    }

    private static boolean isSharedMemorySizeSizeSmallerOrEqualToRam(String ram, String shareMemorySize) {
        long ramSizeInMiB = toMiB(ram);
        long shareSizeInMiB = toMiB(shareMemorySize);
        return shareSizeInMiB <= ramSizeInMiB;
    }

    private static long toMiB(String input) {
        String numberPart = input.replaceAll("[^0-9]", "");
        String unitPart = input.replaceAll("[0-9]", "").toLowerCase(); // normalize unit

        long number = Long.parseLong(numberPart);
        return "gi".equals(unitPart) ? number * 1024 : number;
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isValidInputWithSubsetting(PlatformServiceDescriptor.Parameter inputToBeValidated, List<PlatformServiceDescriptor.Parameter> inputs) {
        if (!inputIsOfType("CATALOGUE", inputToBeValidated)) {
            return false;
        }

        Optional<PlatformServiceDescriptor.Parameter> aoiInput = findDescriptorInputById(inputToBeValidated.getSubsetting().getAoiInputRef(), inputs);

        return aoiInput.map(this::aoiInputIsValid).orElse(false);
    }

    private boolean inputIsOfType(String inputType, PlatformServiceDescriptor.Parameter input) {
        return input.getPlatformMetadata() != null && inputType.equals(input.getPlatformMetadata().get("format"));
    }

    private static Optional<PlatformServiceDescriptor.Parameter> findDescriptorInputById(String id, List<PlatformServiceDescriptor.Parameter> inputs) {
        return inputs.stream().filter(input -> input.getId().equals(id)).findFirst();
    }

    private boolean aoiInputIsValid(PlatformServiceDescriptor.Parameter aoiInput) {
        return inputIsOfType("AOI", aoiInput);
    }

    private static boolean isStatusEqual(PlatformService platformService, PlatformService entity) {
        return platformService.getStatus().equals(entity.getStatus());
    }

    private static boolean isServiceDescriptorTypeEqual(PlatformService platformService, PlatformService entity) {
        return isFromCwl(platformService) == isFromCwl(entity);
    }

    private static boolean isFromCwl(PlatformService entity) { return entity.getCwl() != null; }

    private PlatformServiceValidationResult successfulValidation(PlatformService service) {
        return new PlatformServiceValidationResult(service, true, null);
    }

    private PlatformServiceValidationResult failedValidation(PlatformService service, String errorMessage) {
        return new PlatformServiceValidationResult(service, false, errorMessage);
    }

    private static boolean containsObjectsWithDuplicatedId(List<PlatformServiceDescriptor.Parameter> listOfParametersToCheck){

        if(listOfParametersToCheck == null || listOfParametersToCheck.isEmpty()){
            return false;
        }

        Set<String> idsSet = new HashSet<>();
        for (PlatformServiceDescriptor.Parameter parameter : listOfParametersToCheck) {
            if(idsSet.contains(parameter.getId())) {
                return true;
            }
            idsSet.add(parameter.getId());
        }
        return false;

    }

    private boolean isValidResourceValue(String resourceValue, Pattern matchPattern){
        return resourceValue == null || matchPattern.matcher(resourceValue.trim()).matches();
    }

}
