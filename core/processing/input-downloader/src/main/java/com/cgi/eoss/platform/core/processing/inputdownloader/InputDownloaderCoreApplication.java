package com.cgi.eoss.platform.core.processing.inputdownloader;

import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Import;

@AllArgsConstructor
@SpringBootApplication
@Import(InputDownloaderCoreConfig.class)
@ConditionalOnExpression(
        "'${platform.core.processing.inputdownloader.autorun.enabled}' == 'true'" +
                " and '${platform.inputdownloader.app}' == 'processing-core'")
public class InputDownloaderCoreApplication implements CommandLineRunner {

    private final InputDownloaderRunner inputDownloaderRunner;

    public static void main(String[] args) {
        SpringApplication.run(InputDownloaderCoreApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        inputDownloaderRunner.run();
    }
}
