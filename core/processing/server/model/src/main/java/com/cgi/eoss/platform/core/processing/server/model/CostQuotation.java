package com.cgi.eoss.platform.core.processing.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CostQuotation {

    public enum Recurrence {
        ONE_OFF,
        HOURLY,
        DAILY,
        MONTHLY;
    }

    private final Integer cost;

    private final Recurrence recurrence;

    public CostQuotation(@JsonProperty("cost") Integer cost, @JsonProperty("recurrence") Recurrence recurrence) {
        this.cost = cost;
        this.recurrence = recurrence;
    }

    @Override
    public String toString() {
        return cost + " " + recurrence;
    }
}
