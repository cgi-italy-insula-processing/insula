package com.cgi.eoss.platform.core.processing.server.orchestrator.service;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.stac.model.StacDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>Class that represents the input of a Job and exposes methods to
 * retrieve and process it.</p>
 */
@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@Log4j2
public class JobInput {

    private final String id;

    private final List<String> values;

    private final Type type;

    private final URL internalReference;

    private final Object contents;

    private final boolean parallelInput;

    /**
     * <p>Enum representing the different types of input that a Job can have.</p>
     */
    public enum Type {
        URL,
        STAC,
        OTHER
    }

    /**
     * <p>Method to get the input content as a list of Stac features.</p>
     * @return the input content as a list of Stac features.
     */
    public List<StacDocument.StacItem> getContentsAsStac() {
        checkStacType();
        return (List<StacDocument.StacItem>) contents;
    }

    /**
     * <p>Returns a Collection holding the internal references for the current Job Input.
     * The current implementation only works for inputs of STAC type.</p>
     * @return the Job Input's internal references.
     */
    public Collection<String> getInternalReferences() {
        // TODO: add support for non-STAC input types
        checkStacType();
        if (this.isParallelInput()) {
            URL internalReferenceWithFragment =
                    this.getValuesToInternalReferences().get(String.join("", this.getValues()));
            return Collections.singletonList(
                    toASCIIString(internalReferenceWithFragment)
            );
        }
        return Collections.singletonList(toASCIIString(this.getInternalReference()));
    }

    /**
     * <p>Checks whether the current input is exploded by looking for a fragment in its values.
     * The current implementation only works for inputs of STAC type.</p>
     * @return ture if the input is exploded, false otherwise.
     */
    public boolean isExploded() {
        // TODO: add support for non-STAC input types
        return this.getValues().stream().anyMatch(input -> input.contains("#"));
    }

    /**
     * <p>
     *     Returns the values of this Job Input without any fragment that
     *     might be attached to them.
     * </p>
     * @return a list of values without fragments.
     */
    public List<String> getValuesWithoutFragments() {
        return this.getValues().stream()
                .map(url -> url.split("#")[0])
                .collect(Collectors.toList());
    }

    static Type getTypeFromParameter(PlatformServiceDescriptor.Parameter parameter) {
        Map<String, String> platformMetadata = Optional.ofNullable(parameter.getPlatformMetadata())
                .orElse(Collections.emptyMap());

        if (isStacType(platformMetadata)) {
            return Type.STAC;
        }
        if (isCatalogueFormat(platformMetadata) || isDownloadable(platformMetadata, parameter.getDefaultAttrs())) {
            return Type.URL;
        }
        return Type.OTHER;
    }

    /**
     * <p>Method to get a map linking each value to its corresponding
     * internalReference URL to the Stac document with a fragment of its feature identifier.</p>
     * @return the map linking to each value the corresponding internalReference URL
     *         with the fragment of its feature identifier.
     */
    private Map<String, URL> getValuesToInternalReferences() {
        HashMap<String, URL> featureIdsToInternalReferences = new HashMap<>();
        for (String value : values) {
            featureIdsToInternalReferences.put(value,
                    buildUrlWithFragment(this.getInternalReference(), getFragmentFromValue(value)));
        }
        return featureIdsToInternalReferences;
    }

    private static boolean isDownloadable(Map<String, String> platformMetadata, Map<String, String> defaultAttrs) {
        return "false".equals(platformMetadata.getOrDefault("preventUrlDownload", "true")) ||
                isUrl(platformMetadata, defaultAttrs);
    }

    private static boolean isUrl(Map<String, String> platformMetadata, Map<String, String> defaultAttrs) {
        if (platformMetadata.isEmpty()) {
            return true;
        }
        return "string".equals(defaultAttrs.get("dataType")) &&
                !defaultAttrs.containsKey("allowedValues") &&
                "OTHER".equals(platformMetadata.get("format")) &&
                "false".equals(platformMetadata.getOrDefault("preventUrlDownload", "false"));
    }

    private static boolean isStacType(Map<String, String> platformMetadata) {
        return "STAC".equalsIgnoreCase(platformMetadata.get("type"));
    }

    private static boolean isCatalogueFormat(Map<String, String> platformMetadata) {
        return "CATALOGUE".equals(platformMetadata.get("format"));
    }

    private static String getFragmentFromValue(String value) {
        int lastIndexOfFragment = value.lastIndexOf("#");
        return value.substring(lastIndexOfFragment+1);
    }

    private static URL buildUrlWithFragment(URL url, String fragment) {
        try {
            return new URL(url.toString() + "#" + fragment);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Error building URL with fragment: " + e.getMessage());
        }
    }

    private static String toASCIIString(URL url) {
        try {
            return url.toURI().toASCIIString();
        } catch (URISyntaxException e) {
            LOG.error("Could not parse url {}", url);
            throw new IllegalStateException(e);
        }
    }

    private void checkStacType() {
        if (!Type.STAC.equals(this.getType())) {
            throw new IllegalStateException("Input is not of type " + Type.STAC);
        }
    }
}
