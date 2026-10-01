package com.cgi.eoss.platform.core.processing.server.model.converters;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
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
public class PlatformServiceResourcesYamlConverter implements AttributeConverter<PlatformServiceResources, String> {

    private static final TypeReference<PlatformServiceResources> PLATFORM_SERVICE_RESOURCES = new TypeReference<PlatformServiceResources>() { };

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    public PlatformServiceResourcesYamlConverter() {
    }

    @Override
    public String convertToDatabaseColumn(PlatformServiceResources attribute) {
        return toYaml(attribute);
    }

    @Override
    public PlatformServiceResources convertToEntityAttribute(String dbData) {
        return dbData!=null?fromYaml(dbData):null;
    }

    public static String toYaml(PlatformServiceResources platformServiceResources) {
        try {
            return MAPPER.writeValueAsString(platformServiceResources);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to convert PlatformServiceResources to YAML string: {}", platformServiceResources);
            throw new IllegalArgumentException("Could not convert PlatformServiceResources to YAML string", e);
        }
    }

    public static PlatformServiceResources fromYaml(String yaml) {
        try {
            return MAPPER.readValue(yaml, PLATFORM_SERVICE_RESOURCES);
        } catch (IOException e) {
            LOG.error("Failed to convert YAML string to PlatformServiceResources: {}", yaml);
            throw new IllegalArgumentException("Could not convert YAML string to PlatformServiceResources", e);
        }
    }

}

