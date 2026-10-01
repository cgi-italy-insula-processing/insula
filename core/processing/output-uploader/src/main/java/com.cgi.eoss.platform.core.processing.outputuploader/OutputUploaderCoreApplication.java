package com.cgi.eoss.platform.core.processing.outputuploader;

import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Import;

import java.io.IOException;

@AllArgsConstructor
@SpringBootApplication
@Import({OutputUploaderCoreConfig.class, OutputUploaderCoreDefaultConfig.class})
@ConditionalOnExpression(
        "'${platform.core.processing.outputuploader.autorun.enabled}' == 'true'" +
                " and '${platform.outputuploader.app}' == 'processing-core'")
public class OutputUploaderCoreApplication implements CommandLineRunner {

    private final OutputUploaderRunner outputUploaderRunner;

    @Override
    public void run(String... args) throws IOException {
        outputUploaderRunner.run();
        System.exit(0);
    }

    public static void main(String[] args) {
        SpringApplication.run(OutputUploaderCoreApplication.class, args);
    }

}
