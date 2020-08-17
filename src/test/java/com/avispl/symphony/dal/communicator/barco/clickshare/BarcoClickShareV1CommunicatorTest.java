/*
 * Copyright (c) 2020 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.barco.clickshare;

import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BarcoClickShareV1CommunicatorTest {
    private static final String host = "73.89.148.164";
    private static final String protocol = "https";
    private static final String login = "integrator";
    private static final String password = "integrator";
    private static final int port = 4001;

    private BarcoClickShareCommunicator communicator;

    @BeforeEach
    public void setUp() throws Exception {
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
        Assert.assertEquals("1873200822", ((ExtendedStatistics)statistics.get(0)).getStatistics().get("Device Information#Serial Number"));
        Assert.assertEquals("CSE-200+", ((ExtendedStatistics)statistics.get(0)).getStatistics().get("Device Information#Model Name"));
        Assert.assertEquals(23, ((ExtendedStatistics)statistics.get(0)).getControllableProperties().size());
    }

    @Test
    public void testAirplayControl() throws Exception{
        communicator.getMultipleStatistics();
        ControllableProperty property = new ControllableProperty();
        property.setValue(1);
        property.setProperty("Features#Airplay");
        communicator.controlProperty(property);
    }
}
