package com.cgi.eoss.platform.core.processing.server.model.converters;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDockerBuildInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import lombok.extern.log4j.Log4j2;
import java.io.IOException;

@Converter
@Log4j2
public class PlatformServiceDockerBuildInfoYamlConverter implements AttributeConverter<PlatformServiceDockerBuildInfo, String> {

    private static final TypeReference<PlatformServiceDockerBuildInfo> PLATFORM_SERVICE_DOCKER_BUILD_INFO = new TypeReference<PlatformServiceDockerBuildInfo>() { };

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    @Override
    public String convertToDatabaseColumn(PlatformServiceDockerBuildInfo attribute) {
        return toYaml(attribute);
    }

    @Override
    public PlatformServiceDockerBuildInfo convertToEntityAttribute(String dbData) {
        return dbData!=null?fromYaml(dbData):null;
    }

    public static String toYaml(PlatformServiceDockerBuildInfo platformServiceDockerBuildInfo) {
        try {
            return MAPPER.writeValueAsString(platformServiceDockerBuildInfo);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to convert PlatformServiceDockerBuildInfo to YAML string: {}", platformServiceDockerBuildInfo);
            throw new IllegalArgumentException("Could not convert PlatformServiceDockerBuildInfo to YAML string", e);
        }
    }

    public static PlatformServiceDockerBuildInfo fromYaml(String yaml) {
        try {
            return MAPPER.readValue(yaml, PLATFORM_SERVICE_DOCKER_BUILD_INFO);
        } catch (IOException e) {
            LOG.error("Failed to convert YAML string to PlatformServiceDockerBuildInfo: {}", yaml);
            throw new IllegalArgumentException("Could not convert YAML string to PlatformServiceDockerBuildInfo", e);
        }
    }
}
