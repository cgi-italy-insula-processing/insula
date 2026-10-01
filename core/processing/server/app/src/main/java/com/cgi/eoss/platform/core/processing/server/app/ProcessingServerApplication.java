package com.cgi.eoss.platform.core.processing.server.app;

import com.cgi.eoss.platform.core.processing.server.api.ApiConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * The main entry point for the Platform Server Core application.
 */
@Import({
        ApiConfig.class
})
@SpringBootApplication
public class ProcessingServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessingServerApplication.class, args);
    }

}
