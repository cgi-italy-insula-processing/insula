package com.cgi.eoss.platform.core.processing.worker.kubernetes;

import com.cgi.eoss.platform.core.processing.worker.kubernetes.K8sJobParams.Subsetting;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class K8sJobParamsTest {

    private final K8sJobParams k8sJobParams = new K8sJobParams();

    @Test
    public void testPut_AddsK8sJobParamWithEmptyValues_WhenValuesIsNull() {

        k8sJobParams.put("param1", "string", null, null);

        Map<String, K8sJobParams.ParamValue> expectedResult = new HashMap<>();
        expectedResult.put("param1", new K8sJobParams.ParamValue("string", Collections.emptyList(), null));

        assertThat(k8sJobParams.getJobParams()).isEqualTo(expectedResult);
    }

    @Test
    public void testPut_AddsK8sJobParamWithEmptyValues_WhenValuesIsEmpty() {

        k8sJobParams.put("param1", "string", ImmutableList.of(), null);

        Map<String, K8sJobParams.ParamValue> expectedResult = new HashMap<>();
        expectedResult.put("param1", new K8sJobParams.ParamValue("string", Collections.emptyList(), null));

        assertThat(k8sJobParams.getJobParams()).isEqualTo(expectedResult);
    }

    @Test
    public void testPut_AddsK8sJobParamWithProvidedNameTypeAndValues () {
        k8sJobParams.put("param1", "string", Collections.singletonList("value1"), null);

        Map<String, K8sJobParams.ParamValue> expectedResult = new HashMap<>();
        expectedResult.put("param1", new K8sJobParams.ParamValue("string", Collections.singletonList("value1"), null));

        assertThat(k8sJobParams.getJobParams()).isEqualTo(expectedResult);
    }

    @Test
    public void testPut_AddsK8sJobParamWithOTHERType_WhenTypeIsNull() {
        k8sJobParams.put("param1", null, Collections.singletonList("value1"), null);

        Map<String, K8sJobParams.ParamValue> expectedResult = new HashMap<>();
        expectedResult.put("param1", new K8sJobParams.ParamValue("OTHER", Collections.singletonList("value1"), null));

        assertThat(k8sJobParams.getJobParams()).isEqualTo(expectedResult);
    }

    @Test
    public void testPut_AddsK8sJobParamWithOTHERType_WhenTypeIsEmpty() {
        k8sJobParams.put("param1", "", Collections.singletonList("value1"), null);

        Map<String, K8sJobParams.ParamValue> expectedResult = new HashMap<>();
        expectedResult.put("param1", new K8sJobParams.ParamValue("OTHER", Collections.singletonList("value1"), null));

        assertThat(k8sJobParams.getJobParams()).isEqualTo(expectedResult);
    }

    @Test
    public void testPut_AddsK8sJobParamWithNullSubsetting_WhenNullSubsettingIsProvided() {
        k8sJobParams.put("param1", "string", Collections.singletonList("value1"), null);

        Map<String, K8sJobParams.ParamValue> expectedResult = new HashMap<>();
        expectedResult.put("param1", new K8sJobParams.ParamValue("string", Collections.singletonList("value1"), null));

        assertThat(k8sJobParams.getJobParams()).isEqualTo(expectedResult);
    }

    @Test
    public void testPut_AddsK8sJobParamWithSubsetting_WhenSubsettingIsProvided() {
        Subsetting subsetting = Subsetting.builder()
                .aoi("aoi")
                .format("format")
                .build();
        k8sJobParams.put("param1", "string", Collections.singletonList("value1"), subsetting);

        Map<String, K8sJobParams.ParamValue> expectedResult = new HashMap<>();
        expectedResult.put("param1", new K8sJobParams.ParamValue("string", Collections.singletonList("value1"), subsetting));

        assertThat(k8sJobParams.getJobParams()).isEqualTo(expectedResult);
    }
}