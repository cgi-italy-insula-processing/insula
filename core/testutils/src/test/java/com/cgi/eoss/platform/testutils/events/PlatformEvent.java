package com.cgi.eoss.platform.testutils.events;

import lombok.Builder;
import lombok.Value;

/**
 * Data class that contains the details of a generic platform event.
 *
 *
 * @author cantaveneraf
 *
 */
@Value
@Builder
public class PlatformEvent {
    private final long timestamp;
    private final String type;
    private final Object value;

    /**
     * Return the value wrapped by the platform event statically casting it to the destination type
     *
     * @param <T>
     *            The destination type
     * @return
     *         The platform event value casted to the destination type
     */
    @SuppressWarnings("unchecked")
    public <T> T getValueAs() {
        return (T) value;
    }

}
