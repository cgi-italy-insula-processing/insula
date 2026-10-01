package com.cgi.eoss.platform.core.processing.io.download.s3.config;


import org.junit.Before;
import org.junit.Test;

import java.net.URI;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class S3DownloaderCoreConfigurationTest {

    private S3DownloaderCoreConfiguration configuration;
    private S3Location locationOne;
    private S3AccountProperties account;
    private static final URI PATH_STYLE_URI = URI.create("http://localhost:9000/bucket");
    private static final URI VHOST_STYLE_URI = URI.create("http://bucket.localhost:9000");

    @Before
    public void init(){
        configuration = new S3DownloaderCoreConfiguration();
        {
            account = new S3AccountProperties();
            account.setAccessKey("accessKey");
            account.setPrivateKey("privateKey");

            {
                locationOne = new S3Location();
                locationOne.setBaseUrl(PATH_STYLE_URI);
                locationOne.setEndpoint("http://localhost:9000");
                locationOne.setRegion("italy");
                locationOne.setPathStyle(true);
                locationOne.setBucket("bucketOne");
                account.getLocations().add(locationOne);
            }
            {
                S3Location locationTwo = new S3Location();
                locationTwo.setBaseUrl(VHOST_STYLE_URI);
                locationTwo.setEndpoint("http://localhost:9000");
                locationTwo.setRegion("france");
                locationTwo.setPathStyle(false);
                locationTwo.setBucket("bucketTwo");
                account.getLocations().add(locationTwo);
            }
            configuration.getAccounts().add(account);
        }
    }

    @Test
    public void testConfigurationGetters()  {
        assertThat(configuration.getAccounts().size(), is(1));

        S3AccountProperties accountOne = configuration.getAccounts().get(0);
        assertThat(accountOne.getAccessKey(), is("accessKey"));
        assertThat(accountOne.getPrivateKey(), is("privateKey"));
        assertThat(accountOne.getConnectionTimeout(), is(10000));
        assertThat(accountOne.getSocketTimeout(), is(10000));
        assertThat(accountOne.getRetries(), is(0));
        assertThat(accountOne.getLocations().size(), is(2));

        {
            S3Location locationOne = accountOne.getLocations().get(0);
            assertThat(locationOne.getBaseUrl(), is(PATH_STYLE_URI));
            assertThat(locationOne.getBucket(), is("bucketOne"));
            assertThat(locationOne.getEndpoint(), is("http://localhost:9000"));
            assertThat(locationOne.getRegion(), is("italy"));
            assertThat(locationOne.isPathStyle(), is(true));
        }
        {
            S3Location locationTwo = accountOne.getLocations().get(1);
            assertThat(locationTwo.getBaseUrl(), is(VHOST_STYLE_URI));
            assertThat(locationTwo.getBucket(), is("bucketTwo"));
            assertThat(locationTwo.getEndpoint(), is("http://localhost:9000"));
            assertThat(locationTwo.getRegion(), is("france"));
            assertThat(locationTwo.isPathStyle(), is(false));
        }
    }

    @Test
    public void testGetPropertyByUri() {
        URI uri = URI.create("http://localhost:9000/bucket/key");
        S3AccountProperties properties = configuration.getAccountProperties(uri);

        assertThat(properties.getLocations().size(), is(2));

        assertThat(properties.getLocations().get(0).getBaseUrl(), is(PATH_STYLE_URI));
        assertThat(properties.getLocations().get(1).getBaseUrl(), is(VHOST_STYLE_URI));
    }

    @Test
    public void testGetPropertyNotFound() {
        URI uri = URI.create("http://localhost:9000/bucketX/key");

        try {
            configuration.getAccountProperties(uri);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Can't find S3 configuration for uri: " + uri.toASCIIString()));
        }
    }

    @Test
    public void testContainsLocation() {
        URI uri = URI.create("http://localhost:9000/bucket/key");
        assertThat(account.containsLocation(uri), is(true));
    }

    @Test
    public void testContainsLocationFalse() {
        URI uri = URI.create("http://localhost:9000/bucketX/key");
        assertThat(account.containsLocation(uri), is(false));
    }

    @Test
    public void testGetLocationByUri() {
        URI uri = URI.create("http://localhost:9000/bucket/key");
        assertThat(account.getLocation(uri).getBaseUrl(), is(PATH_STYLE_URI));
    }

    @Test
    public void testGetLocationByUriNotFound() {
        URI uri = URI.create("http://localhost:9000/bucketX/key");
        try {
            account.getLocation(uri);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Failed to retrieve S3 Location for uri: " + uri.toASCIIString()));
        }

    }

    @Test
    public void testBaseUrlMatches() {
        URI uri = URI.create("http://localhost:9000/bucket/key");
        assertThat(locationOne.baseUrlMatches(uri), is(true));
    }

    @Test
    public void testBaseUrlDoesNotMatch() {
        URI uri = URI.create("http://localhost:9000/bucketX/key");
        assertThat(locationOne.baseUrlMatches(uri), is(false));
    }

    @Test
    public void testExtractKeyFromMatchingBaseUrl() {
        URI uri = URI.create("http://localhost:9000/bucket/key");
        assertThat(locationOne.extractKey(uri), is("key"));
    }

    @Test
    public void testExtractNestedKeyFromMatchingBaseUrl() {
        URI uri = URI.create("http://localhost:9000/bucket/nested/key");
        assertThat(locationOne.extractKey(uri), is("nested/key"));
    }

    @Test
    public void testExtractEmptyKey() {
        URI uri = URI.create("http://localhost:9000/bucket/");
        assertThat(locationOne.extractKey(uri), is(""));
    }

    @Test
    public void testExtractKeyFromUriSubstringOfBaseUrl() {
        URI uri = URI.create("http://localhost:9000/buck");
        try {
            assertThat(locationOne.extractKey(uri), is(""));
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Can't extract s3 key from uri: " + uri.toASCIIString() + " because it does not match the base uri: " + PATH_STYLE_URI + "/"));
        }
    }

    @Test
    public void testExtractKeyFromNotMatchingBaseUrl() {
        URI uri = URI.create("http://localhost:9000/bucketX/key");
        try {
            locationOne.extractKey(uri);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Can't extract s3 key from uri: " + uri.toASCIIString() + " because it does not match the base uri: " + PATH_STYLE_URI + "/"));
        }
    }

    @Test
    public void testExtractKeyFromMatchingBaseUrlWithSlash() {
        S3Location location = new S3Location();
        location.setBaseUrl(URI.create(PATH_STYLE_URI.toASCIIString() + "/"));

        URI uri = URI.create("http://localhost:9000/bucket/key");
        assertThat(location.extractKey(uri), is("key"));

    }

    @Test
    public void testExtractNestedKeyFromMatchingBaseUrlWithSlash() {
        S3Location location = new S3Location();
        location.setBaseUrl(URI.create(PATH_STYLE_URI.toASCIIString() + "/"));

        URI uri = URI.create("http://localhost:9000/bucket/nested/key");
        assertThat(location.extractKey(uri), is("nested/key"));

    }

}
