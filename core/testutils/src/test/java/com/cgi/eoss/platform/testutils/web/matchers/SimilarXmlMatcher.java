package com.cgi.eoss.platform.testutils.web.matchers;

import com.cgi.eoss.platform.testutils.xml.XmlTestUtils;

/**
 * Implementation of the object matcher that check if two strings with XML content match,
 * ignoring the XML formatting characters
 *
 * @author cantaveneraf
 *
 */
public class SimilarXmlMatcher implements ObjectMatcher<String> {

    @Override
    public void match(String actual, String expected) {
        XmlTestUtils.isXmlSimilarTo(actual, expected);
    }

}
