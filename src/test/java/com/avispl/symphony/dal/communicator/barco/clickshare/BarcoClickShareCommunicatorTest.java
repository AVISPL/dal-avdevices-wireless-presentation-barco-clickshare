package com.avispl.symphony.dal.communicator.barco.clickshare;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BarcoClickShareCommunicatorTest {
    private static final String host = "***REMOVED***";
    private static final String protocol = "https";
    private static final String login = "admin";
    private static final String password = "12345";
    private static final int port = 4003;

    private BarcoClickShareCommunicator communicator;
    @BeforeEach
    public void setUp() throws Exception {
        communicator = new BarcoClickShareCommunicator();
        communicator.setHost(host);
        communicator.setPort(port);
        communicator.setLogin(login);
        communicator.setPassword(password);
        communicator.setProtocol(protocol);
        communicator.init();
    }

    @Test
    public void testStatistics() throws Exception {
        communicator.getMultipleStatistics();
    }
}
