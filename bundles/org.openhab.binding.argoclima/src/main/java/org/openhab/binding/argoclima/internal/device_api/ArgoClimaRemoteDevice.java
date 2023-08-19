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

import java.net.InetAddress;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationRemote;
import org.openhab.binding.argoclima.internal.device_api.DeviceStatus.DeviceProperties;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Argo protocol implementation for a REMOTE connection to the device
 * <p>
 * The HVAC device MUST be communicating with actual Argo servers for this method work.
 * This means the device is either directly connected to the Internet (w/o traffic intercept), or there's an
 * intercepting Stub server already running in a PASS-THROUGH mode (sniffing the messages but passing through to the
 * actual vendor's servers)
 *
 * <p>
 * Use of this mode is actually NOT recommended for advanced users as cleartext device & WiFi passwords are sent to Argo
 * servers through unencrypted HTTP connection (sic!). If the Argo UI access is desired (ex. for FW update or IR
 * remote-like experience), consider using this mode only on a dedicated WiFi network (and possibly through VPN)
 *
 * @author Mateusz Bronk - Initial contribution
 *
 */
@NonNullByDefault
public class ArgoClimaRemoteDevice extends ArgoClimaDeviceApiBase {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final InetAddress oemServerHostname;
    private final int oemServerPort;
    private final String username;
    private final String passwordMD5Hash;
    private static final Pattern REMOTE_API_RESPONSE_EXPECTED = Pattern.compile(
            "^[\\\\{][|](?<commands>[^|]+)[|](?<localIP>[^|]+)[|](?<lastSeen>[^|]+)[|][\\\\}]\\s*$",
            Pattern.CASE_INSENSITIVE); // Capture group names are used in code!

    /**
     * C-tor
     *
     * @param config The Thing configuration
     * @param client The common HTTP client used for issuing requests to the remote server
     * @param timeZoneProvider System-wide TZ provider, for parsing/displaying local dates
     * @param oemServerHostname The address of the remote (vendor's) server
     * @param oemServerPort The port of remote (vendor's) server
     * @param username The username used for authenticating to the remote server
     * @param passwordMD5 A MD5 hash of the password used for authenticating to the remote server (custom Basic-like
     *            auth)
     * @param onStateUpdate Callback to be invoked when device status gets updated(device-side channel updates)
     * @param onReachableStatusChange Callback to be invoked when device's reachability status (online/offline) changes
     * @param onDevicePropertiesUpdate Callback to invoke when device properties get refreshed
     */
    public ArgoClimaRemoteDevice(ArgoClimaConfigurationRemote config, HttpClient client,
            TimeZoneProvider timeZoneProvider, InetAddress oemServerHostname, int oemServerPort, String username,
            String passwordMD5, Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate,
            Consumer<ThingStatus> onReachableStatusChange,
            Consumer<SortedMap<String, String>> onDevicePropertiesUpdate) {
        super(config, client, timeZoneProvider, onStateUpdate, onReachableStatusChange, onDevicePropertiesUpdate,
                "REMOTE_API");
        this.oemServerHostname = oemServerHostname;
        this.oemServerPort = oemServerPort;
        this.username = username;
        this.passwordMD5Hash = passwordMD5;
    }

    @Override
    public final Pair<Boolean, String> isReachable() {
        try {
            var status = extractDeviceStatusFromResponse(pollForCurrentStatusFromDeviceSync(getDeviceStateQueryUrl()));
            this.deviceStatus.fromDeviceString(status.getCommandString());
            this.updateDevicePropertiesFromDeviceResponse(status.getProperties(), this.deviceStatus);
            status.throwIfStatusIsStale();
            return Pair.of(true, "");
        } catch (ArgoLocalApiCommunicationException e) {
            logger.debug("Device not reachable: {}", e.getMessage());
            return Pair.of(false,
                    MessageFormat.format(
                            "Failed to communicate with Argo HVAC remote device at [http://{0}:{1,number,#}{2}]. {3}",
                            this.getDeviceStateQueryUrl().getHost(),
                            this.getDeviceStateQueryUrl().getPort() != -1 ? this.getDeviceStateQueryUrl().getPort()
                                    : this.getDeviceStateQueryUrl().getDefaultPort(),
                            this.getDeviceStateQueryUrl().getPath(), e.getMessage()));
        }
    }

    @Override
    protected URL getDeviceStateQueryUrl() {
        // Hard-coded values are part of ARGO protocol
        return newUrl(this.oemServerHostname.getHostName(), this.oemServerPort, "/UI/UI.php",
                String.format("CM=UI_TC&USN=%s&PSW=%s&HMI=&UPD=0", this.username, this.passwordMD5Hash));
    }

    @Override
    protected URL getDeviceStateUpdateUrl() {
        // Hard-coded values are part of ARGO protocol
        return newUrl(this.oemServerHostname.getHostName(), this.oemServerPort, "/UI/UI.php",
                String.format("CM=UI_TC&USN=%s&PSW=%s&HMI=%s&UPD=1", this.username, this.passwordMD5Hash,
                        this.deviceStatus.getDeviceCommandStatus()));
    }

    @Override
    protected DeviceStatus extractDeviceStatusFromResponse(String apiResponse)
            throws ArgoLocalApiCommunicationException {
        if (apiResponse.isBlank()) {
            throw new ArgoLocalApiCommunicationException(
                    "The remote API response was empty. Check username and password");
        }

        var matcher = REMOTE_API_RESPONSE_EXPECTED.matcher(apiResponse);
        if (!matcher.matches()) {
            throw new ArgoLocalApiCommunicationException(
                    String.format("The remote API response [%s] was not recognized", apiResponse));
        }

        // Group names must match regex above
        var properties = new DeviceProperties(matcher.group("localIP"), matcher.group("lastSeen"),
                Optional.of(getWebUiUrl(this.oemServerHostname.getHostName(), this.oemServerPort)));

        return new DeviceStatus(matcher.group("commands"), properties);
    }

    /**
     * Return the full URL to the Vendor's web application
     *
     * @param hostName The OEM server host
     * @param port The OEM server port
     * @return Full URL to the UI webapp
     */
    public static URL getWebUiUrl(String hostName, int port) {
        return newUrl(hostName, port, "/UI/WEBAPP/webapp.php", "");
    }
}
