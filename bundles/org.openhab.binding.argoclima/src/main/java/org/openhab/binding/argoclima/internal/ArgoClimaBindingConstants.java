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

import java.time.Duration;

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

    /////////////
    // List of all Thing Type UIDs
    /////////////
    public static final ThingTypeUID THING_TYPE_ARGOCLIMA_LOCAL = new ThingTypeUID(BINDING_ID, "argoclima-local");
    public static final ThingTypeUID THING_TYPE_ARGOCLIMA_REMOTE = new ThingTypeUID(BINDING_ID, "argoclima-remote");

    /////////////
    // Thing configuration parameters
    /////////////
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
    public static final String PARAMETER_SHOW_CLEARTEXT_PASSWORDS = "showCleartextPasswords";
    public static final String PARAMETER_MATCH_ANY_INCOMING_DEVICE_IP = "matchAnyIncomingDeviceIp";

    public static final String PARAMETER_USERNAME = "username";
    public static final String PARAMETER_PASSWORD = "password";

    public static final String PARAMETER_SCHEDULE_GROUP_NAME = "schedule%d"; // 1..3
    public static final String PARAMETER_SCHEDULE_X_DAYS = PARAMETER_SCHEDULE_GROUP_NAME + "DayOfWeek";
    public static final String PARAMETER_SCHEDULE_X_ON_TIME = PARAMETER_SCHEDULE_GROUP_NAME + "OnTime";
    public static final String PARAMETER_SCHEDULE_X_OFF_TIME = PARAMETER_SCHEDULE_GROUP_NAME + "OffTime";
    public static final String PARAMETER_ACTIONS_GROUP_NAME = "actions";
    public static final String PARAMETER_RESET_TO_FACTORY_DEFAULTS = "resetToFactoryDefaults";

    /////////////
    // Thing configuration properties
    /////////////
    public static final String PROPERTY_CPU_ID = "cpuId";
    public static final String PROPERTY_LOCAL_IP_ADDRESS = "localIpAddress";
    public static final String PROPERTY_UNIT_FW = "unitFirmwareVersion";
    public static final String PROPERTY_WIFI_FW = "wifiFirmwareVersion";
    public static final String PROPERTY_LAST_SEEN = "lastSeen";
    public static final String PROPERTY_WEB_UI = "argoWebUI";
    public static final String PROPERTY_WEB_UI_USERNAME = "argoWebUIUsername";
    public static final String PROPERTY_WEB_UI_PASSWORD = "argoWebUIPassword";
    public static final String PROPERTY_WIFI_SSID = "wifiSSID";
    public static final String PROPERTY_WIFI_PASSWORD = "wifiPassword";
    public static final String PROPERTY_LOCAL_TIME = "localTime";

    /////////////
    // List of all Channel IDs
    /////////////
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
    // Note: schedule timers day of week/time setting not currently supported as channels (YAGNI), and moved to config
    public static final String CHANNEL_MODE_EX = "unsupported#modeEx";
    public static final String CHANNEL_SWING_MODE = "unsupported#swingMode";
    public static final String CHANNEL_FILTER_MODE = "unsupported#filterMode";

    public static final String CHANNEL_I_FEEL_ENABLED = "settings#iFeelEnabled";
    public static final String CHANNEL_DEVICE_LIGHTS = "settings#deviceLights";

    public static final String CHANNEL_TEMPERATURE_DISPLAY_UNIT = "settings#temperatureDisplayUnit";
    public static final String CHANNEL_ECO_POWER_LIMIT = "settings#ecoPowerLimit";

    /////////////
    // Binding's hard-coded configuration (not parameterized)
    /////////////
    /** Maximum number of failed status polls after which the device will be considered offline */
    public static final int MAX_API_RETRIES = 3;

    /**
     * Time to wait for (confirmable) command to be reported back by the device (by changing its state to the requested
     * value). If this period elapses w/o the device confirming, the command is considered not handled and REJECTED
     * (would not be retried any more, and the reported device's state will be the actual one device sent, not the
     * "in-flight" desired one)
     */
    public static final Duration PENDING_COMMAND_EXPIRE_TIME = Duration.ofSeconds(120); // TODO: THIS SHOULD MATCH MAX
                                                                                        // TRY TIME

    /**
     * Time to wait between command issue and communicating with the device. Allows to include multiple commands in one
     * device communication session (preferred).
     * Time window chosen so that it is not (too) perceptible by an user, while still enough for rules/groups to be able
     * to fit
     */
    public static final Duration SEND_COMMAND_DEBOUNCE_TIME = Duration.ofMillis(100);

    /**
     * The minimum resolution during which the command sending background thread does any meaningful action. This is
     * merely to avoid busy wait and doesn't mean the thread is doing anything of use on every cycle. There are separate
     * configurable "update" and "(re)send" frequencies governing that. This parameter only controls the lowest possible
     * resolution of those (a "tick")
     */
    public static final Duration SEND_COMMAND_DUTY_CYCLE = Duration.ofSeconds(1);

    /**
     * Whether the binding shall wait for the device confirming commands have been received (by flipping to the desired
     * state) or work in a fire&forget mode and stop tracking upon first send.
     * <p>
     * This applies only to confirmable commands (read-write) and is a default behavior of Argo's own web implementation
     *
     * @implNote This is a debug-only switch (makes little to no sense to disable it in real-world usage)
     */
    public static final boolean AWAIT_DEVICE_CONFIRMATIONS_AFTER_COMMANDS = true;

    // ArgoClimaHandlerRemote:: 60s (or not)
}
