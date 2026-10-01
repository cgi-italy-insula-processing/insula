package com.cgi.eoss.platform.core.processing.io.download.s3.config;

import com.cgi.eoss.platform.core.processing.io.IoCoreConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {IoCoreConfig.class})
@TestPropertySource(
        locations = {
                "classpath:test-core-io.properties"
        })
public class S3DownloaderCoreConfigurationIT {

    @Autowired
    private S3DownloaderCoreConfiguration s3DownloaderCoreConfiguration;

    @Value("${platform.io.downloader.port}")
    private String port;

    @Test
    public void testS3DownloaderCoreConfiguration_BindsExpectedProperties() {

        List<S3AccountProperties> accounts = s3DownloaderCoreConfiguration.getAccounts();
        assertThat(accounts).hasSize(1);

        S3AccountProperties account =  accounts.get(0);
        assertThat(account.getAccessKey()).isEqualTo("accessKey");
        assertThat(account.getPrivateKey()).isEqualTo("privateKey");

        List<S3Location> locations = account.getLocations();
        assertThat(locations).hasSize(1);

        S3Location location = locations.get(0);
        assertThat(location.getBaseUrl()).isEqualTo(URI.create("s3://bucket-01"));
        assertThat(location.getBucket()).isEqualTo("bucket-01");
        assertThat(location.isPathStyle()).isTrue();
        assertThat(location.getRegion()).isEqualTo("gra");
        assertThat(location.getEndpoint()).isEqualTo("http://localhost:" + port + "/s3Downloader/");
    }
}
