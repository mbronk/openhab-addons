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
package org.openhab.binding.argoclima.internal.device_api;

import java.net.InetAddress;
import java.net.URL;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.URIUtil;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSidePostRtUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mbronk - Initial contribution
 *
 */
@NonNullByDefault
public class ArgoClimaRemoteDevice extends ArgoClimaDeviceApiBase {
    private final Logger logger = LoggerFactory.getLogger(ArgoClimaRemoteDevice.class);

    public final InetAddress oemServerHostname;
    public final int oemServerPort;
    public final String username;
    public final String passwordMD5Hash;
    final static Pattern REMOTE_API_RESPONSE_EXPECTED = Pattern.compile(
            "^[\\\\{][|](?<commands>[^|]+)[|](?<localIP>[^|]+)[|](?<lastSeen>[^|]+)[|][\\\\}]\\s*$",
            Pattern.CASE_INSENSITIVE);

    public ArgoClimaRemoteDevice(HttpClient client, InetAddress oemServerHostname, int oemServerPort, String username,
            String passwordMD5, Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate,
            Consumer<ThingStatus> onReachableStatusChange) {
        super(client, onStateUpdate, onReachableStatusChange);
        this.oemServerHostname = oemServerHostname;
        this.oemServerPort = oemServerPort;
        this.username = username;
        this.passwordMD5Hash = passwordMD5;
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
    public void updateDeviceStateFromPushRequest(@NonNull String hmiStringFromDevice, @NonNull String deviceIP,
            @NonNull String deviceCpuId) {
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
    protected String extractDeviceStatusFromResponse(String apiResponse) throws ArgoLocalApiCommunicationException {
        if (apiResponse.isBlank()) {
            throw new ArgoLocalApiCommunicationException("Remote API response was empty. Check username and password");
        }

        var matcher = REMOTE_API_RESPONSE_EXPECTED.matcher(apiResponse);
        if (!matcher.matches()) {
            throw new ArgoLocalApiCommunicationException(
                    String.format("Remote API response [%s] was not recognized", apiResponse));
        }

        // this.onStateUpdate.
        var localIp = matcher.group("localIP");
        var lastSeen = matcher.group("lastSeen");
        logger.info("Local IP is: {}. Last seen: {}", localIp, lastSeen); // TODO: do sth with it
        // <-- TODO matcher.groups
        return matcher.group("commands");
    }
}
