package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.Cwl;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformServiceValidatorTest {

    private final PlatformServiceValidator serviceValidator = new PlatformServiceValidator();
    private final User user = new User("user");

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenServiceDescriptorIsNull() {
        PlatformService service = new PlatformService("service1", user, "dockerTag");
        service.setServiceDescriptor(null);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("ServiceDescriptor cannot be null");
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenServiceDescriptorInputsAreNull() {
        PlatformService service = createServiceWithDescriptorInputs(null);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("ServiceDescriptor data inputs cannot be null");
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenServiceDescriptorInputsIdAreNotUnique() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in").build());
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in").build());
        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                createServiceWithDescriptorInputs(descriptorInputs),false,"ServiceDescriptor data inputs must have unique ids"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenServiceDescriptorOutputsIdAreNotUnique() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in").build());
        List<PlatformServiceDescriptor.Parameter> descriptorOutputs = new ArrayList<>();
        descriptorOutputs.add(PlatformServiceDescriptor.Parameter.builder().id("in").build());
        descriptorOutputs.add(PlatformServiceDescriptor.Parameter.builder().id("in").build());
        PlatformServiceDescriptor serviceDescriptor = new PlatformServiceDescriptor();
        serviceDescriptor.setDataInputs(descriptorInputs);
        serviceDescriptor.setDataOutputs(descriptorOutputs);
        PlatformService service = new PlatformService("service1", user, "dockerTag");
        service.setServiceDescriptor(serviceDescriptor);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        List<PlatformServiceDescriptor.Parameter> expectedDescriptorInputs = new ArrayList<>();
        expectedDescriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in").build());
        List<PlatformServiceDescriptor.Parameter> expectedDescriptorOutputs = new ArrayList<>();
        expectedDescriptorOutputs.add(PlatformServiceDescriptor.Parameter.builder().id("in").build());
        expectedDescriptorOutputs.add(PlatformServiceDescriptor.Parameter.builder().id("in").build());
        PlatformService expectedService = new PlatformService("service1", user, "dockerTag");
        PlatformServiceDescriptor expectedServiceDescriptor = new PlatformServiceDescriptor();
        expectedServiceDescriptor.setDataInputs(expectedDescriptorInputs);
        expectedServiceDescriptor.setDataOutputs(expectedDescriptorOutputs);
        expectedService.setServiceDescriptor(expectedServiceDescriptor);

        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                expectedService,false,"ServiceDescriptor data outputs must have unique ids"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceDescriptorInputsDoNotContainSubsetting() {
        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in")
                .platformMetadata(ImmutableMap.of("format", "CATALOGUE"))
                .build());
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("aoi")
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .build());

        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenSubsettingInInputHasEmptyFields() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in")
                .platformMetadata(ImmutableMap.of("format", "OTHER"))
                .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("").format("").build())
                .build());
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("aoi")
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .build());

        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenSubsettingInInputHasNullFields() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in")
                .platformMetadata(ImmutableMap.of("format", "OTHER"))
                .subsetting(PlatformServiceDescriptor.Subsetting.builder().build())
                .build());
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("aoi")
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .build());

        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenInputWithSubsettingIsOfTypeCatalogueAndReferencesAnotherInputOfTypeAOI() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in")
                .platformMetadata(ImmutableMap.of("format", "CATALOGUE"))
                .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoi").format("format").build())
                .build());
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("aoi")
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .build());

        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenSubsettingHasOnlyAoiInputRefFieldSet() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in")
                .platformMetadata(ImmutableMap.of("format", "OTHER"))
                .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoi").format("").build())
                .build());
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("aoi")
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .build());

        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Subsetting must contain a reference to another input of type AOI and a format");
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenSubsettingHasOnlyFormatFieldSet() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in")
                .platformMetadata(ImmutableMap.of("format", "OTHER"))
                .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("").format("format").build())
                .build());
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("aoi")
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .build());

        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Subsetting must contain a reference to another input of type AOI and a format");
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenInputWithSubsettingIsNotOfTypeCatalogue() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in")
                .platformMetadata(ImmutableMap.of("format", "OTHER"))
                .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoi").format("format").build())
                .build());
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("aoi")
                .platformMetadata(ImmutableMap.of("format", "AOI"))
                .build());

        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Each input with subsetting must be of type CATALOGUE and has to reference another input of type AOI");
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenInputWithSubsettingReferencesAnInputOfTypeAOIThatDoesNotExist() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in")
                .platformMetadata(ImmutableMap.of("format", "CATALOGUE"))
                .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoi").format("format").build())
                .build());

        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Each input with subsetting must be of type CATALOGUE and has to reference another input of type AOI");
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenInputWithSubsettingReferencesAnInputThatIsNotOfTypeAOI() {

        List<PlatformServiceDescriptor.Parameter> descriptorInputs = new ArrayList<>();
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("in")
                .platformMetadata(ImmutableMap.of("format", "CATALOGUE"))
                .subsetting(PlatformServiceDescriptor.Subsetting.builder().aoiInputRef("aoi").format("format").build())
                .build());
        descriptorInputs.add(PlatformServiceDescriptor.Parameter.builder().id("aoi")
                .platformMetadata(ImmutableMap.of("format", "OTHER"))
                .build());

        PlatformService service = createServiceWithDescriptorInputs(descriptorInputs);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Each input with subsetting must be of type CATALOGUE and has to reference another input of type AOI");
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenStorageServiceRequirementIsLessThanZero() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setStorage("-1");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        PlatformServiceResources expectedResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        expectedResources.setStorage("-1");
        PlatformService expectedService = createServiceWithDescriptorInputs(new ArrayList<>());
        expectedService.setRequiredResources(expectedResources);
        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                expectedService,false,"Required resources must be greater or equal than zero: storage"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenGpuServiceRequirementIsLessThanZero() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<PlatformServiceDescriptor.Parameter>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setGpus("-1");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        PlatformServiceResources expectedResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        expectedResources.setGpus("-1");
        PlatformService expectedService = createServiceWithDescriptorInputs(new ArrayList<>());
        expectedService.setRequiredResources(expectedResources);
        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                expectedService,false,"Required resources must be greater or equal than zero: gpus"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenRamServiceRequirementIsLessThanZero() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setRam("-1Mi");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        PlatformServiceResources expectedResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        expectedResources.setRam("-1Mi");
        PlatformService expectedService = createServiceWithDescriptorInputs(new ArrayList<>());
        expectedService.setRequiredResources(expectedResources);
        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                expectedService,false,"Required resources must be greater or equal than zero: ram"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenCpuServiceRequirementIsLessThanZero() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setCpus("-1");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        PlatformServiceResources expectedResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        expectedResources.setCpus("-1");
        PlatformService expectedService = createServiceWithDescriptorInputs(new ArrayList<>());
        expectedService.setRequiredResources(expectedResources);
        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                expectedService,false,"Required resources must be greater or equal than zero: cpus"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenCpuServiceRequirementIsNotAnInteger(){

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setCpus("notAnInteger");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        PlatformServiceResources expectedResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        expectedResources.setCpus("notAnInteger");
        PlatformService expectedService = createServiceWithDescriptorInputs(new ArrayList<>());
        expectedService.setRequiredResources(expectedResources);
        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                expectedService,false,"Required resources must be greater or equal than zero: cpus"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenGpuServiceRequirementIsNotAnInteger(){

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setGpus("notAnInteger");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        PlatformServiceResources expectedResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        expectedResources.setGpus("notAnInteger");
        PlatformService expectedService = createServiceWithDescriptorInputs(new ArrayList<>());
        expectedService.setRequiredResources(expectedResources);
        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                expectedService,false,"Required resources must be greater or equal than zero: gpus"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenRamServiceRequirementIsNotAnInteger(){

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setRam("notAnInteger");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        PlatformServiceResources expectedResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        expectedResources.setRam("notAnInteger");
        PlatformService expectedService = createServiceWithDescriptorInputs(new ArrayList<>());
        expectedService.setRequiredResources(expectedResources);
        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                expectedService,false,"Required resources must be greater or equal than zero: ram"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenStorageServiceRequirementIsNotAnInteger(){

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setStorage("notAnInteger");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult = serviceValidator.validate(service);

        PlatformServiceResources expectedResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        expectedResources.setStorage("notAnInteger");
        PlatformService expectedService = createServiceWithDescriptorInputs(new ArrayList<>());
        expectedService.setRequiredResources(expectedResources);
        PlatformServiceValidator.PlatformServiceValidationResult expectedValidationResult = new PlatformServiceValidator.PlatformServiceValidationResult(
                expectedService,false,"Required resources must be greater or equal than zero: storage"
        );
        assertThat(actualValidationResult).isEqualTo(expectedValidationResult);

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenAllRequirementAreEqualToZero() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setStorage("0");
        serviceResources.setCpus("0");
        serviceResources.setGpus("0");
        serviceResources.setRam("0Mi");
        serviceResources.getSharedMemory().setSize("0Mi");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.validate(service);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("0");
        assertThat(actualServiceResources.getGpus()).isEqualTo("0");
        assertThat(actualServiceResources.getCpus()).isEqualTo("0");
        assertThat(actualServiceResources.getRam()).isEqualTo("0Mi");
        assertThat(actualServiceResources.getSharedMemory().getSize()).isEqualTo("0Mi");
        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenAllRequirementAreGreaterThanZero() {

        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setStorage("1");
        serviceResources.setCpus("1");
        serviceResources.setGpus("1");
        serviceResources.setRam("1Mi");
        serviceResources.getSharedMemory().setSize("1Mi");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.validate(newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("1");
        assertThat(actualServiceResources.getGpus()).isEqualTo("1");
        assertThat(actualServiceResources.getCpus()).isEqualTo("1");
        assertThat(actualServiceResources.getRam()).isEqualTo("1Mi");
        assertThat(actualServiceResources.getSharedMemory().getSize()).isEqualTo("1Mi");
        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenEntityToBeSavedHasTheSameStatusAsTheExistingService() {

        PlatformService service = new PlatformService("service1", user, "dockerTag");
        service.setStatus(PlatformService.Status.IN_DEVELOPMENT);

        PlatformService entityToBeSaved = new PlatformService("serviceNewName", user, "dockerTag");
        entityToBeSaved.setStatus(PlatformService.Status.IN_DEVELOPMENT);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(service, entityToBeSaved);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNonValidValidationResult_WhenEntityToBeSavedHasDifferentStatusAsTheExistingService() {

        PlatformService service = new PlatformService("service1", user, "dockerTag");
        service.setStatus(PlatformService.Status.IN_DEVELOPMENT);

        PlatformService entityToBeSaved = new PlatformService("serviceNewName", user, "dockerTag");
        entityToBeSaved.setStatus(PlatformService.Status.AVAILABLE);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(service, entityToBeSaved);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Status update from " + service.getStatus() + " to " + entityToBeSaved.getStatus() + " is forbidden for Service " + service.getId());
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenEntityToBeSavedAndTheExistingServiceAreBothFromCwl() {
        Cwl cwl = new Cwl(URI.create("https://reference.url/cwl"), "document as free text");

        PlatformService service = new PlatformService("service1", user, "dockerTag");
        service.setCwl(cwl);

        PlatformService entityToBeSaved = new PlatformService("serviceNewName", user, "dockerTag");
        entityToBeSaved.setCwl(cwl);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(service, entityToBeSaved);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNonValidValidationResult_WhenEntityToBeSavedAndTheExistingServiceAreNotBothFromCwl() {
        Cwl cwl = new Cwl(URI.create("https://reference.url/cwl"), "document as free text");

        PlatformService service = new PlatformService("service1", user, "dockerTag");
        service.setCwl(cwl);

        PlatformService entityToBeSaved = new PlatformService("serviceNewName", user, "dockerTag");
        entityToBeSaved.setCwl(null);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(service, entityToBeSaved);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Update is forbidden for Service " + service.getId() + ": only one of the two services (existing and updated) is defined through CWL.");
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenPlatformServiceResourcesIsNull() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setRequiredResources(null);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenSharedMemoryIsNull() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setRequiredResources(createDefaultPlatformServiceResourcesAndSharedMemory());
        service.getRequiredResources().setSharedMemory(null);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenSharedMemoryIsEmpty() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setRequiredResources(createDefaultPlatformServiceResourcesAndSharedMemory());
        PlatformServiceResources.SharedMemory sharedMemory = new PlatformServiceResources.SharedMemory();
        sharedMemory.setSize("");
        service.getRequiredResources().setSharedMemory(sharedMemory);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenSharedMemoryIsEmptyUsingSpace() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setRequiredResources(createDefaultPlatformServiceResourcesAndSharedMemory());
        PlatformServiceResources.SharedMemory sharedMemory = new PlatformServiceResources.SharedMemory();
        sharedMemory.setSize("   ");
        service.getRequiredResources().setSharedMemory(sharedMemory);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenSharedMemorySizeIsSetAndRamIsNotDefined() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.getSharedMemory().setSize("10Gi");
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenRamIsGreaterThanSharedMemorySize() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setRam("20Gi");
        serviceResources.setSharedMemory(PlatformServiceResources.SharedMemory.builder().size("10Gi").build());
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenRamIsGreaterThanSharedMemorySizeAndLowerCaseUsingGi() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setRam("20gi");
        serviceResources.setSharedMemory(PlatformServiceResources.SharedMemory.builder().size("10gi").build());
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenRamIsGreaterThanSharedMemorySizeAndLowerCaseUsingMi() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setRam("20mi");
        serviceResources.setSharedMemory(PlatformServiceResources.SharedMemory.builder().size("10mi").build());
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenRamIsGreaterThanSharedMemorySizeUsingDifferentUnitsOfMeasurement() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setRam("20000Mi");
        serviceResources.setSharedMemory(PlatformServiceResources.SharedMemory.builder().size("10gi").build());
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenRamIsEqualToSharedMemorySizeUsingDifferentUnitsOfMeasurement() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setRam("10240Mi");
        serviceResources.setSharedMemory(PlatformServiceResources.SharedMemory.builder().size("10gi").build());
        service.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenRamIsLessThanSharedMemorySize() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10Gi", "9Gi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Shared memory size must be smaller or equal to the RAM size");
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenRamIsLessThanSharedMemorySizeUsingDifferentUnitsOfMeasurement() {

        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10Gi", "2000Mi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Shared memory size must be smaller or equal to the RAM size");
    }

    @Test
    public void testValidate_ReturnsNonValidValidationResult_WhenServiceIsFromCwlAndDescriptionExceedsMaxLength() {
        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setCwl(new Cwl(URI.create("https://reference.url/cwl"), "document as free text"));
        service.setDescription(StringUtils.repeat("a", 256));

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage())
                .isEqualTo("Invalid CWL metadata: description exceeds maximum length of 255 characters");
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceIsNotFromCwlAndDescriptionExceedsMaxLength() {
        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setDescription(StringUtils.repeat("a", 256));

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceIsFromCwlAndDescriptionDoesNotExceedMaxLength() {
        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setCwl(new Cwl(URI.create("https://reference.url/cwl/app-package.cwl"), "document as free text"));
        service.setDescription(StringUtils.repeat("a", 255));

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testValidate_ReturnsValidValidationResult_WhenServiceIsFromCwlAndDescriptionIsNull() {
        PlatformService service = createServiceWithDescriptorInputs(new ArrayList<>());
        service.setCwl(new Cwl(URI.create("https://reference.url/cwl/app-package.cwl"), "cwl document"));
        service.setDescription(null);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.validate(service);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenPlatformServiceResourcesIsNull() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(null);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenSharedMemoryIsNull() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(new PlatformServiceResources());
        newService.getRequiredResources().setSharedMemory(null);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenSharedMemoryIsEmpty() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(new PlatformServiceResources());

        PlatformServiceResources.SharedMemory sharedMemory = new PlatformServiceResources.SharedMemory();
        sharedMemory.setSize("");
        newService.getRequiredResources().setSharedMemory(sharedMemory);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenSharedMemoryIsEmptyUsingSpace() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(new PlatformServiceResources());

        PlatformServiceResources.SharedMemory sharedMemory = new PlatformServiceResources.SharedMemory();
        sharedMemory.setSize("   ");
        newService.getRequiredResources().setSharedMemory(sharedMemory);

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenSharedMemorySizeIsSetAndRamIsNotDefined() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10Gi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenRamIsGreaterThanSharedMemorySize() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10Gi", "20Gi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenRamIsGreaterThanSharedMemorySizeAndLowerCaseUsingGi() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10gi", "20gi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenRamIsGreaterThanSharedMemorySizeAndLowerCaseUsingMi() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10mi", "20mi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenRamIsGreaterThanSharedMemorySizeUsingDifferentUnitsOfMeasurement() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10Gi", "20000Mi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenRamIsEqualToSharedMemorySizeUsingDifferentUnitsOfMeasurement() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10Gi", "10240Mi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isTrue();
        assertThat(validationResult.getErrorMessage()).isNull();
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNonValidValidationResult_WhenRamIsLessThanSharedMemorySize() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10Gi", "9Gi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Shared memory size must be smaller or equal to the RAM size");
    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNonValidValidationResult_WhenRamRequirementIsLessThanZero() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setRam("-1Mi");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("10240");
        assertThat(actualServiceResources.getGpus()).isEqualTo("0");
        assertThat(actualServiceResources.getCpus()).isEqualTo("500");
        assertThat(actualServiceResources.getRam()).isEqualTo("-1Mi");
        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Required resources must be greater or equal than zero: ram");

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNonValidValidationResult_WhenCpusRequirementIsLessThanZero() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setCpus("-1");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("10240");
        assertThat(actualServiceResources.getGpus()).isEqualTo("0");
        assertThat(actualServiceResources.getCpus()).isEqualTo("-1");
        assertThat(actualServiceResources.getRam()).isEqualTo("20480Mi");
        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Required resources must be greater or equal than zero: cpus");

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNonValidValidationResult_WhenGpusRequirementIsLessThanZero() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setGpus("-1");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("10240");
        assertThat(actualServiceResources.getGpus()).isEqualTo("-1");
        assertThat(actualServiceResources.getCpus()).isEqualTo("500");
        assertThat(actualServiceResources.getRam()).isEqualTo("20480Mi");
        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Required resources must be greater or equal than zero: gpus");

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNonValidValidationResult_WhenStorageRequirementIsLessThanZero() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setStorage("-1");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("-1");
        assertThat(actualServiceResources.getGpus()).isEqualTo("0");
        assertThat(actualServiceResources.getCpus()).isEqualTo("500");
        assertThat(actualServiceResources.getRam()).isEqualTo("20480Mi");
        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Required resources must be greater or equal than zero: storage");

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNotValidValidationResult_WhenStorageRequirementIsNotAnInteger() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setStorage("notAnInteger");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("notAnInteger");
        assertThat(actualServiceResources.getGpus()).isEqualTo("0");
        assertThat(actualServiceResources.getCpus()).isEqualTo("500");
        assertThat(actualServiceResources.getRam()).isEqualTo("20480Mi");
        assertThat(actualServiceResources.getSharedMemory().getSize()).isEqualTo("5120Mi");
        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Required resources must be greater or equal than zero: storage");

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNotValidValidationResult_WhenCpuRequirementIsNotAnInteger() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setCpus("notAnInteger");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();

        assertThat(actualServiceResources.getStorage()).isEqualTo("10240");
        assertThat(actualServiceResources.getGpus()).isEqualTo("0");
        assertThat(actualServiceResources.getCpus()).isEqualTo("notAnInteger");
        assertThat(actualServiceResources.getRam()).isEqualTo("20480Mi");
        assertThat(actualServiceResources.getSharedMemory().getSize()).isEqualTo("5120Mi");
        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Required resources must be greater or equal than zero: cpus");

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNotValidValidationResult_WhenGpuRequirementIsNotAnInteger() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setGpus("notAnInteger");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("10240");
        assertThat(actualServiceResources.getGpus()).isEqualTo("notAnInteger");
        assertThat(actualServiceResources.getCpus()).isEqualTo("500");
        assertThat(actualServiceResources.getRam()).isEqualTo("20480Mi");
        assertThat(actualServiceResources.getSharedMemory().getSize()).isEqualTo("5120Mi");
        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Required resources must be greater or equal than zero: gpus");

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNotValidValidationResult_WhenRamRequirementIsNotAnInteger() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setRam("notAnInteger");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("10240");
        assertThat(actualServiceResources.getGpus()).isEqualTo("0");
        assertThat(actualServiceResources.getCpus()).isEqualTo("500");
        assertThat(actualServiceResources.getRam()).isEqualTo("notAnInteger");
        assertThat(actualServiceResources.getSharedMemory().getSize()).isEqualTo("5120Mi");
        assertThat(actualValidationResult.isValid()).isFalse();
        assertThat(actualValidationResult.getErrorMessage()).isEqualTo("Required resources must be greater or equal than zero: ram");

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenAllRequirementAreEqualToZero() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setStorage("0");
        serviceResources.setCpus("0");
        serviceResources.setGpus("0");
        serviceResources.setRam("0Mi");
        serviceResources.getSharedMemory().setSize("0Mi");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("0");
        assertThat(actualServiceResources.getGpus()).isEqualTo("0");
        assertThat(actualServiceResources.getCpus()).isEqualTo("0");
        assertThat(actualServiceResources.getRam()).isEqualTo("0Mi");
        assertThat(actualServiceResources.getSharedMemory().getSize()).isEqualTo("0Mi");
        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsValidValidationResult_WhenAllRequirementAreGreaterThanZero() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformServiceResources serviceResources = createDefaultPlatformServiceResourcesAndSharedMemory();
        serviceResources.setStorage("1");
        serviceResources.setCpus("1");
        serviceResources.setGpus("1");
        serviceResources.setRam("1Mi");
        serviceResources.getSharedMemory().setSize("1Mi");
        newService.setRequiredResources(serviceResources);

        PlatformServiceValidator.PlatformServiceValidationResult actualValidationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        PlatformServiceResources actualServiceResources = actualValidationResult.getPlatformService().getRequiredResources();
        assertThat(actualServiceResources.getStorage()).isEqualTo("1");
        assertThat(actualServiceResources.getGpus()).isEqualTo("1");
        assertThat(actualServiceResources.getCpus()).isEqualTo("1");
        assertThat(actualServiceResources.getRam()).isEqualTo("1Mi");
        assertThat(serviceResources.getSharedMemory().getSize()).isEqualTo("1Mi");
        assertThat(actualValidationResult.isValid()).isTrue();
        assertThat(actualValidationResult.getErrorMessage()).isNull();

    }

    @Test
    public void testCheckIfUpdateIsAllowed_ReturnsNonValidValidationResult_WhenRamIsLessThanSharedMemorySizeUsingDifferentUnitsOfMeasurement() {

        PlatformService oldService = createServiceWithDescriptorInputs(new ArrayList<>());
        PlatformService newService = createServiceWithDescriptorInputs(new ArrayList<>());
        newService.setRequiredResources(
                createPlatformServiceResourcesAndSharedMemory(
                        "10Gi", "2000Mi")
        );

        PlatformServiceValidator.PlatformServiceValidationResult validationResult =
                serviceValidator.checkIfUpdateIsAllowed(oldService, newService);

        assertThat(validationResult.isValid()).isFalse();
        assertThat(validationResult.getErrorMessage()).isEqualTo("Shared memory size must be smaller or equal to the RAM size");
    }

    private PlatformService createServiceWithDescriptorInputs(List<PlatformServiceDescriptor.Parameter> descriptorInputs) {
        PlatformServiceDescriptor serviceDescriptor = new PlatformServiceDescriptor();
        serviceDescriptor.setDataInputs(descriptorInputs);
        PlatformService service = new PlatformService("service1", user, "dockerTag");
        service.setServiceDescriptor(serviceDescriptor);
        return service;
    }

    private PlatformServiceResources createPlatformServiceResourcesAndSharedMemory(String sharedMemorySize, String platformServiceResourcesRam) {
        PlatformServiceResources platformServiceResources = new PlatformServiceResources();
        platformServiceResources.setRam(platformServiceResourcesRam);
        PlatformServiceResources.SharedMemory sharedMemory = new PlatformServiceResources.SharedMemory();
        sharedMemory.setSize(sharedMemorySize);
        platformServiceResources.setSharedMemory(sharedMemory);

        return platformServiceResources;
    }

    private PlatformServiceResources createDefaultPlatformServiceResourcesAndSharedMemory(){

        return PlatformServiceResources
            .builder()
            .storage("10240")
            .gpus("0")
            .ram("20480Mi")
            .cpus("500")
            .sharedMemory(
                    PlatformServiceResources.SharedMemory.builder()
                            .size("5120Mi")
                            .build()
            )
        .build();

    }

    private PlatformServiceResources createPlatformServiceResourcesAndSharedMemory(String sharedMemorySize) {
        return createPlatformServiceResourcesAndSharedMemory(sharedMemorySize, null);
    }

}