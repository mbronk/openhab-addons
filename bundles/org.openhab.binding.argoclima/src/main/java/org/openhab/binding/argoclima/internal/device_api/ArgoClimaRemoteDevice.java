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
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.URIUtil;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationRemote;
import org.openhab.binding.argoclima.internal.device_api.ArgoClimaDeviceApiBase.DeviceStatus.DeviceProperties;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSidePostRtUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mateusz Bronk - Initial contribution
 *
 */
@NonNullByDefault
public class ArgoClimaRemoteDevice extends ArgoClimaDeviceApiBase {
    private static final Logger logger = LoggerFactory.getLogger(ArgoClimaRemoteDevice.class);

    public final InetAddress oemServerHostname;
    public final int oemServerPort;
    public final String username;
    public final String passwordMD5Hash;
    final static Pattern REMOTE_API_RESPONSE_EXPECTED = Pattern.compile(
            "^[\\\\{][|](?<commands>[^|]+)[|](?<localIP>[^|]+)[|](?<lastSeen>[^|]+)[|][\\\\}]\\s*$",
            Pattern.CASE_INSENSITIVE);

    public ArgoClimaRemoteDevice(ArgoClimaConfigurationRemote config, HttpClient client,
            TimeZoneProvider timeZoneProvider, InetAddress oemServerHostname, int oemServerPort, String username,
            String passwordMD5, Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate,
            Consumer<ThingStatus> onReachableStatusChange, Consumer<Map<String, String>> onDevicePropertiesUpdate) {
        super(config, client, timeZoneProvider, onStateUpdate, onReachableStatusChange, onDevicePropertiesUpdate,
                "REMOTE_API");
        this.oemServerHostname = oemServerHostname;
        this.oemServerPort = oemServerPort;
        this.username = username;
        this.passwordMD5Hash = passwordMD5;
    }

    // private static boolean checkLastCommunicationState(DeviceStatus status) {
    // var delta = status.getProperties().getLastSeenDelta();
    //
    //
    // if(delta.toMinutes() > 30) {
    // return false;
    // }
    // // TODO
    // return true;
    // }

    @Override
    public final Pair<Boolean, String> isReachable() {
        // TODO: last successful comms also may qualify?

        try {
            var status = extractDeviceStatusFromResponse(pollForCurrentStatusFromDeviceSync(getDeviceStateQueryUrl()));
            // var delta = status.getProperties().getLastSeenDelta();
            // logger.warn("Last comms state: {} - {}", delta, delta.toMinutes());
            //
            // if (delta.toMinutes() > 30) {
            // return Pair.of(false, MessageFormat.format("Device was last seen {0} minutes ago", delta.toMinutes()));
            // }
            //
            // if (!checkLastCommunicationState(status)) {
            // return Pair.of(false, "Device was Last Seen xx h ago"); // TODO
            // }
            this.deviceStatus.fromDeviceString(status.getCommandString());
            this.updateDevicePropertiesFromDeviceResponse(status.getProperties(), this.deviceStatus);

            return Pair.of(true, "");
        } catch (ArgoLocalApiCommunicationException e) {
            logger.warn("Device not reachable: {}", e.getMessage());
            return Pair.of(false,
                    MessageFormat.format(
                            "Failed to communicate with Argo HVAC device at [http://{0}:{1,number,#}{2}]. {3}",
                            this.getDeviceStateQueryUrl().getHost(),
                            this.getDeviceStateQueryUrl().getPort() != -1 ? this.getDeviceStateQueryUrl().getPort()
                                    : this.getDeviceStateQueryUrl().getDefaultPort(),
                            this.getDeviceStateQueryUrl().getPath(), e.getMessage()));
        }
    }

    @Override
    protected URL getDeviceStateQueryUrl() {
        // http://31.14.128.210/UI/UI.php?CM=UI_TC&USN=&PSW=MD5&UPD=0&HMI=
        return uriToURL(URIUtil.newURI("http", this.oemServerHostname.getHostName(), this.oemServerPort, "/UI/UI.php",
                String.format("CM=UI_TC&USN=%s&PSW=%s&HMI=&UPD=0", this.username, this.passwordMD5Hash)));
    }

    @Override
    protected URL getDeviceStateUpdateUrl() {
        return uriToURL(URIUtil.newURI("http", this.oemServerHostname.getHostName(), this.oemServerPort, "/UI/UI.php",
                String.format("CM=UI_TC&USN=%s&PSW=%s&HMI=%s&UPD=1", this.username, this.passwordMD5Hash,
                        this.deviceStatus.getDeviceCommandStatus())));
    }

    @Override
    public void updateDeviceStateFromPushRequest(String hmiStringFromDevice, String deviceIP, String deviceCpuId) {
        throw new RuntimeException(); // TODO
    }

    @Override
    public InetAddress getIpAddressForDirectCommunication() {
        // TODO Auto-generated method stub
        throw new RuntimeException(); // TODO
    }

    @Override
    public void updateDeviceStateFromPostRtRequest(DeviceSidePostRtUpdateDTO fromDevice) {
        // TODO Auto-generated method stub
        throw new RuntimeException(); // TODO
    }

    @Override
    protected DeviceStatus extractDeviceStatusFromResponse(String apiResponse)
            throws ArgoLocalApiCommunicationException {
        if (apiResponse.isBlank()) {
            throw new ArgoLocalApiCommunicationException("Remote API response was empty. Check username and password");
        }

        var matcher = REMOTE_API_RESPONSE_EXPECTED.matcher(apiResponse);
        if (!matcher.matches()) {
            throw new ArgoLocalApiCommunicationException(
                    String.format("Remote API response [%s] was not recognized", apiResponse));
        }

        // this.onStateUpdate.
        // var properties = Map.of(DevicePropertyType.LocalIP, matcher.group("localIP"), DevicePropertyType.LastSeen,
        // matcher.group("lastSeen"));

        var properties = new DeviceProperties(matcher.group("localIP"), matcher.group("lastSeen"));

        // var localIp = matcher.group("localIP");
        // var lastSeen = matcher.group("lastSeen");
        // logger.info("Local IP is: {}. Last seen: {}", localIp, lastSeen); // TODO: do sth with it

        // logger.info("Local IP is: {}. Last seen: {}", properties.getLocalIP(),
        // properties.getLastSeenStr(this.timeZoneProvider)); // TODO: do
        // sth with
        // it
        // <-- TODO matcher.groups

        var delta = properties.getLastSeenDelta();
        // logger.warn("Last comms state: {} - {}", delta, delta.toMinutes());

        if (delta.toSeconds() > ArgoClimaConfigurationRemote.LAST_SEEN_UNAVAILABILITY_THRESHOLD.toSeconds()) {
            throw new ArgoLocalApiCommunicationException(MessageFormat.format(
                    "Device was last seen {0} mins ago (threshold is set at {1} min)", delta.toMinutes(),
                    ArgoClimaConfigurationRemote.LAST_SEEN_UNAVAILABILITY_THRESHOLD.toMinutes()));
        }

        return new DeviceStatus(matcher.group("commands"), properties);
    }
}
