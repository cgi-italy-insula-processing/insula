package com.cgi.eoss.platform.core.processing.io;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = IoCoreConfig.class)
@EnableConfigurationProperties
public class IoCoreConfig {
}
