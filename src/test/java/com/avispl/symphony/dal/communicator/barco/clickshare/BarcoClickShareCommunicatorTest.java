/*
 * Copyright (c) 2020 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.barco.clickshare;

import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BarcoClickShareCommunicatorTest {
    private static final String host = "***REMOVED***";
    private static final String protocol = "https";
    private static final String login = "admin";
    private static final String password = "12345";
    private static final int port = 4003;

    private BarcoClickShareCommunicator communicator;

    @BeforeEach
    public void init() throws Exception {
        communicator = new BarcoClickShareCommunicator();
        communicator.setTrustAllCertificates(true);
        communicator.setHost(host);
        communicator.setPort(port);
        communicator.setLogin(login);
        communicator.setPassword(password);
        communicator.setProtocol(protocol);
        communicator.init();
    }

    @Test
    public void testStatistics() throws Exception {
        List<Statistics> statistics = communicator.getMultipleStatistics();
        Assert.assertNotNull(statistics.get(0));
        Assert.assertEquals(1, statistics.size());
        Assert.assertEquals("***REMOVED***", ((ExtendedStatistics)statistics.get(0)).getStatistics().get("Network configuration: wired#Configuration 1 Ip Address"));
        Assert.assertEquals("HDMI", ((ExtendedStatistics)statistics.get(0)).getStatistics().get("Audio Output"));
        Assert.assertEquals("ClickShare-1863550376", ((ExtendedStatistics)statistics.get(0)).getStatistics().get("Personalization#Meeting room name"));
        Assert.assertEquals("00:04:A5:01:04:78", ((ExtendedStatistics)statistics.get(0)).getStatistics().get("Network configuration: wired#Configuration 1 MAC Address"));
        Assert.assertEquals("R9861522EU", ((ExtendedStatistics)statistics.get(0)).getStatistics().get("Device information#Article number"));
        Assert.assertEquals("ClickShare-1863550376", ((ExtendedStatistics)statistics.get(0)).getStatistics().get("Network configuration#Hostname"));
        Assert.assertEquals(14, ((ExtendedStatistics)statistics.get(0)).getControllableProperties().size());
    }
}
