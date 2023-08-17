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
package org.openhab.binding.argoclima.internal.device_api;

import java.io.EOFException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.openhab.binding.argoclima.internal.ArgoClimaBindingConstants;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationRemote;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSideUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.protocol.ArgoApiDataElement;
import org.openhab.binding.argoclima.internal.device_api.protocol.ArgoDeviceStatus;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.IArgoElement;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common implementation of Argo API
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public abstract class ArgoClimaDeviceApiBase implements IArgoClimaDeviceAPI {
    private final Logger logger = LoggerFactory.getLogger(ArgoClimaDeviceApiBase.class);
    // public final InetAddress ipAddress;
    // private final Optional<InetAddress> localIpAddress;
    // private final Optional<String> cpuId;
    // public int port;
    private final HttpClient client;
    protected final TimeZoneProvider timeZoneProvider;
    protected ArgoDeviceStatus deviceStatus;
    protected Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate;
    protected Consumer<ThingStatus> onReachableStatusChange;
    protected Consumer<SortedMap<String, String>> onDevicePropertiesUpdate;
    // protected HashMap<String, String> deviceProperties;
    protected SortedMap<String, String> deviceProperties;
    private final String remoteEndName;

    /**
     * C-tor
     *
     * @param config The configuration class (common part)
     * @param client The common HTTP client used for making connections from OH to the device
     * @param timeZoneProvider The common TZ provider
     * @param onStateUpdate Callback to invoke on device-side state update (one or more of channels)
     * @param onReachableStatusChange Callback to invoke on device-side status update (ex. going OFFLINE)
     * @param onDevicePropertiesUpdate Callback to invoke on device-side dynamic property update (ex. lastSeen)
     * @param remoteEndName The name of the "remote end" party, for use in logging
     */
    public ArgoClimaDeviceApiBase(ArgoClimaConfigurationBase config, HttpClient client,
            TimeZoneProvider timeZoneProvider, Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate,
            Consumer<ThingStatus> onReachableStatusChange, Consumer<SortedMap<String, String>> onDevicePropertiesUpdate,
            String remoteEndName) {
        this.client = client;
        this.timeZoneProvider = timeZoneProvider;
        this.deviceStatus = new ArgoDeviceStatus(config);
        this.onStateUpdate = onStateUpdate;
        this.onReachableStatusChange = onReachableStatusChange;
        this.onDevicePropertiesUpdate = onDevicePropertiesUpdate;
        this.deviceProperties = new TreeMap<String, String>();
        this.remoteEndName = remoteEndName.isBlank() ? "DEVICE" : remoteEndName.trim().toUpperCase();
    }

    protected abstract URL getDeviceStateQueryUrl();

    protected abstract URL getDeviceStateUpdateUrl();

    protected final static URL uriToURL(String uriStr) {
        try {
            return new URL(uriStr);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to build url from: " + uriStr, e);
        }
    }

    @Override
    public final SortedMap<String, String> getCurrentDeviceProperties() {
        return Collections.unmodifiableSortedMap(this.deviceProperties);
    }

    protected String pollForCurrentStatusFromDeviceSync(URL url) throws ArgoLocalApiCommunicationException {
        // TODO: consider separate http client and timeouts
        try {
            logger.info("Communication: OPENHAB --> {}: [GET {}]", remoteEndName, url);

            ContentResponse resp = this.client.GET(url.toString());

            logger.info("   [response]: OPENHAB <-- {}: [{} {} {} - {} bytes], body=[{}]", remoteEndName,
                    resp.getVersion(), resp.getStatus(), resp.getReason(), resp.getContent().length,
                    resp.getContentAsString());

            if (resp.getStatus() != 200) {
                throw new ArgoLocalApiCommunicationException(String.format(
                        "API request yielded invalid response status %d %s (expected HTTP 200 OK). URL was: %s",
                        resp.getStatus(), resp.getReason(), url));
            }
            // logger.warn("Got response {}", resp.getStatus());
            return resp.getContentAsString();
        } catch (InterruptedException ex) {
            logger.info("Interrupted...");
            return "";
        } catch (ExecutionException ex) {
            var cause = Optional.ofNullable(ex.getCause());
            if (cause.isPresent() && cause.get() instanceof EOFException) {
                // logger.warn("Cause is: EOF: {}", ((EOFException) cause).getMessage());
                throw new ArgoLocalApiCommunicationException(
                        "Cause is: EOF: " + ((EOFException) cause.get()).getMessage(), cause.get());
            }
            throw new ArgoLocalApiCommunicationException(
                    "Device communication error: " + Objects.requireNonNullElse(ex.getCause(), ex).getMessage(),
                    ex.getCause());
        } catch (TimeoutException e) {
            throw new ArgoLocalApiCommunicationException("Timeout: " + e.getMessage(), e);
        }
    }

    /**
     * Represents the current device status, as-communicated by the device (either push or pull model)
     * <p>
     * Includes both the "raw" {@link #getCommandString() commandString} as well as {@link #getProperties() properties}
     *
     * @author Mateusz Bronk - Initial contribution
     */
    static class DeviceStatus {
        /**
         * Helper class for dealing with device properties
         *
         * @author Mateusz Bronk - Initial contribution
         */
        static class DeviceProperties {
            private final Logger logger = LoggerFactory.getLogger(DeviceProperties.class);
            private Optional<String> localIP;
            private Optional<OffsetDateTime> lastSeen;
            private Optional<URL> vendorUiUrl;
            private Optional<String> cpuId = Optional.empty();
            private Optional<String> webUiUsername = Optional.empty();
            private Optional<String> webUiPassword = Optional.empty();
            private Optional<String> unitFWVersion = Optional.empty();
            private Optional<String> wifiFWVersion = Optional.empty();
            private Optional<String> wifiSSID = Optional.empty();
            private Optional<String> wifiPassword = Optional.empty();
            private Optional<String> localTime = Optional.empty();

            public DeviceProperties(String localIP, String lastSeenStr, Optional<URL> vendorUiAddress) {
                this.localIP = lastSeenStr.isEmpty() ? Optional.empty() : Optional.of(localIP);
                this.vendorUiUrl = vendorUiAddress;
                if (lastSeenStr.isEmpty()) {
                    this.lastSeen = Optional.empty();
                } else {
                    try {
                        // logger.warn("STR is: {}", lastSeenStr);
                        this.lastSeen = Optional
                                .of(OffsetDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(lastSeenStr)));
                        // logger.warn("Parsed is: {}", this.lastSeen.get().toString());
                        // logger.warn("Now is: {}", Instant.now().toString());
                    } catch (DateTimeException ex) {
                        // TODO: seems bad
                        logger.debug("Failed to parse [LastSeen] timestamp. Exception: {}", ex.getMessage());
                        this.lastSeen = Optional.empty();
                    }
                }
            }

            public DeviceProperties(OffsetDateTime lastSeen) {
                this.localIP = Optional.empty();
                this.lastSeen = Optional.of(lastSeen);
                this.vendorUiUrl = Optional.empty();
            }

            public DeviceProperties(OffsetDateTime lastSeen, DeviceSideUpdateDTO properties) {
                this.localIP = Optional.of(properties.setup.localIP.orElse(properties.deviceIp));
                this.lastSeen = Optional.of(lastSeen);
                this.vendorUiUrl = Optional.of(ArgoClimaRemoteDevice.getWebUiUrl(properties.remoteServerId, 80));
                this.cpuId = Optional.of(properties.cpuId);
                this.webUiUsername = Optional.of(properties.setup.username.orElse(properties.username));
                this.webUiPassword = properties.setup.password;
                this.unitFWVersion = Optional.of(properties.setup.unitVersionInstalled.orElse(properties.unitFirmware));
                this.wifiFWVersion = Optional.of(properties.setup.wifiVersionInstalled.orElse(properties.wifiFirmware));
                this.wifiSSID = properties.setup.wifiSSID;
                this.wifiPassword = properties.setup.wifiPassword;
                this.localTime = properties.setup.localTime;
            }

            public String getLocalIP() {
                return localIP.orElse("UNKNOWN");
            }

            public String getLastSeenStr(TimeZoneProvider timeZoneProvider) {
                if (lastSeen.isEmpty()) {
                    return "UNKNOWN";
                }
                var timeAtZone = lastSeen.get().atZoneSameInstant(timeZoneProvider.getTimeZone());// .atZone(zone);
                return timeAtZone.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG));
                // return lastSeen.get().atZone(timeZoneProvider.getTimeZone())
                // .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            public OffsetDateTime getLastSeen() {
                return lastSeen.orElse(OffsetDateTime.MIN);
            }

            public Duration getLastSeenDelta() {
                return Duration.between(getLastSeen().toInstant(), Instant.now());
            }

            SortedMap<String, String> asPropertiesRaw(TimeZoneProvider timeZoneProvider) {
                var result = new TreeMap<String, String>();

                if (this.lastSeen.isPresent()) {
                    result.put(ArgoClimaBindingConstants.PROPERTY_LAST_SEEN, this.getLastSeenStr(timeZoneProvider));
                }
                if (this.localIP.isPresent()) {
                    result.put(ArgoClimaBindingConstants.PROPERTY_LOCAL_IP_ADDRESS, this.getLocalIP());
                }
                if (this.vendorUiUrl.isPresent()) {
                    result.put(ArgoClimaBindingConstants.PROPERTY_WEB_UI,
                            this.vendorUiUrl.map(x -> x.toString()).orElse("N/A"));
                }

                this.cpuId.map(value -> result.put(ArgoClimaBindingConstants.PROPERTY_CPU_ID, value));
                this.webUiUsername.map(value -> result.put(ArgoClimaBindingConstants.PROPERTY_WEB_UI_USERNAME, value));
                this.webUiPassword.map(value -> result.put(ArgoClimaBindingConstants.PROPERTY_WEB_UI_PASSWORD, value));
                this.unitFWVersion.map(value -> result.put(ArgoClimaBindingConstants.PROPERTY_UNIT_FW, value));
                this.wifiFWVersion.map(value -> result.put(ArgoClimaBindingConstants.PROPERTY_WIFI_FW, value));
                this.wifiSSID.map(value -> result.put(ArgoClimaBindingConstants.PROPERTY_WIFI_SSID, value));
                this.wifiPassword.map(value -> result.put(ArgoClimaBindingConstants.PROPERTY_WIFI_PASSWORD, value));
                this.localTime.map(value -> result.put(ArgoClimaBindingConstants.PROPERTY_LOCAL_TIME, value));
                return Collections.unmodifiableSortedMap(result);
            }
        }

        private String commandString;
        private DeviceProperties properties;
        // private Map<DevicePropertyType, String> properties;

        public String getCommandString() {
            return this.commandString;
        }

        public DeviceProperties getProperties() {
            return this.properties;
        }

        public DeviceStatus(String commandString, DeviceProperties properties) {
            this.commandString = commandString;
            this.properties = properties;
        }

        /**
         * Constructs DeviceStatus from just-received status response (live poll response)
         *
         * @param commandString The command string received
         */
        public DeviceStatus(String commandString, OffsetDateTime lastSeenDateTime) {
            this(commandString, new DeviceProperties(lastSeenDateTime));
        }

        public void throwIfStatusIsStale() throws ArgoLocalApiCommunicationException {
            var delta = this.getProperties().getLastSeenDelta();
            if (delta.toSeconds() > ArgoClimaConfigurationRemote.LAST_SEEN_UNAVAILABILITY_THRESHOLD.toSeconds()) {
                throw new ArgoLocalApiCommunicationException(MessageFormat.format(
                        "Device was last seen {0} mins ago (threshold is set at {1} min). Please ensure the HVAC is connected to WiFi and communicating with Argo servers",
                        delta.toMinutes(),
                        ArgoClimaConfigurationRemote.LAST_SEEN_UNAVAILABILITY_THRESHOLD.toMinutes()));
            }
        }

    }

    /**
     * Extract device status from just-polled API result (local or remote)
     *
     * @param apiResponse
     * @return
     * @throws ArgoLocalApiCommunicationException
     */
    protected abstract DeviceStatus extractDeviceStatusFromResponse(String apiResponse)
            throws ArgoLocalApiCommunicationException;

    @Override
    public Map<ArgoDeviceSettingType, State> queryDeviceForUpdatedState() throws ArgoLocalApiCommunicationException {
        // this.client.setAddressResolutionTimeout(10 *1000);
        // TODO: if not reachable, calling it makes zero sense!

        var deviceResponse = extractDeviceStatusFromResponse(
                pollForCurrentStatusFromDeviceSync(getDeviceStateQueryUrl()));
        this.deviceStatus.fromDeviceString(deviceResponse.commandString);
        this.updateDevicePropertiesFromDeviceResponse(deviceResponse.getProperties(), this.deviceStatus);
        deviceResponse.throwIfStatusIsStale();
        // http://31.14.128.210/UI/WEBAPP/webapp.php

        // this.deviceProperties.putAll(deviceResponse.getProperties().asPropertiesRaw(this.timeZoneProvider));
        // this.deviceProperties.putAll(Map.of(ArgoClimaBindingConstants.PROPERTY_UNIT_FW,
        // this.deviceStatus.getSetting(ArgoDeviceSettingType.UNIT_FIRMWARE_VERSION).toString()));
        // this.onDevicePropertiesUpdate.accept(properties);
        return this.deviceStatus.getCurrentStateMap();
    }

    protected void updateDevicePropertiesFromDeviceResponse(DeviceStatus.DeviceProperties metadata,
            ArgoDeviceStatus status) {

        var metaProperties = metadata.asPropertiesRaw(this.timeZoneProvider);
        var responseProperties = Map.of(ArgoClimaBindingConstants.PROPERTY_UNIT_FW,
                this.deviceStatus.getSetting(ArgoDeviceSettingType.UNIT_FIRMWARE_VERSION).toString(false));

        synchronized (this) {
            // this.deviceProperties.clear();
            this.deviceProperties.putAll(metaProperties);
            this.deviceProperties.putAll(responseProperties);
        }
    }

    @Override
    public Map<ArgoDeviceSettingType, State> getLastStateReadFromDevice() {
        return this.deviceStatus.getCurrentStateMap();
    }

    @Override
    public void sendCommandsToDevice() throws ArgoLocalApiCommunicationException {
        var deviceResponse = pollForCurrentStatusFromDeviceSync(getDeviceStateUpdateUrl());

        // TODO: notfy
        this.deviceStatus.getItemsWithPendingUpdates().forEach(x -> x.notifyCommandSent());
        logger.info("State update command finished. Device response: {}", deviceResponse);
    }

    @Override
    public boolean handleSettingCommand(ArgoDeviceSettingType settingType, Command command) {
        return this.deviceStatus.getSetting(settingType).handleCommand(command);
    }

    @Override
    public State getCurrentStateNoPoll(ArgoDeviceSettingType settingType) {
        return this.deviceStatus.getSetting(settingType).getState();
    }

    @Override
    public boolean hasPendingCommands() {
        var itemsWithPendingUpdates = this.deviceStatus.getItemsWithPendingUpdates();
        logger.info("Items to update: {}", itemsWithPendingUpdates);
        // return !this.deviceStatus.getItemsWithPendingUpdates().isEmpty();
        return !itemsWithPendingUpdates.isEmpty();
        // return this.deviceStatus.hasUpdatesPending();
    }

    @Override
    public List<ArgoApiDataElement<IArgoElement>> getItemsWithPendingUpdates() {
        return this.deviceStatus.getItemsWithPendingUpdates();
    }
}
