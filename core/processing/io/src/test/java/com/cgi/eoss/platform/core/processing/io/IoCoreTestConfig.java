package com.cgi.eoss.platform.core.processing.io;

import com.cgi.eoss.platform.core.processing.io.download.Downloader;
import com.cgi.eoss.platform.core.processing.io.download.DownloaderFacade;
import com.cgi.eoss.platform.core.processing.io.download.SimpleDownloaderFacade;
import okhttp3.OkHttpClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Set;

@TestConfiguration
public class IoCoreTestConfig {

    @Bean
    public DownloaderFacade simpleDownloaderFacade(Set<Downloader> downloaders) {
        return new SimpleDownloaderFacade(downloaders);
    }

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient();
    }
}
