/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

    // Thing configuration parameters
    public static final String PARAMETER_HOSTNAME = "hostname";
    public static final String PARAMETER_LOCAL_DEVICE_IP = "localDeviceIP";
    public static final String PARAMETER_DEVICE_CPU_ID = "deviceCpuId";
    // // Thing configuration items
    // public static final String PROPERTY_IP = "ipAddress";
    public static final String PROPERTY_CPU_ID = "cpuId";
    public static final String PROPERTY_LOCAL_IP_ADDRESS = "localIpAddress";
    public static final String PROPERTY_UNIT_FW = "unitFirmwareVersion";
    public static final String PROPERTY_WIFI_FW = "wifiFirmwareVersion";

    // List of all Channel ids
    public static final String CHANNEL_1 = "channel1";
    public static final String CHANNEL_POWER = "acControls#power";
    public static final String CHANNEL_MODE = "acControls#mode";
    public static final String CHANNEL_SET_TEMPERATURE = "acControls#setTemperature";
    public static final String CHANNEL_CURRENT_TEMPERATURE = "acControls#currentTemperature";
    public static final String CHANNEL_FAN_SPEED = "acControls#fanSpeed";
    public static final String CHANNEL_ECO_MODE = "modes#ecoMode";
    public static final String CHANNEL_TURBO_MODE = "modes#turboMode";
    public static final String CHANNEL_NIGHT_MODE = "modes#nightMode";
    public static final String CHANNEL_ACTIVE_TIMER = "activeTimer";
    public static final String CHANNEL_DELAY_TIMER = "delayTimer";
    public static final String CHANNEL_SCHEDULE_TIMER_1 = "scheduleTimer1";
    public static final String CHANNEL_SCHEDULE_TIMER_2 = "scheduleTimer2";
    public static final String CHANNEL_SCHEDULE_TIMER_3 = "scheduleTimer3";
    public static final String CHANNEL_MODE_EX = "modeEx";
    public static final String CHANNEL_SWING_MODE = "swingMode";
    public static final String CHANNEL_FILTER_MODE = "filterMode";

    public static final String CHANNEL_I_FEEL_ENABLED = "settings#iFeelEnabled";
    public static final String CHANNEL_DEVICE_LIGHTS = "settings#deviceLights";

    public static final int REFRESH_INTERVAL_SEC = 5;
    public static final int MAX_API_RETRIES = 3;
}
