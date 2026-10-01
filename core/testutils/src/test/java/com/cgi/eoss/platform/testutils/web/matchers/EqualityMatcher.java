package com.cgi.eoss.platform.testutils.web.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Implementation of the object matcher that check if two object match relying on the
 * Object.equals() method.
 *
 * @author cantaveneraf
 *
 * @param <T>
 *            The type of the objects to match
 */
public class EqualityMatcher<T> implements ObjectMatcher<T> {

    @Override
    public void match(T actual, T expected) {
        assertThat(actual, is(expected));
    }

}
