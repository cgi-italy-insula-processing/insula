package com.cgi.eoss.platform.core.processing.inputdownloader;

import com.cgi.eoss.platform.core.processing.io.download.Subsetting;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class K8SJobParamsTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new GuavaModule());

    @Test
    public void testGetDownloadableParams_WhenSubsettingIsNotProvidedInInputValue() {

        K8sJobParams k8sJobParams = createK8sJobParams(
                "{\"param1\":{\"type\":\"URL\",\"values\":[\"paramValue1\"]}}"
        );

        List<K8sJobParams.DownloadableParam> expected = new ArrayList<>();
        K8sJobParams.DownloadableParam expectedParam = K8sJobParams.DownloadableParam.builder()
                .paramName("param1")
                .values(Collections.singletonList("paramValue1"))
                .build();
        expected.add(expectedParam);

        assertThat(k8sJobParams.getDownloadableParams()).isEqualTo(expected);
    }

    @Test
    public void testGetDownloadableParams_WhenSubsettingIsProvidedInInputValue() {

        K8sJobParams k8sJobParams = createK8sJobParams(
                "{\"param1\":{\"type\":\"URL\",\"values\":[\"paramValue1\"],\"subsetting\":{\"aoi\":\"aoi\",\"format\":\"format\"}}}"
        );

        List<K8sJobParams.DownloadableParam> expected = new ArrayList<>();
        K8sJobParams.DownloadableParam expectedParam = K8sJobParams.DownloadableParam.builder()
                .paramName("param1")
                .values(Collections.singletonList("paramValue1"))
                .subsetting(Subsetting.builder()
                        .aoi("aoi")
                        .format("format")
                        .build())
                .build();
        expected.add(expectedParam);

        assertThat(k8sJobParams.getDownloadableParams()).isEqualTo(expected);
    }

    @Test
    public void testGetDownloadableParams_ReturnsAnEmptyList_WhenAllParametersAreNotDownloadable() {

        K8sJobParams k8sJobParams = createK8sJobParams(
                "{\"param1\":{\"type\":\"OTHER\",\"values\":[\"value1\"]}}"
        );

        assertThat(k8sJobParams.getDownloadableParams().isEmpty()).isTrue();
    }

    @Test
    public void testGetDownloadableParams_ReturnsOnlyTheDownloadableParameters_WhenParametersAreBothDownloadableAndNotDownloadable() {

        K8sJobParams k8sJobParams = createK8sJobParams(
                "{\"param1\": {\"type\": \"URL\", \"values\": [\"paramValue1\"]}, \"param2\": {\"type\": \"OTHER\", \"values\": [\"paramValue2\"]}, \"param3\":{\"type\":\"STAC\",\"values\":[\"paramValue3\"]}}"
        );

        List<K8sJobParams.DownloadableParam> expected = new ArrayList<>();
        K8sJobParams.DownloadableParam expectedParam = K8sJobParams.DownloadableParam.builder()
                .paramName("param1")
                .values(Collections.singletonList("paramValue1"))
                .build();
        expected.add(expectedParam);

        assertThat(k8sJobParams.getDownloadableParams()).isEqualTo(expected);
    }

    @Test
    public void testGetDownloadableParams_ReturnsAllParameters_WhenAllParametersAreDownloadable() {

        K8sJobParams k8sJobParams = createK8sJobParams(
                "{\"param1\": {\"type\": \"URL\", \"values\": [\"paramValue1_1\", \"paramValue1_2\"]}, \"param2\": {\"type\": \"URL\", \"values\": [\"paramValue2_1\", \"paramValue2_2\"]}}"
        );

        List<K8sJobParams.DownloadableParam> expected = new ArrayList<>();
        K8sJobParams.DownloadableParam expectedParam1 = K8sJobParams.DownloadableParam.builder()
                .paramName("param1")
                .values(Arrays.asList("paramValue1_1", "paramValue1_2"))
                .build();
        K8sJobParams.DownloadableParam expectedParam2 = K8sJobParams.DownloadableParam.builder()
                .paramName("param2")
                .values(Arrays.asList("paramValue2_1", "paramValue2_2"))
                .build();
        expected.add(expectedParam1);
        expected.add(expectedParam2);

        assertThat(k8sJobParams.getDownloadableParams()).isEqualTo(expected);
    }

    @Test
    public void testGetParams_ReturnsAllParameters_WhenParametersAreAllNotDownloadable() {

        K8sJobParams k8sJobParams = createK8sJobParams(
                "{\"param1\":{\"type\":\"OTHER\",\"values\":[\"paramValue1_1\",\"paramValue1_2\"]}}"
        );
        Multimap<String, String> params = k8sJobParams.getParams();
        Multimap<String, String> expected = ArrayListMultimap.create();
        expected.put("param1", "paramValue1_1");
        expected.put("param1", "paramValue1_2");


        assertThat(params).isEqualTo(expected);
    }

    @Test
    public void testGetParams_ReturnsAllParameters_WhenParametersAreBothDownloadableAndNotDownloadable() {

        K8sJobParams k8sJobParams = createK8sJobParams(
                "{\"param1\": {\"type\": \"BLABLA\", \"values\": [\"paramValue1\"]}, \"param2\": {\"type\": \"URL\", \"values\": [\"paramValue2\"]}, \"param3\":{\"type\":\"STAC\",\"values\":[\"paramValue3\"]}}"
        );
        Multimap<String, String> params = k8sJobParams.getParams();
        Multimap<String, String> expected = ArrayListMultimap.create();
        expected.put("param1", "paramValue1");
        expected.put("param2", "paramValue2");
        expected.put("param3", "paramValue3");

        assertThat(params).isEqualTo(expected);
    }

    @Test
    public void testK8sJobParamsConstructor_ThrowsIllegalArgumentException_WhenJsonIsNotValid() {
        
        assertThatThrownBy(()-> createK8sJobParams("{WRONG JSON}") )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("com.fasterxml.jackson.core.JsonParseException: Unexpected character");
    }

    @Test
    public void testGetStacParams_ReturnsOnlyStacParameters() {
        K8sJobParams k8sJobParams = createK8sJobParams(
                "{\"param1\":{\"type\":\"STAC\",\"values\":[\"paramValue1\"]}, \"param2\":{\"type\":\"URL\",\"values\":[\"paramValue2\"]}, \"param3\":{\"values\":[\"paramValue3\"]}}"
        );

        Multimap<String, String> params = k8sJobParams.getStacParams();
        Multimap<String, String> expected = ArrayListMultimap.create();
        expected.put("param1", "paramValue1");
        assertThat(params).isEqualTo(expected);
    }

    private static K8sJobParams createK8sJobParams(String input) {

        return new K8sJobParams(input, OBJECT_MAPPER);
    }

}