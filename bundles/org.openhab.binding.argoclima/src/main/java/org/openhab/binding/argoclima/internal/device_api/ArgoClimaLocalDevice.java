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
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.URIUtil;
import org.openhab.binding.argoclima.internal.ArgoClimaBindingConstants;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationLocal;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSidePostRtUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSideUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.protocol.ArgoDeviceStatus;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaLocalDevice extends ArgoClimaDeviceApiBase {
    private final Logger logger = LoggerFactory.getLogger(ArgoClimaLocalDevice.class);
    public final InetAddress ipAddress;
    private final Optional<InetAddress> localIpAddress;
    private final Optional<String> cpuId;
    public int port;
    private ArgoDeviceStatus deviceStatus;
    private final boolean matchAnyIncomingDeviceIp;

    public ArgoClimaLocalDevice(ArgoClimaConfigurationLocal config, InetAddress targetDeviceIpAddress, int port,
            Optional<InetAddress> localDeviceIpAddress, Optional<String> cpuId, HttpClient client,
            TimeZoneProvider timeZoneProvider, Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate,
            Consumer<ThingStatus> onReachableStatusChange,
            Consumer<SortedMap<String, String>> onDevicePropertiesUpdate) {
        super(config, client, timeZoneProvider, onStateUpdate, onReachableStatusChange, onDevicePropertiesUpdate, "");
        this.ipAddress = targetDeviceIpAddress;
        this.port = port;
        this.deviceStatus = new ArgoDeviceStatus(config);
        this.localIpAddress = localDeviceIpAddress; // .orElse(targetDeviceIpAddress);
        this.cpuId = cpuId;
        this.matchAnyIncomingDeviceIp = config.getMatchAnyIncomingDeviceIp();
    }

    @Override
    public final Pair<Boolean, String> isReachable() {
        // TODO: last successful comms also may qualify?

        try {
            var status = extractDeviceStatusFromResponse(pollForCurrentStatusFromDeviceSync(getDeviceStateQueryUrl()));

            // TODO: checkme
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
        return uriToURL(URIUtil.newURI("http", this.ipAddress.getHostName(), this.port, "/", "HMI=&UPD=0"));
    }

    @Override
    protected URL getDeviceStateUpdateUrl() {
        return uriToURL(URIUtil.newURI("http", this.ipAddress.getHostName(), this.port, "/",
                String.format("HMI=%s&UPD=1", this.deviceStatus.getDeviceCommandStatus())));
    }

    /**
     * Update device state from intercepted message from device to remote server (device's own send of command)
     * This is sent in response to cloud-side command (likely a form of acknowledgement)
     *
     * @implNote This function is a WORK IN PROGRESS (and not doing anything useful at the present!)
     * @param fromDevice
     */
    public void updateDeviceStateFromPostRtRequest(DeviceSidePostRtUpdateDTO fromDevice) {
        if (this.cpuId.isEmpty()) {
            logger.debug(
                    "Got post update confirmation from device {}, but was not able to match it to this device b/c no CPUID is configured. Configure {} setting to allow this mode...",
                    fromDevice.cpuId, ArgoClimaBindingConstants.PARAMETER_DEVICE_CPU_ID);
            return;
        }
        if (!this.cpuId.get().equalsIgnoreCase(fromDevice.cpuId)) {
            logger.trace("Got post update from device [ID={}], but this entity belongs to device [ID={}]. Ignoring...",
                    fromDevice.cpuId, this.cpuId.orElse("???"));
            return;
        }

        // NOTICE (on possible future extension): The values from 'data' param of the response are NOT following the HMI
        // syntax in the GET requests (much more data is available in this requests)
        // There are some similarities -> ex. target/actual temperatures are at offset 112 & 113 of the array,
        // so at the very least, could get the known values (but not as trivial as:
        // # fromDevice.dataParam.split(",").Arrays.stream(paramArray).skip(111).limit(39).toList()
        // Overall, this needs more reverse-engineering (but works w/o this information, so not implementing for now)
    }

    /**
     * Update device state from intercepted message from device to remote server (device's own polling)
     * <p>
     * Important: The device-sent message will only be used for update if it matches to configured value
     * (this is to avoid updating status of a completely different device)
     * <p>
     * Most robust match is by CPUID, though if n/a, localIP is used as heuristic alternative as well
     *
     * @param hmiStringFromDevice The status update string device has sent
     * @param deviceIP The local IP of the device (as reported by the device itself)
     * @param deviceCpuId The CPUID of the device (as reported by the device itself)
     */
    public void updateDeviceStateFromPushRequest(DeviceSideUpdateDTO deviceUpdate) {
        String hmiStringFromDevice = deviceUpdate.currentValues;
        String deviceIP = deviceUpdate.deviceIp;
        String deviceCpuId = deviceUpdate.cpuId;

        if (this.cpuId.isPresent() && !this.cpuId.get().equalsIgnoreCase(deviceCpuId)) {
            logger.trace(
                    "Got poll update from device [ID={} | IP={}], but this entity belongs to device [ID={}]. Ignoring...",
                    deviceCpuId, deviceIP, this.cpuId.get());
            return; // direct mismatch
        }

        if (!this.localIpAddress.orElse(this.ipAddress).getHostAddress().equalsIgnoreCase(deviceIP)) {
            if (this.matchAnyIncomingDeviceIp) {
                logger.debug(
                        "Got poll update from device {}[IP={}], which is not a match to this device [{}={}]. Ignoring the mismatch due to matchAnyIncomingDeviceIp==true...",
                        deviceCpuId, deviceIP, this.localIpAddress.isPresent() ? "localIP" : "hostname",
                        this.localIpAddress.orElse(this.ipAddress).getHostAddress());
            } else {
                if (this.cpuId.isEmpty() && this.localIpAddress.isEmpty()) {
                    logger.warn(
                            "Got poll update from device {}[IP={}], but was not able to match it to this device with IP={}. Configure {} and/or {} settings to allow detection...",
                            deviceCpuId, deviceIP, this.ipAddress.getHostAddress(),
                            ArgoClimaBindingConstants.PARAMETER_DEVICE_CPU_ID,
                            ArgoClimaBindingConstants.PARAMETER_LOCAL_DEVICE_IP);
                } else {
                    logger.trace(
                            "Got poll update from device [ID={} | IP={}], but this entity belongs to device [ID={} | IP={}]. Ignoring...",
                            deviceCpuId, deviceIP, this.cpuId.orElse("???"),
                            this.localIpAddress.orElse(this.ipAddress).getHostAddress());
                }
                return; // IP address heuristic mismatch
            }
        }

        // TODO: update cpuID || IP

        this.deviceStatus.fromDeviceString(hmiStringFromDevice);
        this.onReachableStatusChange.accept(ThingStatus.ONLINE);
        this.onStateUpdate.accept(this.deviceStatus.getCurrentStateMap());

        var properties = new DeviceStatus.DeviceProperties(OffsetDateTime.now(), deviceUpdate);

        // this.updateDevicePropertiesFromDeviceResponse(null, deviceStatus)
        //
        // var metaProperties = metadata.asPropertiesRaw(this.timeZoneProvider);
        // var responseProperties = Map.of(ArgoClimaBindingConstants.PROPERTY_UNIT_FW,
        // this.deviceStatus.getSetting(ArgoDeviceSettingType.UNIT_FIRMWARE_VERSION).toString(false));

        synchronized (this) {
            // this.deviceProperties.clear();
            this.deviceProperties.putAll(properties.asPropertiesRaw(this.timeZoneProvider));
        }
        this.onDevicePropertiesUpdate.accept(getCurrentDeviceProperties());

        // var status = new DeviceStatus(hmiStringFromDevice, OffsetDateTime.now()); // TODO

        // TODO: checkme
        // this.deviceStatus.fromDeviceString(status.getCommandString());
        // this.updateDevicePropertiesFromDeviceResponse(status.getProperties(), this.deviceStatus);

        // this.onDevicePropertiesUpdate.accept(getCurrentDeviceProperties());

        // if(this)

        // if (this.cpuId.isPresent() && this.cpuId.get().equalsIgnoreCase(deviceCpuId)) {
        // // direct match
        // this.deviceStatus.fromDeviceString(hmiStringFromDevice);
        // this.onReachableStatusChange.accept(ThingStatus.ONLINE);
        // this.onStateUpdate.accept(this.deviceStatus.getCurrentStateMap());
        //
        // //Update IP etc...
        // } else if (this.cpuId.isEmpty()
        // && this.localIpAddress.orElse(this.ipAddress).getHostAddress().equalsIgnoreCase(deviceIP)) {
        // // heuristic (ip-based) match
        // this.deviceStatus.fromDeviceString(hmiStringFromDevice);
        // this.onReachableStatusChange.accept(ThingStatus.ONLINE);
        // this.onStateUpdate.accept(this.deviceStatus.getCurrentStateMap());
        // } else {
        // // no match
        // if (this.cpuId.isEmpty() && this.localIpAddress.isEmpty()) {
        // logger.warn(
        // "Got poll update from device {}[IP={}], but was not able to match it to this device with IP={}. Configure {}
        // or {} settings to allow detection...",
        // deviceCpuId, deviceIP, this.ipAddress.getHostAddress(),
        // ArgoClimaBindingConstants.PARAMETER_DEVICE_CPU_ID,
        // ArgoClimaBindingConstants.PARAMETER_LOCAL_DEVICE_IP);
        // } else {
        // logger.warn(
        // "Got poll update from device [ID={} | IP={}], but this entity belongs to device [ID={} | IP={}].
        // Ignoring...",
        // deviceCpuId, deviceIP, this.cpuId.orElse("???"),
        // this.localIpAddress.orElse(this.ipAddress).getHostAddress());
        // }
        //
        // }
    }

    @Override
    protected DeviceStatus extractDeviceStatusFromResponse(String apiResponse) {
        // local device response does not have all properties, but is always fresh
        return new DeviceStatus(apiResponse, OffsetDateTime.now()); // TODO
    }
}
