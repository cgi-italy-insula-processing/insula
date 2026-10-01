package com.cgi.eoss.platform.testutils.xml;

import static org.junit.Assert.assertThat;
import static org.xmlunit.matchers.CompareMatcher.isSimilarTo;

import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

/**
 * Class that exposes convenience methods to work with XML contents
 *
 * @author dirienzor
 * @author cantaveneraf
 *
 */
public final class XmlTestUtils {

    private XmlTestUtils() {
    }

    /**
     * Assert that the two XML stings have the same XML content ignoring the formatting
     *
     * @param actual
     *            The actual XML string
     * @param expected
     *            The expected XML string
     */
    public static void isXmlSimilarTo(String actual, String expected) {
        assertThat(actual, isSimilarTo(expected)
                .ignoreWhitespace()
                .normalizeWhitespace()
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndText)));
    }
}
