package com.cgi.eoss.platform.core.processing.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.cgi.eoss.platform.rpc.InputBinding;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.OutputBinding;
import com.cgi.eoss.platform.rpc.Subsetting;
import com.google.common.collect.*;
import org.junit.Test;

import com.google.protobuf.Timestamp;

public class GrpcUtilTest {

    @Test
    public void testParamsListToMap() {
        List<JobParam> jobParams = Arrays.asList(
                    JobParam.newBuilder().setParamName("paramOne").addParamValue("valueOneA").addParamValue("valueOneB").build(),
                    JobParam.newBuilder().setParamName("paramOne").addParamValue("valueOneC").addParamValue("valueOneC").build(),
                    JobParam.newBuilder().setParamName("paramTwo").addParamValue("valueTwo").build(),
                    JobParam.newBuilder().setParamName("paramTwo").addParamValue("valueTwo").build());

        Multimap<String, String> map = GrpcUtil.paramsListToMap(jobParams);

        assertThat(map.size()).isEqualTo(6);
        assertThat(map.get("paramOne")).isEqualTo(Arrays.asList("valueOneA", "valueOneB", "valueOneC", "valueOneC"));
        assertThat(map.get("paramTwo")).isEqualTo(Arrays.asList("valueTwo", "valueTwo"));
    }

    @Test
    public void testParamsListToListMultimap() {
        List<JobParam> jobParams = Arrays.asList(
                    JobParam.newBuilder().setParamName("paramOne").addParamValue("valueOneA").addParamValue("valueOneB").build(),
                    JobParam.newBuilder().setParamName("paramOne").addParamValue("valueOneC").addParamValue("valueOneC").build(),
                    JobParam.newBuilder().setParamName("paramTwo").addParamValue("valueTwo").build(),
                    JobParam.newBuilder().setParamName("paramTwo").addParamValue("valueTwo").build());

        ListMultimap<String, String> map = GrpcUtil.paramsListToListMultimap(jobParams);

        assertThat(map.size()).isEqualTo(6);
        assertThat(map.get("paramOne")).isEqualTo(Arrays.asList("valueOneA", "valueOneB", "valueOneC", "valueOneC"));
        assertThat(map.get("paramTwo")).isEqualTo(Arrays.asList("valueTwo", "valueTwo"));
    }

    @Test
    public void testMapToParams() {

        ImmutableMultimap<String, String> map = ImmutableMultimap.<String, String> builder()
                    .put("paramOne", "valueOneA")
                    .put("paramOne", "valueOneB")
                    .put("paramTwo", "valueTwo")
                    .put("paramTwo", "valueTwo")
                    .build();

        List<JobParam> jobParams = GrpcUtil.mapToParams(map);

        assertThat(jobParams).isEqualTo(Arrays.asList(
                    JobParam.newBuilder().setParamName("paramOne").addParamValue("valueOneA").addParamValue("valueOneB").build(),
                    JobParam.newBuilder().setParamName("paramTwo").addParamValue("valueTwo").addParamValue("valueTwo").build()));
    }

    @Test
    public void testCreateJobParam_MapsProvidedAttributesToJobParam() {

        JobParam jobParam = GrpcUtil.createJobParam("paramOne", ImmutableList.of("paramOneValue1"), "URL");
        assertThat(jobParam).isEqualTo(
                JobParam.newBuilder().setParamName("paramOne")
                        .addParamValue("paramOneValue1")
                        .setType("URL")
                        .build()
        );
    }

    @Test
    public void testCreateJobParam_ThrowsIllegalArgumentException_WhenTypeIsNull() {

        String paramName = "paramOne";
        ImmutableList<String> paramValues = ImmutableList.of("param1");

        assertThatThrownBy(
                ()->GrpcUtil.createJobParam(paramName, paramValues, null)
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(
                "type is null or empty"
        );
    }

    @Test
    public void testCreateJobParam_ThrowsIllegalArgumentException_WhenTypeIsEmpty() {

        String paramName = "paramOne";
        ImmutableList<String> paramValues = ImmutableList.of("param1");

        assertThatThrownBy(
                ()->GrpcUtil.createJobParam(paramName, paramValues, "")
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(
                "type is null or empty"
        );
    }

    @Test
    public void testCreateJobParams_MapsProvidedAttributesToJobParams(){

        ImmutableMultimap<String, String> map = ImmutableMultimap.<String, String> builder()
                .put("paramOne", "paramOneValue1")
                .build();

        List<JobParam> jobParams = GrpcUtil.createJobParams(map,"theType");

        assertThat(jobParams).isEqualTo(Collections.singletonList(
                JobParam.newBuilder().setType("theType").setParamName("paramOne").addParamValue("paramOneValue1").build()));
    }


    @Test
    public void testCreateJobParams_ThrowsIllegalArgumentException_WhenTypeIsNull() {

        ImmutableMultimap<String, String> map = ImmutableMultimap.<String, String> builder()
                .put("paramOne", "valueOneA")
                .build();

        assertThatThrownBy(
                ()->GrpcUtil.createJobParams(map,null)
        ).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type is null or empty");
    }


    @Test
    public void testCreateJobParams_ThrowsIllegalArgumentException_WhenTypeIsEmpty() {

        ImmutableMultimap<String, String> map = ImmutableMultimap.<String, String> builder()
                .put("paramOne", "valueOneA")
                .build();

        assertThatThrownBy(
                ()->GrpcUtil.createJobParams(map,"")
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type is null or empty");
    }


    @Test
    public void testTimestampFromOffsetDateTime() {

        OffsetDateTime offsetDateTime = OffsetDateTime.of(2023, 5, 20, 10, 20, 30, 146, ZoneOffset.UTC);
        Timestamp timestamp = GrpcUtil.timestampFromOffsetDateTime(offsetDateTime);

        assertThat(timestamp.getSeconds()).isEqualTo(1684578030L);
        assertThat(timestamp.getNanos()).isEqualTo(146);
    }

    @Test
    public void testTimestampFromInstant() {

        Instant instant = OffsetDateTime.of(2023, 5, 20, 10, 20, 30, 146, ZoneOffset.UTC).toInstant();
        Timestamp timestamp = GrpcUtil.timestampFromInstant(instant);

        assertThat(timestamp.getSeconds()).isEqualTo(1684578030L);
        assertThat(timestamp.getNanos()).isEqualTo(146);
    }

    @Test
    public void testOffsetDateTimeFromTimestamp() {

        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1684578030L).setNanos(146).build();
        OffsetDateTime offsetDateTime = GrpcUtil.offsetDateTimeFromTimestamp(timestamp);

        assertThat(offsetDateTime).isEqualTo(OffsetDateTime.of(2023, 5, 20, 10, 20, 30, 146, ZoneOffset.UTC));
    }

    @Test
    public void testCreateJobParams_MapsAttributesIncludingInputBindingToJobParam() {
        InputBinding binding = InputBinding.newBuilder().setPosition(1).build();
        JobParam jobParam = GrpcUtil.createJobParam("paramOne", ImmutableList.of("paramOneValue1"), "URL", binding);
        assertThat(jobParam).isEqualTo(
                JobParam.newBuilder().setParamName("paramOne")
                        .addParamValue("paramOneValue1")
                        .setType("URL")
                        .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                        .build()
        );
    }

    @Test
    public void testCreateJobParams_MapsAttributesWithoutInputBinding_WhenInputBindingIsNull() {
        JobParam jobParam = GrpcUtil.createJobParam("paramOne", ImmutableList.of("paramOneValue1"), "URL", null);
        assertThat(jobParam).isEqualTo(
                JobParam.newBuilder().setParamName("paramOne")
                        .addParamValue("paramOneValue1")
                        .setType("URL")
                        .build()
        );
    }

    @Test
    public void testCreateJobParams_MapsAttributesWithoutSubsetting_WhenSubsettingIsNull() {
        InputBinding binding = InputBinding.newBuilder().setPosition(1).build();
        JobParam jobParam = GrpcUtil.createJobParam("paramOne", ImmutableList.of("paramOneValue1"), "URL", binding, null);
        assertThat(jobParam).isEqualTo(
                JobParam.newBuilder().setParamName("paramOne")
                        .addParamValue("paramOneValue1")
                        .setType("URL")
                        .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                        .build()
        );
    }

    @Test
    public void testCreateJobParams_MapsAttributesIncludingSubsettingToJobParam_WhenSubsettingIsProvided() {
        InputBinding binding = InputBinding.newBuilder().setPosition(1).build();
        Subsetting subsetting = Subsetting.newBuilder().setAoi("aoi").setFormat("format").build();
        JobParam jobParam = GrpcUtil.createJobParam("paramOne", ImmutableList.of("paramOneValue1"), "URL", binding, subsetting);
        assertThat(jobParam).isEqualTo(
                JobParam.newBuilder().setParamName("paramOne")
                        .addParamValue("paramOneValue1")
                        .setType("URL")
                        .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                        .setSubsetting(Subsetting.newBuilder().setAoi("aoi").setFormat("format").build())
                        .build()
        );
    }

    @Test
    public void testCreateOutputJobParam_MapsProvidedAttributesToJobParam() {
        JobParam jobParam = GrpcUtil.createOutputJobParam("paramOne",
                "STAC",
                OutputBinding.newBuilder().setGlob("globValue").build());
        assertThat(jobParam).isEqualTo(
                JobParam.newBuilder()
                        .setParamName("paramOne")
                        .setType("STAC")
                        .setOutputBinding(OutputBinding.newBuilder().setGlob("globValue").build())
                        .build()
        );
    }

    @Test
    public void testCreateOutputJobParam_ThrowsNullPointerException_WhenOutputBindingIsNull() {
        assertThatThrownBy(
                () -> GrpcUtil.createOutputJobParam("paramName","STAC", null)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testCreateOutputJobParam_ThrowsNullPointerException_WhenTypeIsNull() {
        assertThatThrownBy(
                () -> GrpcUtil.createOutputJobParam("paramName",null, OutputBinding.newBuilder().setGlob("globValue").build())
        ).isInstanceOf(NullPointerException.class);
    }
}
