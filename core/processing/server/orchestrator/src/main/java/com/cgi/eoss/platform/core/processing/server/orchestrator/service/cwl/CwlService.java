package com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl;

import com.cgi.eoss.cwl_v1_2.Process;
import com.cgi.eoss.cwl_v1_2.utils.RootLoader;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.UserMount;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.exceptions.CwlValidationException;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.validators.CwlValidationResult;
import com.cgi.eoss.platform.core.processing.server.persistence.service.UserMountDataService;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.snakeyaml.engine.v2.exceptions.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.mappers.CwlToPlatformServiceMapper.mapExtraSchemaAttributes;
import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.mappers.CwlToPlatformServiceMapper.mapInitialWorkDirRequirementDirectoryToUserMounts;
import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.mappers.CwlToPlatformServiceMapper.toPlatformService;
import static com.cgi.eoss.platform.core.processing.server.orchestrator.service.cwl.validators.CwlValidator.validate;

/**
 * Service class that exposes interfaces to retrieve a CWL {@see https://www.commonwl.org/} and
 * then maps it into a {@link PlatformService} object.
 */
@Log4j2
@AllArgsConstructor
public class CwlService {

    private final OkHttpClient cwlHttpClient;

    private final CwlServiceProperties cwlServiceProperties;

    private final UserMountDataService userMountDataService;

    /**
     * Maps to a PlatformService object the attributes coming from a CWL stored externally.
     * @param cwlUrl the URL to the CWL.
     * @return a PlatformService object with attributes mapped from the CWL.
     */
    public PlatformService populateFromCwl(URI cwlUrl) {
        String cwlAsString = downloadCwl(cwlUrl);
        List<Process> cwl = null;

        try {
            cwl = (List<Process>) RootLoader.loadDocument(cwlAsString, cwlUrl.toASCIIString());
        }catch(DuplicateKeyException duplicateKeyException){
            throw new IllegalArgumentException("Exception while parsing CWL from URL: " + cwlUrl, duplicateKeyException);
        }

        validateCwl(cwl);

        PlatformService platformService = toPlatformService(cwl);
        addAdditionalMountsFromCwl(cwl, platformService);
        mapExtraSchemaAttributes(cwlAsString, cwlUrl, platformService);

        return platformService;
    }

    private void addAdditionalMountsFromCwl(List<Process> cwl, PlatformService platformService){

        Map<Long, String> additionalMounts = new HashMap<>();
        for (UserMount cwlUserMount : mapInitialWorkDirRequirementDirectoryToUserMounts(cwl)) {
            UserMount userMount = userMountDataService.getByName(cwlUserMount.getName()).orElseThrow(
                    ()->new IllegalArgumentException( "UserMount name: " + cwlUserMount.getName() + " not found" )
            );
            additionalMounts.put(userMount.getId(), cwlUserMount.getMountPath());
        }

        if (!additionalMounts.isEmpty()) {
            platformService.getAdditionalMounts().putAll(additionalMounts);
        }
    }

    private String downloadCwl(URI cwlUrl) {
        try (Response getResponse = cwlHttpClient.newCall(buildRequest(cwlUrl)).execute()) {
            if (isValidResponseStatus(getResponse)) {
                ResponseBody responseBody = getResponse.body();
                if (responseBody == null || responseBody.contentLength() == 0) {
                    throw new IllegalStateException("Received an empty response body.");
                }
                return readResponse(responseBody);
            }
            int statusCode = getResponse.code();
            LOG.error("Received invalid HTTP status code {} in response from provided URL {}", statusCode, cwlUrl);
            throw new IllegalStateException("Received invalid HTTP status code " + statusCode + " from URL " + cwlUrl);
        } catch (IOException e) {
            LOG.error("Could not download CWL from {} cause {}", cwlUrl, e.getMessage());
            throw new UncheckedIOException(e);
        }
    }

    private static void validateCwl(List<Process> cwl) {
        CwlValidationResult cwlValidationResult = validate(cwl);
        if (!cwlValidationResult.isValid()) {
            throw new CwlValidationException(cwlValidationResult.getValidationMessage());
        }
    }

    private String readResponse(ResponseBody responseBody) {
        StringBuilder responseBuilder = new StringBuilder();
        int maximumSize = cwlServiceProperties.getMaxDocumentSize() + 1;
        try (InputStream in = responseBody.byteStream()) {
            readUntilMaximumSizeIsReached(responseBuilder, in, maximumSize);
        } catch (IOException e) {
            LOG.error("An error occurred while reading response.", e);
            throw new UncheckedIOException(e);
        }
        return responseBuilder.toString();
    }

    private static void readUntilMaximumSizeIsReached(StringBuilder responseBuilder, InputStream in, int maximumSize) throws IOException {
        byte[] buffer = new byte[maximumSize];
        int cumulativeBytesRead = 0;
        int currentBytesRead = 0;
        while (isReadingStream(currentBytesRead) && cumulativeBytesRead < maximumSize) {
            currentBytesRead = in.read(buffer);
            if (isReadingStream(currentBytesRead)) {
                String currentRead = new String(buffer, 0, currentBytesRead);
                responseBuilder.append(currentRead);
                cumulativeBytesRead += currentBytesRead;
            }
        }
        if (isReadingStream(currentBytesRead)) {
            LOG.error("Response exceeds maximum expected size {}", maximumSize);
            throw new IllegalStateException("Response exceeds maximum expected size " + maximumSize);
        }
    }

    public static boolean isReadingStream(int bytesRead) {
        return bytesRead != -1;
    }

    private static boolean isValidResponseStatus(Response response) {
        return response.isSuccessful() || response.isRedirect() || response.code() == 304;
    }

    private static Request buildRequest(URI cwlUrl) throws MalformedURLException {
        return new Request.Builder().url(toUrl(cwlUrl)).get().header("Accept", "*/*").build();
    }

    private static URL toUrl(URI uri) throws MalformedURLException {
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            LOG.error("Malformed CWL reference URL {}", uri);
            throw e;
        }
    }
}
