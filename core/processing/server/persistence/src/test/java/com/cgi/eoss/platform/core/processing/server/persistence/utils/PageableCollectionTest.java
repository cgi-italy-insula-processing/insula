package com.cgi.eoss.platform.core.processing.server.persistence.utils;


import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PageableCollectionTest {

    @Test
    public void testConstructor_ThrowsIllegalArgumentException_WhenPageSizeIsZero() {
        List<String> values = Arrays.asList("key1", "key2");

        assertThatThrownBy(() -> new PageableCollection<>(values, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pageSize must be > 0");
    }

    @Test
    public void testConstructor_ThrowsIllegalArgumentException_WhenPageSizeIsNegative() {
        List<String> values = Arrays.asList("key1", "key2");

        assertThatThrownBy(() -> new PageableCollection<>(values, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("pageSize must be > 0");
    }

    @Test
    public void testConstructor_CreatesInstance_WhenPageSizeIsValid() {

        assertThat(new PageableCollection<>(new HashSet<>(Arrays.asList("key1", "key2")), 1)).isNotNull();
    }

    @Test
    public void testHasNext_ReturnsTrue_WhenCollectionHasElements() {
        List<String> values = Arrays.asList("key1", "key2");
        PageableCollection<String> pageableCollection = new PageableCollection<>(values, 1);

        assertThat(pageableCollection.hasNext()).isTrue();
    }

    @Test
    public void testHasNext_ReturnsFalse_WhenCollectionIsEmpty() {
        Set<String> values = Collections.emptySet();
        PageableCollection<String> pageableCollection = new PageableCollection<>(values, 1);

        assertThat(pageableCollection.hasNext()).isFalse();
    }

    @Test
    public void testHasNext_ReturnsFalse_WhenAllPagesAreConsumed() {
        List<String> values = Collections.singletonList("key1");
        PageableCollection<String> pageableCollection = new PageableCollection<>(values, 1);

        pageableCollection.next();

        assertThat(pageableCollection.hasNext()).isFalse();
    }

    @Test
    public void testNext_ReturnsNull_WhenNoMorePagesAvailable() {
        List<String> values = Collections.singletonList("key1");
        PageableCollection<String> pageableCollection = new PageableCollection<>(values, 1);

        pageableCollection.next();

        assertThat(pageableCollection.next()).isNull();
    }

    @Test
    public void testNext_ReturnsNull_WhenCollectionIsEmpty() {
        Set<String> values = Collections.emptySet();
        PageableCollection<String> pageableCollection = new PageableCollection<>(values, 1);

        assertThat(pageableCollection.next()).isNull();
    }

    @Test
    public void testNext_ReturnsFirstPage_WhenCalledFirstTime() {
        List<String> values = Arrays.asList("key1", "key2", "key3");
        PageableCollection<String> pageableCollection = new PageableCollection<>(values, 2);

        List<String> firstPage = pageableCollection.next();

        assertThat(firstPage).containsExactly("key1", "key2");
    }

    @Test
    public void testNext_ReturnsSecondPage_WhenCalledSecondTime() {
        List<String> orderedValues = Arrays.asList("key1", "key2", "key3");
        PageableCollection<String> pageableCollection = new PageableCollection<>(orderedValues, 2);

        pageableCollection.next();
        List<String> secondPage = pageableCollection.next();

        assertThat(secondPage).containsExactly("key3");
    }

    @Test
    public void testNext_ReturnsCompletePages_WhenPageSizeMatchesTotalElements() {
        List<String> values = Arrays.asList("key1", "key2");
        PageableCollection<String> pageableCollection = new PageableCollection<>(values, 2);

        List<String> page = pageableCollection.next();
        assertThat(page).containsExactly("key1", "key2");
        assertThat(pageableCollection.hasNext()).isFalse();
    }

    @Test
    public void testNext_ReturnsCompletePages_WhenPageSizeBiggerThanTotalElements() {
        List<String> values =Arrays.asList("key1", "key2");
        PageableCollection<String> pageableCollection = new PageableCollection<>(values, 3);

        List<String> page = pageableCollection.next();
        assertThat(page).containsExactly("key1", "key2");
        assertThat(pageableCollection.hasNext()).isFalse();
    }
}