package com.cgi.eoss.platform.core.processing.rpc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import com.cgi.eoss.platform.rpc.InputBinding;
import com.cgi.eoss.platform.rpc.JobParam;
import com.cgi.eoss.platform.rpc.OutputBinding;
import com.cgi.eoss.platform.rpc.Subsetting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.protobuf.Timestamp;

import lombok.experimental.UtilityClass;

/**
 * <p>
 * Utility class providing helper methods for dealing with Protobuf/Grpc services and objects.
 * </p>
 */
@UtilityClass
public class GrpcUtil {

    /**
     * <p>
     * Convert a gRPC parameters collection to a more convenient {@link Multimap}.
     * </p>
     *
     * @param params
     *            The parameters to be converted.
     * @return
     *         The params input collection mapped to &lt;String, String&gt; entries.
     */
    public static Multimap<String, String> paramsListToMap(List<JobParam> params) {
        ImmutableMultimap.Builder<String, String> mapBuilder = ImmutableMultimap.builder();
        params.forEach(p -> mapBuilder.putAll(p.getParamName(), p.getParamValueList()));
        return mapBuilder.build();
    }

    /**
     * <p>
     * Convert a gRPC parameters collection to a more convenient {@link ListMultimap}.
     * </p>
     *
     * @param params
     *            The parameters to be converted.
     * @return
     *         The params input collection mapped to &lt;String, String&gt; entries.
     */
    public static ListMultimap<String, String> paramsListToListMultimap(List<JobParam> params) {
        return params.stream().collect(Multimaps.flatteningToMultimap(JobParam::getParamName,
                sp -> sp.getParamValueList().stream(),
                MultimapBuilder.linkedHashKeys().arrayListValues()::build));
    }

    /**
     * <p>
     * Convert a {@link Multimap} into a collection of {@link JobParam}s for gRPC.
     * </p>
     *
     * @param params
     *            The parameters to be converted.
     * @return
     *         The params input collection mapped to {@link JobParam}s.
     */
    public static List<JobParam> mapToParams(Multimap<String, String> params) {
        ImmutableList.Builder<JobParam> paramsBuilder = ImmutableList.builder();
        params.keySet().forEach(
                k -> paramsBuilder.add(JobParam.newBuilder().setParamName(k).addAllParamValue(params.get(k)).build()));
        return paramsBuilder.build();
    }

    /**
     * Creates a list of {@link JobParam} objects from a multimap parameters and assigning
     * a specified type to each parameter.
     *
     * @param params a {@link Multimap} containing the parameters to map
     * @param type the type to be assigned to each {@link JobParam}, cannot be {@code null} nor empty
     * @return a list of {@link JobParam} objects, one for each key in the {@code params} multimap
     *
     * @throws IllegalArgumentException if {@code type} is {@code null} or empty
     */
    public static List<JobParam> createJobParams(Multimap<String, String> params, String type) {
        ImmutableList.Builder<JobParam> paramsBuilder = ImmutableList.builder();
        params.keySet().forEach(
              k -> paramsBuilder.add(createJobParam(k,params.get(k),type)));
        return paramsBuilder.build();
    }

    /**
     * Creates a {@link JobParam} object by converting a collection of parameter values
     * and associating them with a parameter name and type.
     *
     * @param paramName the name of the parameter
     * @param paramValues a collection of values for the parameter
     * @param type parameter types, must not be {@code null} or empty
     *
     * @return a {@link JobParam} object that contains the provided name, values, and types.
     *
     * @throws IllegalArgumentException if {@code type} is {@code null} or empty
     */
    public static JobParam createJobParam(String paramName, Collection<String> paramValues, String type) {
        return createJobParam(paramName, paramValues, type, null);
    }

    /**
     * Creates a {@link JobParam} object by converting a collection of parameter values
     * and associating them with a parameter name, type and optional binding details.
     *
     * @param paramName the name of the parameter
     * @param paramValues a collection of values for the parameter
     * @param type parameter types, must not be {@code null} or empty
     * @param binding input binding for the job parameter
     *
     * @return a {@link JobParam} object that contains the provided name, values, type and binding.
     *
     * @throws IllegalArgumentException if {@code type} is {@code null} or empty
     */
    public static JobParam createJobParam(String paramName, Collection<String> paramValues, String type, InputBinding binding) {
        return createJobParam(paramName, paramValues, type, binding, null);
    }

    /**
     * Creates a {@link JobParam} object by converting a collection of parameter values
     * and associating them with a parameter name, type and optional binding details.
     *
     * @param paramName the name of the parameter
     * @param paramValues a collection of values for the parameter
     * @param type parameter types, must not be {@code null} or empty
     * @param binding input binding for the job parameter
     * @param subsetting subsetting for the job parameter
     *
     * @return a {@link JobParam} object that contains the provided name, values, type, binding and subsetting.
     *
     * @throws IllegalArgumentException if {@code type} is {@code null} or empty
     */
    public static JobParam createJobParam(String paramName, Collection<String> paramValues, String type, InputBinding binding, Subsetting subsetting) {

        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type is null or empty");
        }

        JobParam.Builder builder = JobParam.newBuilder()
                .setParamName(paramName)
                .addAllParamValue(paramValues)
                .setType(type);

        if (binding != null) {
            builder.setInputBinding(binding);
        }

        if (subsetting != null) {
            builder.setSubsetting(subsetting);
        }

        return builder.build();
    }

    /**
     * <p>
     * Convert a {@link OffsetDateTime} into a {@link Timestamp} for gRPC.
     * </p>
     *
     * @param offsetDateTime
     *            The OffsetDateTime to be converted.
     * @return
     *         The offsetDateTime mapped to {@link Timestamp}.
     */
    public static Timestamp timestampFromOffsetDateTime(OffsetDateTime offsetDateTime) {
        Instant i = offsetDateTime.atZoneSameInstant(ZoneId.of("Z")).toInstant();
        return timestampFromInstant(i);
    }

    /**
     * <p>
     * Convert a {@link Timestamp} for gRPC into a {@link OffsetDateTime}.
     * </p>
     *
     * @param timestamp
     *            The Timestamp to be converted.
     * @return
     *         The Timestamp mapped to {@link OffsetDateTime}.
     */
    public static OffsetDateTime offsetDateTimeFromTimestamp(Timestamp timestamp) {
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return OffsetDateTime.ofInstant(instant, ZoneId.of("Z"));
    }

    /**
     * <p>
     * Convert a {@link Instant} into a {@link Timestamp} for gRPC.
     * </p>
     *
     * @param instant
     *            The Instant to be converted.
     * @return
     *         The instant mapped to {@link Timestamp}.
     */
    public static Timestamp timestampFromInstant(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    /**
     * Creates a job param with the purpose of being an output parameters.
     * @param name name of the output parameter.
     * @param type the type of the output parameter.
     * @param outputBinding output binding for the job parameter
     * @return a new JobParam with name and output binding set.
     */
    public static JobParam createOutputJobParam(String name, String type, OutputBinding outputBinding) {
        return JobParam.newBuilder()
                .setParamName(name)
                .setType(type)
                .setOutputBinding(outputBinding)
                .build();
    }
}
