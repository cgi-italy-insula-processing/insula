package com.cgi.eoss.platform.core.processing.k8seventcollector.worker.kubernetes.workflow.model;

import org.junit.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class StatusTest {

    private static final String START_STRING = "2020-07-30T07:56:56Z";
    private static final String END_STRING = "2020-07-30T07:57:31Z";
    private static final Instant START = Instant.parse(START_STRING);
    private static final Instant END = Instant.parse(END_STRING);

    @Test
    public void testBuildStatusFromMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("phase", "one");
        map.put("startedAt", START_STRING);
        map.put("finishedAt", END_STRING);

        Status status = Status.from(map);
        assertThat(status.getPhase(), is("one"));
        assertThat(status.getStartedAt(), is(START));
        assertThat(status.getFinishedAt(), is(END));
    }

    @Test
    public void testBuildStatusFromMapWithUnknownKeys() {
        Map<String, Object> map = new HashMap<>();
        map.put("phase", "one");
        map.put("started", START_STRING);
        map.put("finished", END_STRING);

        Status status = Status.from(map);
        assertThat(status.getPhase(), is("one"));
        assertThat(status.getStartedAt(), nullValue());
        assertThat(status.getFinishedAt(), nullValue());
    }

    @Test
    public void testBuildStatusFromMapWithMissingKeys() {
        Map<String, Object> map = new HashMap<>();
        map.put("phase", "one");

        Status status = Status.from(map);
        assertThat(status.getPhase(), is("one"));
        assertThat(status.getStartedAt(), nullValue());
        assertThat(status.getFinishedAt(), nullValue());
    }

}
