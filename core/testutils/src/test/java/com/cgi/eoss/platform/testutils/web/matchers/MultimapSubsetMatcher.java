package com.cgi.eoss.platform.testutils.web.matchers;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

/**
 * Implementation of the object matcher that verifies if two MultiMaps match by checking
 * whether the 'expected' Map is a sub-set of the 'actual' one: in order to match all keys
 * and values from 'expected' must be present in 'actual', but the opposite could not be true.
 *
 *
 * @author cantaveneraf
 *
 * @param <K>
 *            The type of the keys of the multimap
 * @param <V>
 *            The type of the values of the multimap
 */
public class MultimapSubsetMatcher<K, V> implements ObjectMatcher<Map<K, List<V>>> {

    @Override
    public void match(Map<K, List<V>> actual, Map<K, List<V>> expected) {
        for (Object key : expected.keySet()) {
            assertTrue(actual.containsKey(key));
            assertTrue(actual.get(key).containsAll(expected.get(key)));
        }
    }

}
