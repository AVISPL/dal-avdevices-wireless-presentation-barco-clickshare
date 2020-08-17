/*
 * Copyright (c) 2020 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.barco.clickshare;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.http.entity.ContentType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;
import com.avispl.symphony.dal.util.StringUtils;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.avispl.symphony.dal.communicator.barco.clickshare.BarcoClickShareProperties.*;

/**
 * An implementation of RestCommunicator to provide communication and interaction with Barco ClickShare devices.
 * Supported features are:
 * API V2:
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
 * (deep_standby mode is removed to keep device responsive. If set to deep standby -> implicitly changes to eco_standby)
 *          - Reboot
 *          - Standby
 * API V1.x:
 *      Monitoring:
 *          - Audio mode
 *          - General device information
 *          - Sensors data
 *          - Devise status
 *          - Display settings
 *          - Features (miracast, googlecast, airplay, blackboard, clickshare app)
 *          - Network settings
 *          - Power management
 *          - Processes
 *          - Software update type
 *      Control:
 *          - Audio mode
 *          - Display settings
 *          - Features (miracast, googlecast, airplay, blackboard, clickshare app)
 *          - Power management
 * (deep_standby mode is removed to keep device responsive. If set to deep standby -> implicitly changes to eco_standby,
 * CSE-200 has networked_standby mode removed)
 *          - Reboot
 *          - Standby
 *          - Software update type
 * @author Maksym.Rossiytsev
 * @version 1.1
 * */
public class BarcoClickShareCommunicator extends RestCommunicator implements Monitorable, Controller {

    private String encodedCreds;
    ExtendedStatistics localStatistics;
    private final ReentrantLock controlOperationsLock = new ReentrantLock();

    private long latestControlTimestamp;
    private long controlsCooldownTimeout = 3000;

    private String deviceModel;
    private String supportedApiVersion = "";

    public BarcoClickShareCommunicator(){
        super();
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
            updateSupportedApiVersion();
            if(property.matches("Display#Output\\s\\d{1,3}\\sResolution")){
                String displayNumber = property.split(" ")[1];
                putDeviceMode(String.format(V1_DISPLAY_RESOLUTION, displayNumber), "value", value, property);
                return;
            }

            if(isApiV2Supported()){
                switch (property){
                    case POWER_MODE_NAME:
                        patchDeviceMode(POWER_MANAGEMENT, "powerMode", value, property);
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
                    case AUDIO_OUTPUT_NAME:
                        patchDeviceMode(CONFIGURATION_AUDIO, "output", value, property);
                        break;
                    case LANGUAGE_NAME:
                        patchDeviceMode(PERSONALIZATION, "language", value, property);
                        break;
                    case WELCOME_MESSAGE_NAME:
                        patchDeviceMode(PERSONALIZATION, "welcomeMessage", value, property);
                        break;
                    case MEETING_ROOM_NAME:
                        patchDeviceMode(PERSONALIZATION, "meetingRoomName", value, property);
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
                    case STANDBY_NAME:
                        requestStandby();
                        break;
                    case REBOOT_NAME:
                        requestReboot();
                        break;
                    default:
                        if(logger.isWarnEnabled()){
                            logger.warn(String.format("Operation %s with value %s is not supported.", property, value));
                        }
                        break;
                }
            } else {
                switch (property){
                    case POWER_MODE_NAME:
                        putDeviceMode(V1_5_ENERGY_MODE, "value", value, property);
                        break;
                    case ONSCREEN_LANGUAGE_NAME:
                        putDeviceMode(V1_ON_SCREEN_TEXT_LANGUAGE, "value", value, property);
                        break;
                    case ONSCREEN_WELCOME_MESSAGE_NAME:
                        putDeviceMode(V1_ON_SCREEN_TEXT_WELCOME_MESSAGE, "value", value, property);
                        break;
                    case ONSCREEN_MEETING_ROOM_NAME:
                        putDeviceMode(V1_ON_SCREEN_TEXT_MEETING_ROOM_NAME, "value", value, property);
                        break;
                    case SOFTWARE_UPDATE_NAME:
                        putDeviceMode(V1_7_UPDATE_TYPE, "value", value, property);
                        break;
                    case AUDIO_OUTPUT_NAME:
                        putDeviceMode(V1_5_AUDIO_OUTPUT, "value", value, property);
                        break;
                    case SCREENSAVER_MODE_NAME:
                        putDeviceMode(V1_14_SCREEN_SAVER_MODE, "value", value, property);
                        break;
                    case DISPLAY_TIMEOUT_NAME:
                        putDeviceMode(V1_DISPLAY_TIMEOUT, "value", value, property);
                        break;
                    case SCREENSAVER_TIMEOUT_NAME:
                        putDeviceMode(V1_SCREENSAVER_TIMEOUT, "value", value, property);
                        break;
                    case MIRACAST_NAME:
                        putDeviceMode(V1_13_ENABLE_MIRACAST, "value", "0".equals(value) ? "false" : "true", property);
                        break;
                    case GOOGLECAST_NAME:
                        putDeviceMode(V1_8_ENABLE_GOOGLECAST, "value", "0".equals(value) ? "false" : "true", property);
                        break;
                    case AIRPLAY_NAME:
                        putDeviceMode(V1_8_ENABLE_AIRPLAY, "value", "0".equals(value) ? "false" : "true", property);
                        break;
                    case BLACKBOARD_SAVING_NAME:
                        putDeviceMode(V1_7_BLACKBOARD_SAVING, "value", "0".equals(value) ? "false" : "true", property);
                        break;
                    case CLICKSHARE_NAME:
                        putDeviceMode(V1_8_ENABLE_CLICKSHARE_APP, "value", "0".equals(value) ? "false" : "true", property);
                        break;
                    case ONSCREEN_MEETING_ROOM_INFO_NAME:
                        putDeviceMode(V1_ON_SCREEN_TEXT_SHOW_MEETING_ROOM, "value", "0".equals(value) ? "false" : "true", property);
                        break;
                    case ONSCREEN_NETWORK_INFO_NAME:
                        putDeviceMode(V1_ON_SCREEN_TEXT_SHOW_NETWORK, "value", "0".equals(value) ? "false" : "true", property);
                        break;
                    case AUDIO_NAME:
                        putDeviceMode(V1_AUDIO_ENABLED, "value", "0".equals(value) ? "false" : "true", property);
                        break;
                    case DISPLAY_WALLPAPER:
                        putDeviceMode(V1_SHOW_WALLPAPER, "value",  "0".equals(value) ? "false" : "true", property);
                        break;
                    case DISPLAY_HOTPLUG_NAME:
                        putDeviceMode(V1_DISPLAY_HOT_PLUG, "value", "0".equals(value) ? "false" : "true", property);
                        break;
                    case STANDBY_NAME:
                        boolean success = putDeviceMode(V1_6_REQUEST_STANDBY, "value", "0".equals(value) ? "false" : "true", property);
                        if(success){
                            localStatistics.getStatistics().put(POWER_STATUS_NAME, "0".equals(value) ? "On" : "Standby");
                        }
                        break;
                    case DISPLAY_STANDBY_NAME:
                        putDeviceMode(V1_DISPLAY_STANDBY_STATE, "value", "0".equals(value) ? "false" : "true", property);
                    case REBOOT_NAME:
                        requestReboot();
                        break;
                    default:
                        if(logger.isWarnEnabled()){
                            logger.warn(String.format("Operation %s with value %s is not supported.", property, value));
                        }
                        break;
                }
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
    protected void authenticate() {
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        ExtendedStatistics extendedStatistics = new ExtendedStatistics();

        controlOperationsLock.lock();
        try {
            updateSupportedApiVersion();
            if(isValidControlCoolDown() && localStatistics != null){
                if (logger.isDebugEnabled()) {
                    logger.debug("Device is occupied. Skipping statistics refresh call.");
                }
                extendedStatistics.setStatistics(localStatistics.getStatistics());
                extendedStatistics.setControllableProperties(localStatistics.getControllableProperties());
                return Collections.singletonList(extendedStatistics);
            }

            Map<String, String> statistics = new HashMap<>();
            List<AdvancedControllableProperty> controls = new ArrayList<>();

            if(supportedApiVersion.contains("v1")){
                v1ApiRoute(statistics, controls);
            } else if(supportedApiVersion.contains("v2")){
                v2ApiRoute(statistics, controls);
            }

            extendedStatistics.setStatistics(statistics);
            extendedStatistics.setControllableProperties(controls);
            localStatistics = extendedStatistics;
        } finally {
            controlOperationsLock.unlock();
        }

        return Collections.singletonList(extendedStatistics);
    }

    /**
     * Update supported API version
     * Used outside of init() method since it's using https endpoint in order to fetch the supported version
     * which is not possible to do before init() has completed
     * Might be fetched by both statistics collection and control event ->
     * if the device adapter has been removed/reloaded - controls on UI may stay stale which will make it
     * possible to send a control event. If done before the initial statistics call - may lead to an error
     * (fetching data before the baseUrl which is not possible to set at this stage because of the init() process as well)
     * @throws Exception during http communication
     */
    private void updateSupportedApiVersion() throws Exception {
        if(StringUtils.isNullOrEmpty(supportedApiVersion)){
            supportedApiVersion = String.format("%s/", getSupportedVersion());
        }
    }

    /**
     * Performs actions needed for collecting data for devices supporting API V1 (CSE series)
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void v1ApiRoute(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        if(!supportedApiVersion.contains(".")){
            throw new UnsupportedOperationException("The v1 API version without minor version is not supported");
        }
        int minorApiVersion = Integer.parseInt(supportedApiVersion.replaceAll("\\/", "").split("\\.")[1]);

        if(minorApiVersion >= 0){
            v1_0_RequestDeviceInfo(statistics, controls);
        }
        if(minorApiVersion >= 5){
            v1_5_RequestDeviceInfo(statistics, controls);
        }
        if(minorApiVersion >= 6){
            v1_6_RequestDeviceInfo(statistics, controls);
        }
        if(minorApiVersion >= 7){
            v1_7_RequestDeviceInfo(statistics, controls);
        }
        if(minorApiVersion >= 8){
            v1_8_RequestDeviceInfo(statistics, controls);
        }
        if(minorApiVersion >= 11){
            v1_11_RequestDeviceInfo(statistics);
        }
        if(minorApiVersion >= 13){
            v1_13_RequestDeviceInfo(statistics, controls);
        }
        if(minorApiVersion >= 14){
            v1_14_RequestDeviceInfo(statistics, controls);
        }
    }

    /**
     * Performs actions needed for collecting data for devices supporting API V2 (CX series)
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void v2ApiRoute(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        List<String> supportedOperations = getSupportedOperations();

        if(supportedOperations.contains("reboot")){
            statistics.put(REBOOT_NAME, "");
            controls.add(createButton(REBOOT_NAME, REBOOT_NAME, "Rebooting...", V2_REBOOT_GRACE_PERIOD));
        }
        if(supportedOperations.contains("standby")){
            statistics.put(STANDBY_NAME, "");
            controls.add(createButton(STANDBY_NAME, STANDBY_NAME, "Processing...", 0));
        }

        v2RequestPowerStatus(statistics, controls);
        v2RequestVideoMode(statistics, controls);
        v2RequestAudioMode(statistics, controls);
        v2RequestNetworkConfiguration(statistics);
        v2RequestDeviceIdentity(statistics);
        v2RequestFeatures(statistics, controls);
        v2RequestPersonalizationFeatures(statistics, controls);
    }

    @Override
    protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) throws Exception {
        if(httpMethod.equals(HttpMethod.PATCH) || httpMethod.equals(HttpMethod.PUT)){
            headers.set(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
        }
        headers.set("Authorization", "Basic " + encodedCreds);
        return super.putExtraRequestHeaders(httpMethod, uri, headers);
    }

    /**
     * Collecting statistics and adding controls for API V1.0
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void v1_0_RequestDeviceInfo(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode deviceInfo = getApiV1JsonNode(supportedApiVersion + V1_DEVICE_INFO);
        JsonNode audioEnabled = getApiV1JsonNode(supportedApiVersion + V1_AUDIO_ENABLED);

        deviceModel = deviceInfo.get("ModelName").asText();

        statistics.put("Device Information#Article Number", deviceInfo.get("ArticleNumber").asText());
        statistics.put("Device Information#Model Name", deviceModel);
        statistics.put("Device Information#Serial Number", deviceInfo.get("SerialNumber").asText());

        statistics.put("Device Status#Uptime (sec)", deviceInfo.get("CurrentUptime").asText());
        statistics.put("Device Status#Uptime Total (sec)", deviceInfo.get("TotalUptime").asText());
        statistics.put("Device Status#First Used", deviceInfo.get("FirstUsed").asText());
        statistics.put("Device Status#In Use", deviceInfo.get("InUse").asText());
        statistics.put("Device Status#Status", DEVICE_STATUSES.get(deviceInfo.get("Status").asInt()));
        statistics.put("Device Status#Sharing", deviceInfo.get("Sharing").asText());
        addStatisticsProperty(statistics, "Device Status#Status Message", deviceInfo.get("StatusMessage"));

        statistics.put("Device Sensors#Cpu Temperature (C)", deviceInfo.get("Sensors").get("CpuTemperature").asText());
        statistics.put("Device Sensors#Pcie Temperature (C)", deviceInfo.get("Sensors").get("PcieTemperature").asText());
        statistics.put("Device Sensors#Sio Temperature (C)", deviceInfo.get("Sensors").get("SioTemperature").asText());
        addStatisticsProperty(statistics, "Last used", deviceInfo.get("LastUsed"));

        for(int i = 1; i < deviceInfo.get("Processes").get("ProcessCount").asInt() + 1; i++){
            JsonNode process = deviceInfo.get("Processes").get("ProcessTable").get(String.valueOf(i));
            statistics.put(String.format("Processes#%s. %s", i, process.get("Name").asText()), process.get("Status").asText());
        }

        statistics.put(AUDIO_NAME, "");
        controls.add(createSwitch(AUDIO_NAME, "enabled", "disabled", audioEnabled.asBoolean()));
        statistics.put(REBOOT_NAME, "");
        controls.add(createButton(REBOOT_NAME, REBOOT_NAME, "Rebooting...", V1_REBOOT_GRACE_PERIOD));

        setDisplayStatistics(statistics, controls);
        setOnScreenTextStatistics(statistics, controls);
    }

    /**
     * Collecting statistics related to on screen text data for API V1.0
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void setOnScreenTextStatistics(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception{
        JsonNode display = getApiV1JsonNode(supportedApiVersion + V1_ON_SCREEN_TEXT);
        statistics.put("On Screen Text#Location", display.get("Location").asText());

        statistics.put(ONSCREEN_LANGUAGE_NAME, "");
        controls.add(createDropdown(ONSCREEN_LANGUAGE_NAME, display.get("Language").asText(), Arrays.asList(display.get("SupportedLanguages").asText().split(","))));

        statistics.put(ONSCREEN_WELCOME_MESSAGE_NAME, "");
        controls.add(createText(ONSCREEN_WELCOME_MESSAGE_NAME, display.get("WelcomeMessage").asText()));

        statistics.put(ONSCREEN_MEETING_ROOM_NAME, "");
        controls.add(createText(ONSCREEN_MEETING_ROOM_NAME, display.get("MeetingRoomName").asText()));

        statistics.put(ONSCREEN_MEETING_ROOM_INFO_NAME, "");
        controls.add(createSwitch(ONSCREEN_MEETING_ROOM_INFO_NAME, "On", "Off", display.get("ShowNetworkInfo").asBoolean()));

        statistics.put(ONSCREEN_NETWORK_INFO_NAME, "");
        controls.add(createSwitch(ONSCREEN_NETWORK_INFO_NAME, "On", "Off", display.get("ShowMeetingRoomInfo").asBoolean()));
    }

    /**
     * Collecting statistics related to display data for API V1.0
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void setDisplayStatistics(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode display = getApiV1JsonNode(supportedApiVersion + V1_DISPLAY);
        statistics.put(DISPLAY_STANDBY_NAME, "");
        controls.add(createSwitch(DISPLAY_STANDBY_NAME, "On", "Off", display.get("StandbyState").asBoolean()));

        statistics.put("Display#Display Count", display.get("DisplayCount").asText());
        statistics.put(DISPLAY_TIMEOUT_NAME, "");
        controls.add(createDropdown(DISPLAY_TIMEOUT_NAME, display.get("DisplayTimeout").asText(), deviceModel.equals(CSE800) ? CSE800_DISPLAY_TIMEOUTS : TIMEOUTS));

        statistics.put(DISPLAY_HOTPLUG_NAME, "");
        controls.add(createSwitch(DISPLAY_HOTPLUG_NAME, "On", "Off", display.get("HotPlug").asBoolean()));
        statistics.put(SCREENSAVER_TIMEOUT_NAME, "");
        controls.add(createDropdown(SCREENSAVER_TIMEOUT_NAME, display.get("ScreenSaverTimeout").asText(), TIMEOUTS));

        statistics.put(DISPLAY_WALLPAPER, "");
        controls.add(createSwitch(DISPLAY_WALLPAPER, "On", "Off", display.get("ShowWallpaper").asBoolean()));

        for(int i = 1; i < display.get("OutputCount").asInt() + 1; i++){
            JsonNode currentNode = display.get("OutputTable").get(String.valueOf(i));
            statistics.put(String.format("Display#Output %s Connected", i), currentNode.get("Connected").asText());
            statistics.put(String.format("Display#Output %s Enabled", i), currentNode.get("Enabled").asText());
            statistics.put(String.format("Display#Output %s Native Resolution", i), currentNode.get("NativeResolution").asText());
            statistics.put(String.format("Display#Output %s Port", i), currentNode.get("Port").asText());
            statistics.put(String.format("Display#Output %s Position", i), currentNode.get("Position").asText());
            statistics.put(String.format("Display#Output %s Resolution", i), currentNode.get("Resolution").asText());

            controls.add(createDropdown(String.format("Display#Output %s Resolution", i), currentNode.get("Resolution").asText(),
                    Arrays.asList(currentNode.get("SupportedResolutions").asText().split(","))));
        }
    }

    /**
     * Collecting statistics and adding controls for API V1.5
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void v1_5_RequestDeviceInfo(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        String audioOutput = getApiV1JsonNode(supportedApiVersion + V1_5_AUDIO_OUTPUT).asText();
        String powerMode = getApiV1JsonNode(supportedApiVersion + V1_5_ENERGY_MODE).asText();
        if(deviceModel != null){
            if(powerMode.equals(DEEP_STANDBY_V1)){
                putDeviceMode(V1_5_ENERGY_MODE, "powerMode", ECO_STANDBY_V1, POWER_MODE_NAME);
                powerMode = ECO_STANDBY_V1;
                if(logger.isDebugEnabled()){
                    logger.debug("The device power mode is set to 'deepStandby'. Requesting change to 'ecoStandby' as a part of device adapter initialization.");
                }
            }
            statistics.put(POWER_MODE_NAME, powerMode);
            if(deviceModel.equals(CSE200)){
                controls.add(createDropdown(POWER_MODE_NAME, powerMode, CSE200_ENERGY_MODES, CSE200_ENERGY_MODES));
            } else if(deviceModel.equals(CSE800)){
                controls.add(createDropdown(POWER_MODE_NAME, powerMode, CSE800_ENERGY_MODES_LABELS, ENERGY_MODES));
            } else {
                controls.add(createDropdown(POWER_MODE_NAME, powerMode, ENERGY_MODES, ENERGY_MODES));
            }

            statistics.put(AUDIO_OUTPUT_NAME, audioOutput);
            controls.add(createDropdown(AUDIO_OUTPUT_NAME, audioOutput, AUDIO_OUTPUT_MODES));
        }
    }

    /**
     * Collecting statistics and adding controls for API V1.6
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void v1_6_RequestDeviceInfo(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        String powerStatusName = getApiV1JsonNode(supportedApiVersion + V1_6_SYSTEM_STATE).asText();
        statistics.put(POWER_STATUS_NAME, powerStatusName);
        statistics.put(STANDBY_NAME, "");
        controls.add(createSwitch(STANDBY_NAME, "On", "Off", powerStatusName.equals("Standby")));
    }

    /**
     * Collecting statistics and adding controls for API V1.7
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void v1_7_RequestDeviceInfo(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        statistics.put(BLACKBOARD_SAVING_NAME, "");
        statistics.put(SOFTWARE_UPDATE_NAME, "");

        controls.add(createSwitch(BLACKBOARD_SAVING_NAME, "On", "Off", getApiV1JsonNode(supportedApiVersion + V1_7_BLACKBOARD_SAVING).asBoolean()));
        controls.add(createDropdown(SOFTWARE_UPDATE_NAME, getApiV1JsonNode(supportedApiVersion + V1_7_UPDATE_TYPE).asText(), SOFTWARE_UPDATE_TYPES));
    }

    /**
     * Collecting statistics and adding controls for API V1.8
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void v1_8_RequestDeviceInfo(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode airplay = getApiV1JsonNode(supportedApiVersion + V1_8_ENABLE_AIRPLAY);
        JsonNode clickShareApp = getApiV1JsonNode(supportedApiVersion + V1_8_ENABLE_CLICKSHARE_APP);
        JsonNode googlecast = getApiV1JsonNode(supportedApiVersion + V1_8_ENABLE_GOOGLECAST);

        addStatisticsProperty(statistics, AIRPLAY_NAME, airplay);
        addStatisticsProperty(statistics, CLICKSHARE_NAME, clickShareApp);
        addStatisticsProperty(statistics, GOOGLECAST_NAME, googlecast);

        controls.add(createSwitch(AIRPLAY_NAME, "On", "Off", airplay.asBoolean()));
        controls.add(createSwitch(CLICKSHARE_NAME, "On", "Off", clickShareApp.asBoolean()));
        controls.add(createSwitch(GOOGLECAST_NAME, "On", "Off", googlecast.asBoolean()));
    }

    /**
     * Collecting statistics and adding controls for API V1.11
     * @param statistics map to store statistics data to
     * @throws Exception during http communication
     */
    private void v1_11_RequestDeviceInfo(Map<String, String> statistics) throws Exception {
        addStatisticsProperty(statistics, "Network#Wlan IP Address", getApiV1JsonNode(supportedApiVersion + V1_11_WLAN_IP_ADDRESS));
        addStatisticsProperty(statistics, "Network#Wlan Subnet Mask", getApiV1JsonNode(supportedApiVersion + V1_11_WLAN_SUBNET_MASK));
        addStatisticsProperty(statistics, "Device Sensors#Cpu Fan Speed", getApiV1JsonNode(supportedApiVersion + V1_11_CPU_FAN_SPEED));
        addStatisticsProperty(statistics, "Display#CEC", getApiV1JsonNode(supportedApiVersion + V1_11_DISPLAY_CEC));
    }

    /**
     * Collecting statistics and adding controls for API V1.13
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void v1_13_RequestDeviceInfo(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        statistics.put(MIRACAST_NAME, "");
        controls.add(createSwitch(MIRACAST_NAME, "enabled", "disabled", getApiV1JsonNode(supportedApiVersion + V1_13_ENABLE_MIRACAST).asBoolean()));
    }

    /**
     * Collecting statistics and adding controls for API V1.14
     * @param statistics map to store statistics data to
     * @param controls list to add controllable properties to
     * @throws Exception during http communication
     */
    private void v1_14_RequestDeviceInfo(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        statistics.put(SCREENSAVER_MODE_NAME, "");
        controls.add(createDropdown(SCREENSAVER_MODE_NAME, getApiV1JsonNode(supportedApiVersion + V1_14_SCREEN_SAVER_MODE).asText(), SCREENSAVER_MODE_NAMES));
    }

    /**
     * Unpack the JsonNode value for device API v1 to avoid boilerplate code
     * Some of the resources may be missing/deprecated over the time, in the API versions that are
     * not released yet. Normally that would lead to error 404. Since this is a singular case so far - it is handled
     * by catching a particular error, which would indicate that the device itself is available, but the requested
     * url resource is not. Since there's no way to figure out the exact reason of the 404 in this case, without
     * knowing all the further deprecations of the resources for particular versions - it's assumed that one of the
     * predefined urls may only be unavailable in 3 cases: device is down, API is down and resource is deprecated.
     * First 2 cases are handled by generic /SupportedVersions call.
     * Also catching error in getApiV1JsonNode method only leaves v2 API behaviour out of this functionality.
     * @param url location to fetch data from
     * @return JsonNode value
     * @throws CommandFailureException during http communication
     */
    private JsonNode getApiV1JsonNode(String url) throws Exception {
        JsonNode response;
        try {
            response = doGet(url, JsonNode.class);
            if(response != null){
                return response.get("data").get("value");
            }
        } catch (CommandFailureException cfe) {
            if(cfe.getResponse().contains("Resource does not exist")){
                logger.debug(String.format("Unable to fetch data from %s. Resource is not available or deprecated.", url));
            } else {
                throw cfe;
            }
        }
        return JsonNodeFactory.instance.objectNode();
    }

    /**
     * Fetch video mode information
     * @param statistics map to save data to
     * @param controls list to add controllable properties related to video mode to
     * @throws Exception during http GET operation
     * */
    private void v2RequestVideoMode(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode response = doGet(supportedApiVersion + CONFIGURATION_VIDEO, JsonNode.class);

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
    private void v2RequestAudioMode(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode response = doGet(supportedApiVersion + CONFIGURATION_AUDIO, JsonNode.class);
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
    private void v2RequestPowerStatus(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode response = doGet(supportedApiVersion + POWER_MANAGEMENT, JsonNode.class);

        String powerMode = response.get("powerMode").asText();
        ArrayNode powerModeOptions = (ArrayNode) response.get("supportedPowerModes");

        if(powerMode.equals(DEEP_STANDBY_V2)){
            patchDeviceMode(POWER_MANAGEMENT, "powerMode", ECO_STANDBY_V2, POWER_MODE_NAME);
            powerMode = ECO_STANDBY_V2;
            if(logger.isDebugEnabled()){
                logger.debug("The device power mode is set to 'deepStandby'. Requesting change to 'ecoStandby' as a part of device adapter initialization.");
            }
        }

        statistics.put(POWER_MODE_NAME, powerMode);
        controls.add(createDropdown(POWER_MODE_NAME, powerMode, StreamSupport.stream(powerModeOptions.spliterator(),
                false).map(JsonNode::asText).filter(s -> !s.toLowerCase().equals("deepstandby")).collect(Collectors.toList())));

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
    private void v2RequestPersonalizationFeatures(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        JsonNode personalization = doGet(supportedApiVersion + PERSONALIZATION, JsonNode.class);
        statistics.put(MEETING_ROOM_NAME, "");
        controls.add(createText(MEETING_ROOM_NAME, personalization.get("meetingRoomName").asText()));

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
    private void v2RequestNetworkConfiguration(Map<String, String> statistics) throws Exception {
        JsonNode response = doGet(supportedApiVersion + CONFIGURATION_NETWORK, JsonNode.class);
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
     * Request general device information
     * @param statistics map of the statistics data to save parameters to
     * @throws Exception during http GET operation
     * */
    private void v2RequestDeviceIdentity(Map<String, String> statistics) throws Exception {
        JsonNode response = doGet(supportedApiVersion + DEVICE_IDENTITY, JsonNode.class);
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
    private void v2RequestFeatures(Map<String, String> statistics, List<AdvancedControllableProperty> controls) throws Exception {
        createFeatureControl(MIRACAST_NAME, doGet(supportedApiVersion + FEATURES_MIRACAST, JsonNode.class), "enabled", statistics, controls);
        createFeatureControl(GOOGLECAST_NAME, doGet(supportedApiVersion + FEATURES_GOOGLECAST, JsonNode.class), "enabled", statistics, controls);
        createFeatureControl(AIRPLAY_NAME, doGet(supportedApiVersion + FEATURES_AIRPLAY, JsonNode.class), "enabled", statistics, controls);
        createFeatureControl(BLACKBOARD_SAVING_NAME, doGet(supportedApiVersion + FEATURES_BLACKBOARD, JsonNode.class), "savingEnabled", statistics, controls);
    }

    /**
     * Get a list of operations supported by ClickShare device
     * @return list of available operations
     * @throws Exception while performing http GET operation
     * */
    private List<String> getSupportedOperations() throws Exception {
        ArrayNode supportedOperations = doGet(supportedApiVersion + OPERATIONS_SUPPORTED, ArrayNode.class);
        return StreamSupport.stream(supportedOperations.spliterator(), false).map(JsonNode::asText).collect(Collectors.toList());
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
     * Get a list of api versions supported by the current ClichShare device.
     * Retrieve the latest one.
     * @return api version string (v2.0 is simplified to v2 to match the url segment)
     * @throws UnsupportedOperationException when no available api version is retrieved
     */
    private String getSupportedVersion() throws Exception {
        JsonNode response = doGet(supportedApiVersion + API_SUPPORTED_VERSIONS, JsonNode.class);
        String version = "";
        if(response.get("status").asInt() == 200){
            ArrayNode versions = (ArrayNode) response.get("data").get("value");
            version = versions.get(versions.size() - 1).asText();
            if(version.startsWith("v2")){
                version = "v2";
            }
        }
        if(StringUtils.isNullOrEmpty(version)){
            throw new UnsupportedOperationException("Unable to retrieve a supported ClickShare API version.");
        }
        return version;
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
            if(!StringUtils.isNullOrEmpty(value.trim())) {
                statistics.put(name, value);
            }
        }
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
     * Labels are used by default -> options data
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
     * @param initialValue initial value of the dropdown control indicating the selected value
     * @param labels list of dropdown labels
     * @param options list of dropdown options
     * @return instance of AdvancedControllableProperty with AdvancedControllableProperty.DropDown as type
     * */
    private AdvancedControllableProperty createDropdown(String name, String initialValue, List<String> labels, List<String> options){
        AdvancedControllableProperty.DropDown dropDown = new AdvancedControllableProperty.DropDown();
        dropDown.setOptions(options.toArray(new String[0]));
        dropDown.setLabels(labels.toArray(new String[0]));
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
     * Patch the device property -> API V2
     * @param apiResource endpoint to use
     * @param key property to patch
     * @param value new property value
     * @param property control property that initiated the patch action
     * @return boolean indicating whether an http PATCH operation is successful or not
     * */
    private boolean patchDeviceMode(String apiResource, String key, String value, String property) throws Exception {
        JsonNode response = doPatch(supportedApiVersion + apiResource, String.format("%s=%s", key, value), JsonNode.class);
        boolean success = response.get("status").asInt() == 200;
        if(success){
            updateLocalControllablePropertyState(property, value);
        }
        updateLatestControlTimestamp();
        return success;
    }

    /**
     * Put the device property -> APIs V1.*
     * @param apiResource endpoint to use
     * @param key property to patch
     * @param value new property value
     * @param property control property that initiated the patch action
     * @return boolean indicating whether an http PATCH operation is successful or not
     * */
    private boolean putDeviceMode(String apiResource, String key, String value, String property) throws Exception {
        JsonNode response = doPut(supportedApiVersion + apiResource, String.format("%s=%s", key, value), JsonNode.class);
        boolean success = response.get("status").asInt() == 200;
        if(success){
            updateLocalControllablePropertyState(property, value);
        }
        updateLatestControlTimestamp();
        return false;
    }

    /**
     * Request ClickShare reboot
     * @return boolean indicating whether request is successful
     * @throws Exception while performing http POST
     * */
    private boolean requestReboot() throws Exception {
        updateLatestControlTimestamp();
        if(isApiV2Supported()) {
            return doPost(supportedApiVersion + OPERATIONS_REBOOT, null, JsonNode.class).get("status").asInt() == 202;
        } else {
            return doPut(supportedApiVersion + V1_RESTART_SYSTEM, "value=true", JsonNode.class).get("status").asInt() == 200;
        }
    }

    /**
     * Request ClickShare standby
     * @return boolean indicating whether request is successful
     * @throws Exception while performing http POST
     * */
    private boolean requestStandby() throws Exception {
        updateLatestControlTimestamp();
        return doPost(supportedApiVersion + OPERATIONS_STANDBY, null, JsonNode.class).get("status").asInt() == 202;
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

    /***
     * Check whether API v2 is supported by the device
     * V1 options are used otherwise.
     * @return boolean value indicating whether API v2 is supported.
     */
    private boolean isApiV2Supported(){
        return supportedApiVersion.contains("v2");
    }
}
