package com.cgi.eoss.platform.core.processing.server.api.controllers;

import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDockerBuildInfo;
import com.cgi.eoss.platform.core.processing.server.persistence.service.ServiceDataService;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing platform services.
 */
@RestController
@RequestMapping("/services")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Log4j2
public class ServicesApiExtension {

    private final ServiceDataService serviceDataService;

    /**
     * Disables a platform service by setting its status to DISABLED.
     *
     * @param service the platform service to be disabled
     * @return a ResponseEntity indicating the result of the operation
     */
    @PostMapping("/{serviceId}/disable")
    public ResponseEntity disableService(@ModelAttribute("serviceId") PlatformService service) {


        service.setStatus(PlatformService.Status.DISABLED);
        PlatformService save = serviceDataService.save(service);

        return new ResponseEntity<>(HttpStatus.OK);
    }

}