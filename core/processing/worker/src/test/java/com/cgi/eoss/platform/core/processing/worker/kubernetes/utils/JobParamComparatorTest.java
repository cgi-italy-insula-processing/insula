package com.cgi.eoss.platform.core.processing.worker.kubernetes.utils;


import com.cgi.eoss.platform.rpc.InputBinding;
import com.cgi.eoss.platform.rpc.JobParam;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JobParamComparatorTest {

    @Test
    public void testCompare_ReturnsMoreThanZero_WhenFirstParamHasBiggerPositionThanSecond() {
        JobParam firstParam = JobParam.newBuilder()
                .setParamName("test")
                .setInputBinding(InputBinding.newBuilder().setPosition(3).build())
                .build();

        JobParam secondParam = JobParam.newBuilder()
                .setParamName("test")
                .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                .build();

        JobParamComparator comparator = new JobParamComparator();

        assertThat(comparator.compare(firstParam, secondParam)).isEqualTo(2);
    }

    @Test
    public void testCompare_ReturnsLessThanZero_WhenFirstParamHasSmallerPositionThanSecond() {
        JobParam firstParam = JobParam.newBuilder()
                .setParamName("test")
                .setInputBinding(InputBinding.newBuilder().setPosition(0).build())
                .build();

        JobParam secondParam = JobParam.newBuilder()
                .setParamName("test")
                .setInputBinding(InputBinding.newBuilder().setPosition(3).build())
                .build();

        JobParamComparator comparator = new JobParamComparator();

        assertThat(comparator.compare(firstParam, secondParam)).isEqualTo(-3);
    }

    @Test
    public void testCompare_ReturnsLessThanZero_WhenPositionIsEqualAndFirstParamNameIsAlphabeticallyBeforeSecondParamName() {
        JobParam firstParam = JobParam.newBuilder()
                .setParamName("ast")
                .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                .build();

        JobParam secondParam = JobParam.newBuilder()
                .setParamName("test")
                .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                .build();

        JobParamComparator comparator = new JobParamComparator();

        assertThat(comparator.compare(firstParam, secondParam)).isLessThan(0);
    }

    @Test
    public void testCompare_ReturnsMoreThanZero_WhenPositionIsEqualAndFirstParamNameIsAlphabeticallyAfterSecondParamName() {
        JobParam firstParam = JobParam.newBuilder()
                .setParamName("test")
                .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                .build();

        JobParam secondParam = JobParam.newBuilder()
                .setParamName("best")
                .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                .build();

        JobParamComparator comparator = new JobParamComparator();

        assertThat(comparator.compare(firstParam, secondParam)).isGreaterThan(0);
    }

    @Test
    public void testCompare_ReturnsZero_WhenPositionIsEqualAndParamNamesAreEqual() {
        JobParam firstParam = JobParam.newBuilder()
                .setParamName("test")
                .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                .build();

        JobParam secondParam = JobParam.newBuilder()
                .setParamName("test")
                .setInputBinding(InputBinding.newBuilder().setPosition(1).build())
                .build();

        JobParamComparator comparator = new JobParamComparator();

        assertThat(comparator.compare(firstParam, secondParam)).isZero();
    }


}