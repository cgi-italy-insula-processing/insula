package com.cgi.eoss.platform.testutils.junit.rules;

import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * JUnit rule to automatically check on test completion with success that all
 * interactions with the registered mocks have been verified.
 *
 * @author cantaveneraf
 *
 */
public class VerifyNoMoreInteractionsRule extends TestWatcher {

    private final Set<Object> mocks = new HashSet<>();

    /**
     * Add the provided mock to the list of registered mocks
     *
     * @param newMock
     *            The mock to register
     */
    public void add(Object newMock) {
        mocks.add(newMock);
    }

    /**
     * Add the provided mocks to the list of registered mocks
     *
     * @param newMocks
     *            The mocks to register
     */
    public void addAll(Collection<Object> newMocks) {
        mocks.addAll(newMocks);
    }

    @Override
    protected void succeeded(Description description) {
        verifyNoMoreInteractions(mocks.toArray());
    }

}
