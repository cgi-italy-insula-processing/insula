package com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.util;

import com.cgi.eoss.platform.core.processing.server.model.PlatformServiceResources;
import com.cgi.eoss.platform.core.processing.server.orchestrator.service.job.resource.util.ResourceUtils;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResourceUtilsTest {

    @Test
    public void testGetServiceStorageInMb_IgnoresWhitespace_WhenValueContainsLeadingAndTrailingSpaces() {
        assertThat(ResourceUtils.getServiceStorageInMb(PlatformServiceResources.builder().storage("    1    ").build())).isEqualTo(1024L);
    }

    @Test
    public void testGetServiceStorageInMb_ReturnsZero_WhenValueIsZero() {
        assertThat(ResourceUtils.getServiceStorageInMb(PlatformServiceResources.builder().storage("0").build())).isEqualTo(0L);
    }

    @Test
    public void testGetServiceStorageInMb_ThrowsIllegalArgumentException_WhenValueIsNegative() {
        assertThatThrownBy(() -> ResourceUtils.getServiceStorageInMb(PlatformServiceResources.builder().storage("-1Mi").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid storage value - must be a positive integer without units: -1Mi");
    }

    @Test
    public void testGetServiceStorageInMb_ReturnsNull_WhenValueIsNull() {
        assertThat(ResourceUtils.getServiceStorageInMb(null)).isNull();
    }

    @Test
    public void testGetServiceStorageInMb_ThrowsIllegalArgumentException_WhenValueIsDecimal() {
        assertThatThrownBy(() -> ResourceUtils.getServiceStorageInMb(PlatformServiceResources.builder().storage("1.5").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid storage value - must be a positive integer without units: 1.5");
    }

    @Test
    public void testGetServiceStorageInMb_ThrowsIllegalArgumentException_WhenValueIsNonNumeric() {
        assertThatThrownBy(() -> ResourceUtils.getServiceStorageInMb(PlatformServiceResources.builder().storage("abc").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid storage value - must be a positive integer without units: abc");
    }

    @Test
    public void testGetServiceRamInMb_ReturnsValueInMb_WhenValueIsInGi() {
        assertThat(ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("2Gi").build())).isEqualTo(2048L);
    }

    @Test
    public void testGetServiceRamInMb_ReturnsValueInMb_WhenValueIsInMi() {
        assertThat(ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("512Mi").build())).isEqualTo(512L);
    }

    @Test
    public void testGetServiceRamInMb_ReturnsRawValue_WhenValueHasNoUnit() {
        assertThat(ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("1024Mi").build())).isEqualTo(1024L);
    }

    @Test
    public void testGetServiceRamInMb_IsCaseInsensitive_WhenUnitIsLowerCase() {
        assertThat(ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("2Gi").build())).isEqualTo(2048L);
    }

    @Test
    public void testGetServiceRamInMb_IgnoresWhitespace_WhenValueContainsLeadingAndTrailingSpaces() {
        assertThat(ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("    2Gi    ").build())).isEqualTo(2048L);
    }

    @Test
    public void testGetServiceRamInMb_ReturnsZero_WhenValueIsZero() {
        assertThat(ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("0Gi").build())).isEqualTo(0L);
    }

    @Test
    public void testGetServiceRamInMb_ThrowsIllegalArgumentException_WhenValueIsNegative() {
        assertThatThrownBy(() -> ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("-1Gi").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RAM value is invalid: must be a positive integer with 'Mi' or 'Gi' unit: -1Gi");
    }

    @Test
    public void testGetServiceRamInMb_ReturnsNull_WhenValueIsNull() {
        assertThat(ResourceUtils.getServiceRamInMb(null)).isNull();
    }

    @Test
    public void testGetServiceRamInMb_ThrowsIllegalArgumentException_WhenUnitIsUnsupported() {
        assertThatThrownBy(() -> ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("2GB").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RAM value is invalid: must be a positive integer with 'Mi' or 'Gi' unit: 2GB");
    }

    @Test
    public void testGetServiceRamInMb_ThrowsIllegalArgumentException_WhenValueIsDecimal() {
        assertThatThrownBy(() -> ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("1.5Gi").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RAM value is invalid: must be a positive integer with 'Mi' or 'Gi' unit: 1.5Gi");
    }

    @Test
    public void testGetServiceRamInMb_ThrowsIllegalArgumentException_WhenValueIsNonNumeric() {
        assertThatThrownBy(() -> ResourceUtils.getServiceRamInMb(PlatformServiceResources.builder().ram("abc").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RAM value is invalid: must be a positive integer with 'Mi' or 'Gi' unit: abc");
    }
    
    @Test
    public void testGetServiceGPUs_IgnoresWhitespace_WhenValueContainsLeadingAndTrailingSpaces() {
        assertThat(ResourceUtils.getServiceGPUs(PlatformServiceResources.builder().gpus("    1    ").build())).isEqualTo(1L);
    }

    @Test
    public void testGetServiceGPUs_ReturnsZero_WhenValueIsZero() {
        assertThat(ResourceUtils.getServiceGPUs(PlatformServiceResources.builder().gpus("0").build())).isEqualTo(0L);
    }

    @Test
    public void testGetServiceGPUs_ThrowsIllegalArgumentException_WhenValueIsNegative() {
        assertThatThrownBy(() -> ResourceUtils.getServiceGPUs(PlatformServiceResources.builder().gpus("-1Gpus").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid GPUs value - must be a positive integer without units: -1Gpus");
    }

    @Test
    public void testGetServiceGPUs_ReturnsNull_WhenValueIsNull() {
        assertThat(ResourceUtils.getServiceGPUs(null)).isNull();
    }
 
    @Test
    public void testGetServiceGPUs_ThrowsIllegalArgumentException_WhenValueIsDecimal() {
        assertThatThrownBy(() -> ResourceUtils.getServiceGPUs(PlatformServiceResources.builder().gpus("1.5").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid GPUs value - must be a positive integer without units: 1.5");
    }

    @Test
    public void testGetServiceGPUs_ThrowsIllegalArgumentException_WhenValueIsNonNumeric() {
        assertThatThrownBy(() -> ResourceUtils.getServiceGPUs(PlatformServiceResources.builder().gpus("abc").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid GPUs value - must be a positive integer without units: abc");
    }


}
