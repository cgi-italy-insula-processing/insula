package com.cgi.eoss.platform.core.processing.server.model.converters;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceDescriptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.log4j.Log4j2;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.io.IOException;

@Converter
@Log4j2
public class PlatformServiceDescriptorYamlConverter implements AttributeConverter<PlatformServiceDescriptor, String> {

    private static final TypeReference<PlatformServiceDescriptor> PLATFORM_SERVICE_DESCRIPTOR = new TypeReference<PlatformServiceDescriptor>() { };

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    public PlatformServiceDescriptorYamlConverter() {
    }

    @Override
    public String convertToDatabaseColumn(PlatformServiceDescriptor attribute) {
        return toYaml(attribute);
    }

    @Override
    public PlatformServiceDescriptor convertToEntityAttribute(String dbData) {
        return fromYaml(dbData);
    }

    public static String toYaml(PlatformServiceDescriptor platformServiceDescriptor) {
        try {
            return MAPPER.writeValueAsString(platformServiceDescriptor);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to convert PlatformServiceDescriptor to YAML string: {}", platformServiceDescriptor);
            throw new IllegalArgumentException("Could not convert PlatformServiceDescriptor to YAML string", e);
        }
    }

    public static PlatformServiceDescriptor fromYaml(String yaml) {
        try {
            return MAPPER.readValue(yaml, PLATFORM_SERVICE_DESCRIPTOR);
        } catch (IOException e) {
            LOG.error("Failed to convert YAML string to PlatformServiceDescriptor: {}", yaml);
            throw new IllegalArgumentException("Could not convert YAML string to PlatformServiceDescriptor", e);
        }
    }

}

