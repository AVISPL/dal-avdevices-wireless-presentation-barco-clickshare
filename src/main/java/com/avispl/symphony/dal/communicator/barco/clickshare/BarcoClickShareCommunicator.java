/*
 * Copyright (c) 2020 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.barco.clickshare;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.http.entity.ContentType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.avispl.symphony.dal.communicator.barco.clickshare.BarcoClickShareProperties.*;

/**
 * An implementation of RestCommunicator to provide communication and interaction with Barco ClickShare devices.
 * Supported features are:
 *      Monitoring:
 *          - Audio mode
 *          - Video mode
 *          - General device information
 *          - Features (miracast, googlecast, airplay, blackboard)
 *          - Network settings
 *          - Personalization
 *          - Power management
 *      Control:
 *          - Audio mode
 *          - Video mode
 *          - Features (miracast, googlecast, airplay, blackboard)
 *          - Personalization
 *          - Power management
 *          - Reboot
 *          - Standby
 * @author Maksym.Rossiytsev
 * @version 1.0
 * */
public class BarcoClickShareCommunicator extends RestCommunicator implements Monitorable, Controller {

    private String encodedCreds;
    ExtendedStatistics localStatistics;
    private final ReentrantLock controlOperationsLock = new ReentrantLock();

    private long latestControlTimestamp;
    private long controlsCooldownTimeout = 3000;

    public BarcoClickShareCommunicator(){
        super();
        setBaseUri("/v2/");
    }

    @Override
    protected void internalInit() throws Exception {
        super.internalInit();
        encodedCreds = Base64.getEncoder().encodeToString(String.format("%s:%s", getLogin(), getPassword()).getBytes());
    }

    @Override
    public void controlProperty(ControllableProperty controllableProperty) throws Exception {
        String property = controllableProperty.getProperty();
        String value = String.valueOf(controllableProperty.getValue());

        controlOperationsLock.lock();
        try{
            switch (property){
                case POWER_MODE_NAME:
                    patchDeviceMode(POWER_MANAGEMENT,"powerMode", value, property);
                    break;
                case POWER_STATUS_NAME:
                    patchDeviceMode(POWER_MANAGEMENT, "status", value, property);
                    break;
                case STANDBY_TIMEOUT_NAME:
                    patchDeviceMode(POWER_MANAGEMENT,"standbyTimeout", value, property);
                    break;
                case VIDEO_MODE_NAME:
                    patchDeviceMode(CONFIGURATION_VIDEO,"mode", value, property);
                    break;
                case MIRACAST_NAME:
                    patchDeviceMode(FEATURES_MIRACAST, "enabled", "0".equals(value) ? "false" : "true", property);
                    break;
                case GOOGLECAST_NAME:
                    patchDeviceMode(FEATURES_GOOGLECAST, "enabled", "0".equals(value) ? "false" : "true", property);
                    break;
                case AIRPLAY_NAME:
                    patchDeviceMode(FEATURES_AIRPLAY, "enabled", "0".equals(value) ? "false" : "true", property);
                    break;
                case BLACKBOARD_SAVING_NAME:
                    patchDeviceMode(FEATURES_BLACKBOARD, "savingEnabled", "0".equals(value) ? "false" : "true", property);
                    break;
                case AUDIO_NAME:
                    patchDeviceMode(CONFIGURATION_AUDIO, "enabled", "0".equals(value) ? "false" : "true", property);
                    break;
                case AUDIO_OUTPUT_NAME:
                    patchDeviceMode(CONFIGURATION_AUDIO, "output", value, property);
                    break;
                case LANGUAGE_NAME:
                    patchDeviceMode(PERSONALIZATION, "language", value, property);
                    break;
                case WELCOME_MESSAGE_NAME:
                    patchDeviceMode(PERSONALIZATION, "welcomeMessage", value, property);
                    break;
                case REBOOT_NAME:
                    requestReboot();
                    break;
                case STANDBY_NAME:
                    requestStandby();
                    break;
                default:
                    if(logger.isWarnEnabled()){
                        logger.warn(String.format("Operation %s with value %s is not supported.", property, value));
                    }
                    break;
            }
        } finally {
            controlOperationsLock.unlock();
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        if (CollectionUtils.isEmpty(list)) {
            throw new IllegalArgumentException("Controllable properties cannot be null or empty");
        }

        for(ControllableProperty controllableProperty: list){
            controlProperty(controllableProperty);
        }
    }

    @Override
    protected void authenticate() throws Exception {
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extendedStatistics = new ExtendedStatistics();

        if(isValidControlCoolDown() && localStatistics != null){
            if (logger.isDebugEnabled()) {
                logger.debug("Device is occupied. Skipping statistics refresh call.");
            }
            extendedStatistics.setStatistics(localStatistics.getStatistics());
            extendedStatistics.setControllableProperties(localStatistics.getControllableProperties());
            return Collections.singletonList(extendedStatistics);
        }

        controlOperationsLock.lock();
        try {
            Map<String, String> statistics = new HashMap<>();
            List<AdvancedControllableProperty> controls = new ArrayList<>();

            List<String> supportedOperations = getSupportedOperations();

            if(supportedOperations.contains("reboot")){
                statistics.put(REBOOT_NAME, "");
                controls.add(createButton(REBOOT_NAME, REBOOT_NAME, "Rebooting...", 90000));
            }
            if(supportedOperations.contains("standby")){
                statistics.put(STANDBY_NAME, "");
                controls.add(createButton(STANDBY_NAME, STANDBY_NAME, "Processing...", 0));
            }

            requestPowerStatus(statistics, controls);
            requestVideoMode(statistics, controls);
            requestAudioMode(statistics, controls);
            requestNetworkConfiguration(statistics);
            requestDeviceIdentity(statistics);
            requestFeatures(statistics, controls);
            requestPersonalizationFeatures(statistics, controls);

            extendedStatistics.setStatistics(statistics);
            extendedStatistics.setControllableProperties(controls);
            localStatistics = extendedStatistics;
        } finally {
            controlOperationsLock.unlock();
        }

        return Collections.singletonList(extendedStatistics);
    }

    @Override
    protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) throws Exception {
        if(httpMethod.equals(HttpMethod.PATCH)){
            headers.set(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
        }
        headers.set("Authorization", "Basic " + encodedCreds);
        return super.putExtraRequestHeaders(httpMethod, uri, headers);
    }

    /**
     * Fetch video mode information
     * @param statistics map to save data to
     * @param controls list to add controllable properties related to video mode to
     * @throws Exception during http GET operation
     * */
    private void requestVideoMode(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode response = doGet(CONFIGURATION_VIDEO, JsonNode.class);

        String videoMode = response.get("mode").asText();
        ArrayNode supportedModes = (ArrayNode) response.get("supportedModes");
        statistics.put(VIDEO_MODE_NAME, videoMode);
        controls.add(createDropdown(VIDEO_MODE_NAME, videoMode, StreamSupport.stream(supportedModes.spliterator(),
                false).map(JsonNode::asText).collect(Collectors.toList())));
    }

    /**
     * Fetch audio mode information
     * @param statistics map to save data to
     * @param controls list to add controllable properties related to audio mode to
     * @throws Exception during http GET operation
     * */
    private void requestAudioMode(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode response = doGet(CONFIGURATION_AUDIO, JsonNode.class);
        String audioOutput = response.get("output").asText();
        ArrayNode supportedOutputs = (ArrayNode) response.get("supportedOutputs");
        statistics.put(AUDIO_OUTPUT_NAME, audioOutput);
        controls.add(createDropdown(AUDIO_OUTPUT_NAME, audioOutput, StreamSupport.stream(supportedOutputs.spliterator(),
                false).map(JsonNode::asText).collect(Collectors.toList())));

        statistics.put(AUDIO_NAME, response.get("enabled").asText());
        controls.add(createSwitch(AUDIO_NAME, "enabled", "disabled", response.get("enabled").asBoolean()));
    }

    /**
     * Fetch power management information
     * @param statistics map to save data to
     * @param controls list to add controllable properties related to power status to
     * @throws Exception during http GET operation
     * */
    private void requestPowerStatus(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode response = doGet(POWER_MANAGEMENT, JsonNode.class);

        String powerMode = response.get("powerMode").asText();
        ArrayNode powerModeOptions = (ArrayNode) response.get("supportedPowerModes");
        statistics.put(POWER_MODE_NAME, powerMode);
        controls.add(createDropdown(POWER_MODE_NAME, powerMode, StreamSupport.stream(powerModeOptions.spliterator(),
                false).map(JsonNode::asText).collect(Collectors.toList())));

        String standbyTimeout = response.get("standbyTimeout").asText();
        ArrayNode standbyTimeouts = (ArrayNode) response.get("supportedStandbyTimeouts");
        statistics.put(STANDBY_TIMEOUT_NAME, standbyTimeout);
        controls.add(createDropdown(STANDBY_TIMEOUT_NAME, standbyTimeout, StreamSupport.stream(standbyTimeouts.spliterator(),
                false).map(JsonNode::asText).collect(Collectors.toList())));

        String powerStatus = response.get("status").asText();
        statistics.put(POWER_STATUS_NAME, powerStatus);
        ArrayNode supportedStatuses = (ArrayNode) response.get("supportedStatuses");
        controls.add(createDropdown(POWER_STATUS_NAME, powerStatus, StreamSupport.stream(supportedStatuses.spliterator(),
                false).map(JsonNode::asText).collect(Collectors.toList())));
    }

    /**
     * Fetch personalization data:
     *      Controllable properties:
     *              System language
     *              Welcome message
     * @param statistics map to save data to
     * @param controls list to add controllable properties related to personalization features to
     * */
    private void requestPersonalizationFeatures(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode personalization = doGet(PERSONALIZATION, JsonNode.class);
        statistics.put("Personalization#Meeting room name", personalization.get("meetingRoomName").asText());

        String language = personalization.get("language").asText();
        ArrayNode languageOptions = (ArrayNode) personalization.get("supportedLanguages");
        statistics.put(LANGUAGE_NAME, language);
        controls.add(createDropdown(LANGUAGE_NAME, language, StreamSupport.stream(languageOptions.spliterator(),
                false).map(JsonNode::asText).collect(Collectors.toList())));

        String welcomeMessage = personalization.get("welcomeMessage").asText();
        statistics.put(WELCOME_MESSAGE_NAME, welcomeMessage);
        controls.add(createText(WELCOME_MESSAGE_NAME, welcomeMessage));
    }

    /**
     * Fetch network configuration and save it to statistics map
     * @param statistics map to save network data to
     * @throws Exception during http GET request
     * */
    private void requestNetworkConfiguration(Map<String, String> statistics) throws Exception {
        JsonNode response = doGet(CONFIGURATION_NETWORK, JsonNode.class);
        addStatisticsProperty(statistics, "Network configuration#Hostname", response.get("hostname"));

        JsonNode proxy = response.get("services").get("proxy");
        JsonNode dhcpServer = response.get("services").get("dhcpServer");

        boolean proxyEnabled = proxy.get("enabled").asBoolean();
        statistics.put("Network configuration#Proxy", proxyEnabled ? "enabled" : "disabled");
        if(proxyEnabled){
            addStatisticsProperty(statistics, "Network configuration#Proxy server address", proxy.get("serverAddress"));
            addStatisticsProperty(statistics, "Network configuration#Proxy username", proxy.get("username"));
        }

        addStatisticsProperty(statistics, "Network configuration#DHCP Domain name", dhcpServer.get("domainName"));
        addStatisticsProperty(statistics, "Network configuration#DHCP MAX address", dhcpServer.get("maxAddress"));
        addStatisticsProperty(statistics, "Network configuration#DHCP MIN address", dhcpServer.get("minAddress"));
        addStatisticsProperty(statistics, "Network configuration#DHCP Subnet mask", dhcpServer.get("subnetMask"));

        populateNetworkConfigurationData(response, "wired", statistics);
        populateNetworkConfigurationData(response, "wireless", statistics);
    }

    /**
     * Unpack network related data from json to statistics
     * @param node json node that contains specific network-related info
     * @param key key to retrieve network related properties from json
     * @param statistics map to save statistics data to
     * */
    private void populateNetworkConfigurationData(JsonNode node, String key, Map<String, String> statistics){
        ArrayNode networkProperties = (ArrayNode) node.get(key);
        if(networkProperties.size() == 0){
            return;
        }
        networkProperties.elements().forEachRemaining(jsonNode -> {
            int id = jsonNode.get("id").asInt();
            addStatisticsProperty(statistics, String.format("Network configuration: %s#Configuration %s Operation Mode", key, id), jsonNode.get("operationMode"));
            addStatisticsProperty(statistics, String.format("Network configuration: %s#Configuration %s Addressing", key, id), jsonNode.get("addressing"));
            addStatisticsProperty(statistics, String.format("Network configuration: %s#Configuration %s Status", key, id), jsonNode.get("status"));
            addStatisticsProperty(statistics, String.format("Network configuration: %s#Configuration %s Ip Address", key, id), jsonNode.get("ipAddress"));
            addStatisticsProperty(statistics, String.format("Network configuration: %s#Configuration %s Subnet Mask", key, id), jsonNode.get("subnetMask"));
            addStatisticsProperty(statistics, String.format("Network configuration: %s#Configuration %s Default Gateway", key, id), jsonNode.get("defaultGateway"));
            addStatisticsProperty(statistics, String.format("Network configuration: %s#Configuration %s MAC Address", key, id), jsonNode.get("macAddress"));
        });
    }

    /**
     * Add statistics property if the property exists
     * Otherwise - skip
     * @param statistics map to save property to
     * @param name name of the property
     * @param node to extract textual data from
     * */
    private void addStatisticsProperty(Map<String, String> statistics, String name, JsonNode node){
        if(node != null && !node.isNull() && node.isTextual()){
            String value = node.asText();
            if(!StringUtils.isEmpty(value)) {
                statistics.put(name, value);
            }
        }
    }

    /**
     * Request general device information
     * @param statistics map of the statistics data to save parameters to
     * @throws Exception during http GET operation
     * */
    private void requestDeviceIdentity(Map<String, String> statistics) throws Exception {
        JsonNode response = doGet(DEVICE_IDENTITY, JsonNode.class);
        addStatisticsProperty(statistics, "Device information#Serial number", response.get("serialNumber"));
        addStatisticsProperty(statistics, "Device information#Article number", response.get("articleNumber"));
        addStatisticsProperty(statistics, "Device information#Model name", response.get("modelName"));
        addStatisticsProperty(statistics, "Device information#Product name", response.get("productName"));
    }

    /**
     * Request ClickShare device features and features states to build controls
     * @param statistics map of the statistics data to save parameters to
     * @param controls list of the controllable properties to add switch controls to
     * @throws Exception during http GET operation
     * */
    private void requestFeatures(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        createFeatureControl(MIRACAST_NAME, doGet(FEATURES_MIRACAST, JsonNode.class), "enabled", statistics, controls);
        createFeatureControl(GOOGLECAST_NAME, doGet(FEATURES_GOOGLECAST, JsonNode.class), "enabled", statistics, controls);
        createFeatureControl(AIRPLAY_NAME, doGet(FEATURES_AIRPLAY, JsonNode.class), "enabled", statistics, controls);
        createFeatureControl(BLACKBOARD_SAVING_NAME, doGet(FEATURES_BLACKBOARD, JsonNode.class), "savingEnabled", statistics, controls);
    }

    /**
     * Create a control based on data about feature triggers
     * @param name property name
     * @param response json containing information about the feature
     * @param triggerKey json key to use for retrieving current feature status
     * @param statistics map to save statistics key:value to
     * @param controls list of controls to put controllable properties to
     * */
    private void createFeatureControl(String name, JsonNode response, String triggerKey,
                                      Map<String, String> statistics, List<AdvancedControllableProperty> controls){
        boolean featureEnabled = response.get(triggerKey).asBoolean();
        statistics.put(name, String.valueOf(featureEnabled));
        controls.add(createSwitch(name, "enabled", "disabled", featureEnabled));
    }

    /**
     * Get a list of operations supported by ClickShare device
     * @return list of available operations
     * @throws Exception while performing http GET operation
     * */
    private List<String> getSupportedOperations() throws Exception {
        ArrayNode supportedOperations = doGet(OPERATIONS_SUPPORTED, ArrayNode.class);
        return StreamSupport.stream(supportedOperations.spliterator(), false).map(JsonNode::asText).collect(Collectors.toList());
    }

    /**
     * Instantiate Text controllable property
     * @param name name of the property
     * @param label default button label
     * @param labelPressed button label when is pressed
     * @param gracePeriod period to pause monitoring statistics for
     * @return instance of AdvancedControllableProperty with AdvancedControllableProperty.Button as type
     * */
    private AdvancedControllableProperty createButton(String name, String label, String labelPressed, long gracePeriod){
        AdvancedControllableProperty.Button button = new AdvancedControllableProperty.Button();
        button.setLabel(label);
        button.setLabelPressed(labelPressed);
        button.setGracePeriod(gracePeriod);

        return new AdvancedControllableProperty(name, new Date(), button, "");
    }

    /**
     * Instantiate Text controllable property
     * @param name name of the property
     * @param initialValue initial value of the dropdown control indicating the selected value
     * @param options list of dropdown options
     * @return instance of AdvancedControllableProperty with AdvancedControllableProperty.DropDown as type
     * */
    private AdvancedControllableProperty createDropdown(String name, String initialValue, List<String> options){
        AdvancedControllableProperty.DropDown dropDown = new AdvancedControllableProperty.DropDown();
        dropDown.setOptions(options.toArray(new String[0]));
        dropDown.setLabels(options.toArray(new String[0]));
        return new AdvancedControllableProperty(name, new Date(), dropDown, initialValue);
    }

    /**
     * Instantiate Text controllable property
     * @param name name of the property
     * @param labelOn "On" label value
     * @param labelOff "Off" label value
     * @param initialValue initial value of the switch control (1|0)
     * @return instance of AdvancedControllableProperty with AdvancedControllableProperty.Switch as type
     * */
    private AdvancedControllableProperty createSwitch(String name, String labelOn, String labelOff, boolean initialValue){
        AdvancedControllableProperty.Switch controlSwitch = new AdvancedControllableProperty.Switch();
        controlSwitch.setLabelOn(labelOn);
        controlSwitch.setLabelOff(labelOff);
        return new AdvancedControllableProperty(name, new Date(), controlSwitch, initialValue);
    }

    /**
     * Instantiate Text controllable property
     * @param name name of the property
     * @param value value of the text field
     * @return instance of AdvancedControllableProperty with AdvancedControllableProperty.Text as type
     * */
    private AdvancedControllableProperty createText(String name, String value){
        AdvancedControllableProperty.Text text = new AdvancedControllableProperty.Text();
        return new AdvancedControllableProperty(name, new Date(), text, value);
    }

    /**
     * Patch the device property
     * @param apiResource endpoint to use
     * @param key property to patch
     * @param value new property value
     * @param property control property that initiated the patch action
     * @return boolean indicating whether an http PATCH operation is successful or not
     * */
    private boolean patchDeviceMode(String apiResource, String key, String value, String property) throws Exception {
        JsonNode response = doPatch(apiResource, String.format("%s=%s", key, value), JsonNode.class);
        boolean success = response.get("status").asInt() == 200;
        if(success){
            updateLocalControllablePropertyState(property, value);
        }
        updateLatestControlTimestamp();
        return success;
    }

    /**
     * Request ClickShare reboot
     * @return boolean indicating whether request is successful
     * @throws Exception while performing http POST
     * */
    private boolean requestReboot() throws Exception {
        JsonNode rebootResponse = doPost(OPERATIONS_REBOOT, null, JsonNode.class);
        updateLatestControlTimestamp();
        return rebootResponse.get("status").asInt() == 202;
    }

    private boolean requestStandby() throws Exception {
        JsonNode standbyResponse = doPost(OPERATIONS_STANDBY, null, JsonNode.class);
        updateLatestControlTimestamp();
        return standbyResponse.get("status").asInt() == 202;
    }

    /***
     * Update controllable property value in local statistics
     * @param property name
     * @param value new property value
     */
    private void updateLocalControllablePropertyState(String property, String value){
        localStatistics.getStatistics().put(property, value);
        localStatistics.getControllableProperties().stream().filter(cp -> cp.getName().equals(property)).findFirst().ifPresent(cp -> {
            cp.setValue(value);
            cp.setTimestamp(new Date());
        });
    }

    /**
     * Update timestamp of the latest control operation
     * */
    private void updateLatestControlTimestamp(){
        latestControlTimestamp = new Date().getTime();
    }

    /***
     * Check whether the control operations cooldown has ended
     * @return boolean value indicating whether the cooldown has ended or not
     */
    private boolean isValidControlCoolDown(){
        return (new Date().getTime() - latestControlTimestamp) < controlsCooldownTimeout;
    }
}
