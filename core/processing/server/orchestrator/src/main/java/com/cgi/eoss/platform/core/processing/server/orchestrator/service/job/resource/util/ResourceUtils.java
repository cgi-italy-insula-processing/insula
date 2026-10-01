package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.util;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing and converting resource values.
 */
public final class ResourceUtils {

    private static final int MB_IN_GB = 1024;
    private static final Pattern RAM_UNIT_PATTERN = Pattern.compile("^(\\d+)(Gi|Mi)?$", Pattern.CASE_INSENSITIVE);

    private ResourceUtils() {
    }

    /**
     * Get Service storage in MB format from service required resources
     *
     * @param requiredResources Actual service requested resources
     * @return The requested storage value in MB
     * @throws IllegalArgumentException if storage value is not numeric or contains units
     */
    public static Integer getServiceStorageInMb(PlatformServiceResources requiredResources) {
        if (!isStorageRequirementSet(requiredResources)) {
            return null;
        }
        try {
            return Integer.parseInt(requiredResources.getStorage().trim()) * MB_IN_GB;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid storage value - must be a positive integer without units: "
                            + requiredResources.getStorage(), e);
        }
    }

    /**
     * Converts a RAM string value into (MiB).
     * Supported formats: positive integers with optional "Gi" or "Mi" suffix.
     * Decimal or negative values are not allowed.
     *
     * @param requiredResources Actual service requested resources
     * @return the RAM value converted to Mi
     */
    public static Integer getServiceRamInMb(PlatformServiceResources requiredResources) {
        if (!isRamRequirementSet(requiredResources)) {
            return null;
        }
        String ram = requiredResources.getRam();
        Matcher matcher = RAM_UNIT_PATTERN.matcher(ram.trim());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("RAM value is invalid: must be a positive integer with 'Mi' or 'Gi' unit: " + ram);
        }
        int value = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);
        if ("Gi".equalsIgnoreCase(unit)) {
            return value * MB_IN_GB;
        }
        return value;
    }

    /**
     * Get Service gpus format from service required resources
     *
     * @param requiredResources Service requested gpus
     * @return The requested gpus
     * @throws IllegalArgumentException if gpus is not numeric or contains units
     */
    public static Integer getServiceGPUs(PlatformServiceResources requiredResources) {
        if (!isGPUsRequirementSet(requiredResources)) {
            return null;
        }
        try {
            return Integer.parseInt(requiredResources.getGpus().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid GPUs value - must be a positive integer without units: "
                            + requiredResources.getGpus(), e);
        }
    }

    /**
     * Returns the service CPU requirement expressed in millicpu.
     *
     * @param platformServiceResources Service requested resources
     * @return CPU requirement in millicpu, or null if not defined
     * * @throws IllegalArgumentException if cpus is not positive or contains units
     */
    public static Integer getServiceCpuInMillis(PlatformServiceResources platformServiceResources) {
        if (!isCpuRequirementSet(platformServiceResources)) {
            return null;
        }
        try {
            double cpuValueParsed = Double.parseDouble(platformServiceResources.getCpus().trim().replace(",", "."));
            if (cpuValueParsed < 0) {
                throw new NumberFormatException();
            }
            return (int) Math.floor(cpuValueParsed * 1000.0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid CPU value - must be a positive number without units: "
                            + platformServiceResources.getCpus().trim(),
                    e
            );
        }
    }

    private static boolean isCpuRequirementSet(PlatformServiceResources platformServiceResources) {
        return platformServiceResources != null &&
                hasText(platformServiceResources.getCpus());
    }

    private static boolean isRamRequirementSet(PlatformServiceResources platformServiceResources) {
        return platformServiceResources != null &&
                hasText(platformServiceResources.getRam());
    }

    private static boolean isGPUsRequirementSet(PlatformServiceResources platformServiceResources) {
        return platformServiceResources != null &&
                hasText(platformServiceResources.getGpus());
    }

    private static boolean isStorageRequirementSet(PlatformServiceResources platformServiceResources) {
        return platformServiceResources != null &&
                hasText(platformServiceResources.getStorage());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }


}
