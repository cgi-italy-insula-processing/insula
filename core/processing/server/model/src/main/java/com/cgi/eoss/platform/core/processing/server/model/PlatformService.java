package com.cgi.eoss.platform.core.processing.server.model;

import com.cgi.eoss.platform.core.processing.server.model.converters.PlatformServiceDescriptorYamlConverter;
import com.cgi.eoss.platform.core.processing.server.model.converters.PlatformServiceDockerBuildInfoYamlConverter;
import com.cgi.eoss.platform.core.processing.server.model.converters.PlatformServiceResourcesYamlConverter;
import com.cgi.eoss.platform.core.processing.server.model.converters.UriStringConverter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ComparisonChain;

import javax.persistence.*;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode(exclude = {"id", "contextFiles"})
@ToString(exclude = {"serviceDescriptor", "contextFiles"})
@Table(name = "services",
        indexes = {@Index(name = "services_name_idx", columnList = "name"), @Index(name = "services_owner_idx", columnList = "owner")},
        uniqueConstraints = {@UniqueConstraint(columnNames = "name")})
@NoArgsConstructor
@Entity
public class PlatformService implements PlatformEntityWithOwner<PlatformService>, Searchable {

    public static final String DEFAULT_SERVICE_PORT = "8080/tcp";

    public static final Long DEFAULT_GROUP_ID = 0L;

    private static final String DATA_SOURCE_NAME_PREFIX = "PLATFORM_SERVICE_";

    /**
     * <p>Internal unique identifier of the service.</p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * <p>Unique name of the service, assigned by the owner.</p>
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * <p>Human-readable descriptive summary of the service.</p>
     */
    @Column(name = "description")
    private String description;

    /**
     * <p>The type of the service, e.g. 'processor' or 'application'.</p>
     */
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Type type = Type.PROCESSOR;

    /**
     * <p>The user owning the service, typically the service creator.</p>
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner", nullable = false)
    private User owner;

    /**
     * <p>The docker container identifier to be used for running the service. It is expected that this is already
     * available on a worker.</p>
     */
    @Column(name = "docker_tag", nullable = false)
    private String dockerTag;
    
    /**
     * <p>If the proxy path has to be stripped prior to be forwarded to this service. </p>
     */
    @Column(name = "strip_proxy_path")
    private boolean stripProxyPath = true;

    /**
     * <p>Usage restriction of the service, e.g. 'open' or 'restricted'.</p>
     */
    @Column(name = "licence", nullable = false)
    @Enumerated(EnumType.STRING)
    private Licence licence = Licence.OPEN;

    /**
     * <p>Service availability status.</p>
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.IN_DEVELOPMENT;

    /**
     * <p>The full definition of the service.</p>
     */
    @Convert(converter = PlatformServiceDescriptorYamlConverter.class)
    @Column(name = "service_descriptor")
    private PlatformServiceDescriptor serviceDescriptor;

    /**
     * <p>The files required to build this service's docker image.</p>
     */
    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<PlatformServiceContextFile> contextFiles = new HashSet<>();

    
    /**
     * <p>The location of the service Files, e.g. Git repository</p>
     */
    @Column(name = "service_files_uri", nullable = true)
    @Convert(converter = UriStringConverter.class)
    private URI serviceFilesUri;

    
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name="user_mount_id", table = "services_mounts")
    @Column(name="target_mount_path", table = "services_mounts")
    @CollectionTable(name="services_mounts", joinColumns=@JoinColumn(name="service_id"))
    private Map<Long, String> additionalMounts = new HashMap<>();
    
    @Convert(converter = PlatformServiceDockerBuildInfoYamlConverter.class)
    @Column(name = "docker_build_info")
    PlatformServiceDockerBuildInfo dockerBuildInfo;
    
    @Convert(converter = PlatformServiceResourcesYamlConverter.class)
    @Column(name = "required_resources")
    PlatformServiceResources requiredResources;
    
    @Column(name = "external_uri")
    @Convert(converter = UriStringConverter.class)
    private URI externalServiceUri;
    
    /**
     * <p>Application port to access application</p>
     */
    @Column(name = "port")
    private String port = DEFAULT_SERVICE_PORT;

    /**
     * <p>Group ID used to run the application</p>
     */
    @Column(name = "group_id")
    private Long groupId = DEFAULT_GROUP_ID;

    /**
     * <p>The Common Workflow Language describing the service.</p>
     */
    @Embedded
    @AttributeOverrides({@AttributeOverride(name="url", column=@Column(name="cwl_url")),
            @AttributeOverride(name="document", column=@Column(name="cwl_document"))})
    private Cwl cwl;
    
    /**
     * <p>Create a new Service with the minimum required parameters.</p>
     *
     * @param name Name of the service.
     * @param owner The user owning the service.
     */
    public PlatformService(String name, User owner, String dockerTag) {
        this.name = name;
        this.owner = owner;
        this.dockerTag = dockerTag;
    }

    @Override
    public int compareTo(PlatformService o) {
        return ComparisonChain.start().compare(name, o.name).result();
    }

    public void setContextFiles(Set<PlatformServiceContextFile> contextFiles) {
        contextFiles.forEach(f -> f.setService(this));
        this.contextFiles = contextFiles;
    }

    public String getDataSourceName() {
        return DATA_SOURCE_NAME_PREFIX + this.name;
    }

    public enum Type {
        PROCESSOR, BULK_PROCESSOR, APPLICATION, PARALLEL_PROCESSOR
    }

    public enum Status {
        IN_DEVELOPMENT, AVAILABLE, DISABLED
    }

    public enum Licence {
        OPEN, RESTRICTED
    }

}
