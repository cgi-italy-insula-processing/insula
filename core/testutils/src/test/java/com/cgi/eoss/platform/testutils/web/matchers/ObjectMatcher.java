package com.cgi.eoss.platform.testutils.web.matchers;

/**
 * Interface that defines the method to check if two objects match.
 * The exact meaning of 'match' depend on the object being compared and is specified by
 * the implementations of this interface.
 *
 * @author cantaveneraf
 *
 * @param <T>
 *            The type of the objects to match
 */
public interface ObjectMatcher<T> {

    /**
     * Check if the two objects match.
     * A runtime exception will be thrown if the objects do not match
     *
     * @param first
     *            The first object
     * @param expected
     *            The second object
     */
    void match(T first, T second);
}
