/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.argoclima.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link ArgoClimaBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaBindingConstants {

    public static final String BINDING_ID = "argoclima";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ARGOCLIMA_LOCAL = new ThingTypeUID(BINDING_ID, "argoclima");
    public static final ThingTypeUID THING_TYPE_ARGOCLIMA_REMOTE = new ThingTypeUID(BINDING_ID, "argoclima-remote");

    // Thing configuration parameters
    public static final String PARAMETER_HOSTNAME = "hostname"; // not used
    public static final String PARAMETER_LOCAL_DEVICE_IP = "localDeviceIP";
    public static final String PARAMETER_LOCAL_DEVICE_PORT = "localDevicePort"; // not used
    public static final String PARAMETER_DEVICE_CPU_ID = "deviceCpuId";
    public static final String PARAMETER_CONNECTION_MODE = "connectionMode"; // LOCAL_CONNECTION | REMOTE_API_STUB |
                                                                             // REMOTE_API_PROXY
    public static final String PARAMETER_USE_LOCAL_CONNECTION = "useLocalConnection";
    public static final String PARAMETER_REFRESH_INTERNAL = "refreshInterval";
    public static final String PARAMETER_STUB_SERVER_PORT = "stubServerPort";
    public static final String PARAMETER_STUB_SERVER_LISTEN_ADDRESSES = "stubServerListenAddresses";
    public static final String PARAMETER_OEM_SERVER_PORT = "oemServerPort";
    public static final String PARAMETER_OEM_SERVER_ADDRESS = "oemServerAddress";

    public static final String PARAMETER_USERNAME = "username";
    public static final String PARAMETER_PASSWORD = "password";

    public static final String PARAMETER_SCHEDULE_GROUP_NAME = "schedule%d"; // 1..3
    public static final String PARAMETER_SCHEDULE_X_DAYS = PARAMETER_SCHEDULE_GROUP_NAME + "DayOfWeek";
    public static final String PARAMETER_SCHEDULE_X_ON_TIME = PARAMETER_SCHEDULE_GROUP_NAME + "OnTime";
    public static final String PARAMETER_SCHEDULE_X_OFF_TIME = PARAMETER_SCHEDULE_GROUP_NAME + "OffTime";
    public static final String PARAMETER_ACTIONS_GROUP_NAME = "actions";
    public static final String PARAMETER_RESET_TO_FACTORY_DEFAULTS = "resetToFactoryDefaults";

    // Thing configuration properties
    public static final String PROPERTY_CPU_ID = "cpuId";
    public static final String PROPERTY_LOCAL_IP_ADDRESS = "localIpAddress";
    public static final String PROPERTY_UNIT_FW = "unitFirmwareVersion";
    public static final String PROPERTY_WIFI_FW = "wifiFirmwareVersion";
    public static final String PROPERTY_LAST_SEEN = "lastSeen";

    // List of all Channel ids
    public static final String CHANNEL_POWER = "acControls#power";
    public static final String CHANNEL_MODE = "acControls#mode";
    public static final String CHANNEL_SET_TEMPERATURE = "acControls#setTemperature";
    public static final String CHANNEL_CURRENT_TEMPERATURE = "acControls#currentTemperature";
    public static final String CHANNEL_FAN_SPEED = "acControls#fanSpeed";
    public static final String CHANNEL_ECO_MODE = "modes#ecoMode";
    public static final String CHANNEL_TURBO_MODE = "modes#turboMode";
    public static final String CHANNEL_NIGHT_MODE = "modes#nightMode";
    public static final String CHANNEL_ACTIVE_TIMER = "timers#activeTimer";
    public static final String CHANNEL_DELAY_TIMER = "timers#delayTimer";
    // public static final String CHANNEL_SCHEDULE_TIMER_1 = "timers#scheduleTimer1";
    // public static final String CHANNEL_SCHEDULE_TIMER_2 = "timers#scheduleTimer2";
    // public static final String CHANNEL_SCHEDULE_TIMER_3 = "timers#scheduleTimer3";
    public static final String CHANNEL_MODE_EX = "unsupported#modeEx";
    public static final String CHANNEL_SWING_MODE = "unsupported#swingMode";
    public static final String CHANNEL_FILTER_MODE = "unsupported#filterMode";

    public static final String CHANNEL_I_FEEL_ENABLED = "settings#iFeelEnabled";
    public static final String CHANNEL_DEVICE_LIGHTS = "settings#deviceLights";

    public static final String CHANNEL_TEMPERATURE_DISPLAY_UNIT = "settings#temperatureDisplayUnit";
    public static final String CHANNEL_ECO_POWER_LIMIT = "settings#ecoPowerLimit";
    // public static final String CHANNEL_RESET_TO_FACTORY_DEFAULTS = "settings#reset"; // todo->move to settings (and
    // require confirmation to set)
    // Toggle?

    // <channel id="currentTime" typeId="currentTime-channel"/>
    // <channel id="currentWeekday" typeId="currentWeekday-channel"/>
    // <channel id="firmwareVersion" typeId="firmwareVersion-channel"/>
    // <channel id="reset" typeId="reset-channel"/>
    // <channel id="health" typeId="health-channel"/>
    // TODO: not all channels are here :/

    public static final int REFRESH_INTERVAL_SEC = 5;
    public static final int MAX_API_RETRIES = 3;
}
