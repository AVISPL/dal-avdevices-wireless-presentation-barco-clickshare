/*
 * Copyright (c) 2020 AVI-SPL Inc. All Rights Reserved.
 */
package com.avispl.symphony.dal.communicator.barco.clickshare;

import com.google.common.collect.ImmutableMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class BarcoClickShareProperties {
    public static final String API_SUPPORTED_VERSIONS = "supportedVersions";

    public static final Map<Integer, String> DEVICE_STATUSES = ImmutableMap.of(0, "OK", 1, "Warning", 2, "Error");
    public static final List<String> TIMEOUTS = Arrays.asList("Infinite", "1", "5", "10", "15", "30", "45", "60");
    public static final List<String> CSE800_DISPLAY_TIMEOUTS = Arrays.asList("Infinite", "5", "10", "15", "30", "45", "60");
    public static final List<String> AUDIO_OUTPUT_MODES = Arrays.asList("Jack", "HDMI", "SPDIF");
    public static final List<String> ENERGY_MODES = Arrays.asList("eco_standby", "networked_standby");
    public static final List<String> CSE200_ENERGY_MODES = Collections.singletonList("eco_standby");
    public static final List<String> CSE800_ENERGY_MODES_LABELS = Arrays.asList("eco_standby", "networked_standby (enable CNI)");
    public static final List<String> SOFTWARE_UPDATE_TYPES = Arrays.asList("AUTOMATIC", "NOTIFY", "OFF");
    public static final List<String> SCREENSAVER_MODE_NAMES = Arrays.asList("Default", "HDMI");

    /*V1 ENDPOINTS*/
    public static final String V1_RESTART_SYSTEM = "Configuration/RestartSystem";
    public static final String V1_DISPLAY = "Display";
    public static final String V1_DISPLAY_TIMEOUT = "Display/DisplayTimeout";
    public static final String V1_DISPLAY_STANDBY_STATE = "Display/StandbyState";
    public static final String V1_DISPLAY_HOT_PLUG = "Display/HotPlug";
    public static final String V1_SCREENSAVER_TIMEOUT = "Display/ScreenSaverTimeout";
    public static final String V1_SHOW_WALLPAPER = "Display/ShowWallpaper";
    public static final String V1_DISPLAY_RESOLUTION = "Display/OutputTable/%s/Resolution";
    public static final String V1_AUDIO_ENABLED = "Audio/Enabled";
    public static final String V1_ON_SCREEN_TEXT = "OnScreenText";
    public static final String V1_ON_SCREEN_TEXT_WELCOME_MESSAGE = "OnScreenText/WelcomeMessage";
    public static final String V1_ON_SCREEN_TEXT_LANGUAGE = "OnScreenText/Language";
    public static final String V1_ON_SCREEN_TEXT_MEETING_ROOM_NAME = "OnScreenText/MeetingRoomName";
    public static final String V1_ON_SCREEN_TEXT_SHOW_MEETING_ROOM = "OnScreenText/ShowMeetingRoomInfo";
    public static final String V1_ON_SCREEN_TEXT_SHOW_NETWORK = "OnScreenText/ShowNetworkInfo";

    // Сan it be replaced by "DeviceInfo" only?...
    public static final String V1_DEVICE_INFO = "DeviceInfo";

    /*V1.5 ENDPOINTS*/
    public static final String V1_5_ENERGY_MODE = "Standby/EnergyMode";
    public static final String V1_5_AUDIO_OUTPUT = "Audio/Output";

    /*V1.6 ENDPOINTS*/
    public static final String V1_6_REQUEST_STANDBY = "Standby/RequestStandby";
    public static final String V1_6_SYSTEM_STATE = "Standby/SystemState";

    /*V1.7 ENDPOINTS*/
    public static final String V1_7_BLACKBOARD_SAVING = "Blackboard/SavingAllowed";
    public static final String V1_7_UPDATE_TYPE = "Software/AutoUpdate/UpdateType";

    /*v1.8 ENDPOINTS*/
    public static final String V1_8_ENABLE_AIRPLAY = "ClientAccess/EnableAirplay";
    public static final String V1_8_ENABLE_CLICKSHARE_APP = "ClientAccess/EnableClickShareApp";
    public static final String V1_8_ENABLE_GOOGLECAST = "ClientAccess/EnableGoogleCast";
    public static final String V1_8_ENABLE_OVER_LAN = "ClientAccess/EnableOverLAN";

    /*v1.11 ENDPOINTS*/
    public static final String V1_11_CPU_FAN_SPEED = "DeviceInfo/Sensors/CpuFanSpeed";
    public static final String V1_11_DISPLAY_CEC = "Display/CEC";
    public static final String V1_11_WLAN_IP_ADDRESS = "Network/Wlan/IpAddress";
    public static final String V1_11_WLAN_SUBNET_MASK = "Network/Wlan/SubnetMask";

    /*v1.13 ENDPOINTS*/
    public static final String V1_13_ENABLE_MIRACAST = "ClientAccess/EnableMiracast";

    /*v1.14 ENDPOINTS*/
    public static final String V1_14_SCREEN_SAVER_MODE = "Display/ScreenSaverMode";

    /*V2 ENDPOINTS*/
    public static final String POWER_MANAGEMENT = "configuration/system/power-management";
    public static final String OPERATIONS_REBOOT = "operations/reboot";
    public static final String OPERATIONS_STANDBY = "operations/standby";
    public static final String OPERATIONS_SUPPORTED = "operations/supported";
    public static final String CONFIGURATION_VIDEO = "configuration/video";
    public static final String CONFIGURATION_AUDIO = "configuration/audio";
    public static final String CONFIGURATION_NETWORK = "configuration/system/network";
    public static final String DEVICE_IDENTITY = "configuration/system/device-identity";
    public static final String PERSONALIZATION = "configuration/personalization";
    public static final String FEATURES_MIRACAST = "configuration/features/miracast";
    public static final String FEATURES_GOOGLECAST = "configuration/features/google-cast";
    public static final String FEATURES_BLACKBOARD = "configuration/features/blackboard";
    public static final String FEATURES_AIRPLAY = "configuration/features/airplay";

    public static final String POWER_MODE_NAME = "Power Management#Power Mode";
    public static final String POWER_STATUS_NAME = "Power Management#Power Status";
    public static final String STANDBY_TIMEOUT_NAME = "Power Management#Standby Timeout (min)";
    public static final String ONSCREEN_LANGUAGE_NAME = "On Screen Text#Language";
    public static final String ONSCREEN_WELCOME_MESSAGE_NAME = "On Screen Text#Welcome Message";
    public static final String ONSCREEN_MEETING_ROOM_NAME = "On Screen Text#Meeting Room Name";
    public static final String ONSCREEN_MEETING_ROOM_INFO_NAME = "On Screen Text#Show Meeting Room Info";
    public static final String ONSCREEN_NETWORK_INFO_NAME = "On Screen Text#Show Network Info";

    public static final String DISPLAY_TIMEOUT_NAME = "Display#Display Timeout";
    public static final String DISPLAY_STANDBY_NAME = "Display#Standby";
    public static final String DISPLAY_HOTPLUG_NAME = "Display#Hot Plug";
    public static final String DISPLAY_WALLPAPER = "Display#Show Wallpaper";
    public static final String SCREENSAVER_MODE_NAME = "Display#Screensaver Mode";
    public static final String SCREENSAVER_TIMEOUT_NAME = "Display#Screensaver Timeout";
    public static final String VIDEO_MODE_NAME = "Video Mode";
    public static final String MIRACAST_NAME = "Features#Miracast";
    public static final String GOOGLECAST_NAME = "Features#Googlecast";
    public static final String CLICKSHARE_NAME = "Features#ClickShare App";
    public static final String ACCESS_OVER_LAN_NAME = "Features#Access Over LAN";
    public static final String BLACKBOARD_SAVING_NAME = "Features#Blackboard saving";
    public static final String SOFTWARE_UPDATE_NAME = "Software Update Type";
    public static final String AIRPLAY_NAME = "Features#Airplay";
    public static final String AUDIO_NAME = "Audio";
    public static final String AUDIO_OUTPUT_NAME = "Audio Output";
    public static final String LANGUAGE_NAME = "Personalization#Language";
    public static final String WELCOME_MESSAGE_NAME = "Personalization#Welcome message";
    public static final String REBOOT_NAME = "Reboot";
    public static final String STANDBY_NAME = "Standby";
    public static final String ECO_STANDBY_V2 = "EcoStandby";
    public static final String ECO_STANDBY_V1 = "eco_standby";
    public static final String DEEP_STANDBY_V2 = "DeepStandby";
    public static final String DEEP_STANDBY_V1 = "deep_standby";

    /*DEVICE MODELS*/
    public static final String CSE800 = "CSE-800";
    public static final String CSE200 = "CSE-200";
}
