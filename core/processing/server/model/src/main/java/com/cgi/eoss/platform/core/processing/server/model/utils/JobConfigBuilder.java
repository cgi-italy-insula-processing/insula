package com.cgi.eoss.platform.core.processing.server.model.utils;

import java.util.Optional;

import com.cgi.eoss.platform.core.processing.server.model.Job;
import com.cgi.eoss.platform.core.processing.server.model.JobConfig;
import com.cgi.eoss.platform.core.processing.server.model.PlatformService;
import com.cgi.eoss.platform.core.processing.server.model.User;
import com.google.common.base.Strings;
import com.google.common.collect.Multimap;

/**
 * Builder for {@link JobConfig} objects
 */
public final class JobConfigBuilder {

    private final User owner;
    private final PlatformService service;
    private String label;
    private String systematicParameter;
    private Job parentJob;
    private Multimap<String, String> inputs;

    /**
     * Creates a JobConfigBuilder initialized with an owner and a service
     *
     * @param owner the {@link User} owning this JobConfig
     * @param service the {@link PlatformService} related to this JobConfig
     */
    public JobConfigBuilder(User owner, PlatformService service) {
        this.owner = owner;
        this.service = service;
    }

    /**
     * Adds the provided label to this JobConfigBuilder object
     *
     * @param label the {@link JobConfig} label
     * @return this JobConfigBuilder
     */
    public JobConfigBuilder withLabel(String label){
        this.label = label;
        return this;
    }

    /**
     * Adds the provided systematicParameter to this JobConfigBuilder object
     *
     * @param systematicParameter the {@link JobConfig} systematicParameter
     * @return this JobConfigBuilder
     */
    public JobConfigBuilder withSystematicParameter(String systematicParameter){
        this.systematicParameter = systematicParameter;
        return this;
    }

    /**
     * Adds the provided parentJob to this JobConfigBuilder object
     *
     * @param parentJob the {@link JobConfig} parentJob
     * @return this JobConfigBuilder
     */
    public JobConfigBuilder withParentJob(Job parentJob){
        this.parentJob = parentJob;
        return this;
    }

    /**
     * Adds the provided inputs to this JobConfigBuilder object
     *
     * @param inputs the {@link JobConfig} inputs
     * @return this JobConfigBuilder
     */
    public JobConfigBuilder withInputs(Multimap<String, String> inputs){
        this.inputs = inputs;
        return this;
    }

    /**
     * Builds a {@link JobConfig} object initialized with the parameters held by this JobConfigBuilder
     * @return an initialized {@link JobConfig} object
     */
    public JobConfig build() {
        JobConfig config = new JobConfig(owner, service);
        config.setLabel(Strings.isNullOrEmpty(label) ? null : label);
        config.setInputs(inputs);
        Optional.ofNullable(parentJob).ifPresent(config::setParent);
        Optional.ofNullable(systematicParameter).ifPresent(config::setSystematicParameter);
        return config;
    }

}
