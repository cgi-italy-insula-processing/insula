package com.cgi.eoss.platform.core.processing.server.model;

import com.cgi.eoss.platform.core.processing.server.model.converters.PlatformServiceDescriptorYamlConverter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
/**
 * <p>The detailed service configuration required to complete a service definition file.</p>
 * <p>Originally, these fields were chosen because they were broadly aligned with the official WPS spec.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatformServiceDescriptor {

    private String id;

    private String title;

    private String description;

    private String version;

    private String port;

    private Long groupId;

    private boolean storeSupported;

    private boolean statusSupported;

    private String serviceType;

    private String serviceProvider;

    private List<Parameter> dataInputs;

    private List<Parameter> dataOutputs;

    private List<String> metadata;

    private String dockerCommand;

    private List<String> dockerArguments;

    private String parallelInputsKey;

    @JsonInclude(Include.NON_NULL)
    private Map<String, String> environmentVariables;

    public String toYaml() {
        return PlatformServiceDescriptorYamlConverter.toYaml(this);
    }

    public static PlatformServiceDescriptor fromYaml(String yaml) {
        return PlatformServiceDescriptorYamlConverter.fromYaml(yaml);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Parameter {

        public enum DataNodeType {
            LITERAL, COMPLEX, BOUNDING_BOX
        }

        private String id;

        private String title;

        private String description;

        private int minOccurs;

        private int maxOccurs;

        private DataNodeType data;

        @JsonInclude(Include.NON_NULL)
        private String timeRegexp;
        
        private Map<String, String> defaultAttrs;

        @Singular
        private List<Map<String, String>> supportedAttrs;
        
        @JsonInclude(Include.NON_NULL)
        private List<Relation> parameterRelations;
        
        @JsonInclude(Include.NON_NULL)
        private Map<String, String> platformMetadata;

        @JsonInclude(Include.NON_NULL)
        private Subsetting subsetting;

        @JsonInclude(Include.NON_NULL)
        private InputBinding inputBinding;

        @JsonInclude(Include.NON_NULL)
        private OutputBinding outputBinding;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Relation {
    	
    	public enum RelationType {
            VISUALIZATION_OF
        }
        private String targetParameterId;

        private RelationType type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InputBinding {

        private Integer position;

        @JsonInclude(Include.NON_NULL)
        private String prefix;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputBinding {
        private String glob;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subsetting {

        private String aoiInputRef;

        private String format;
    }
}
