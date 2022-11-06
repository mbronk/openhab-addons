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

import java.io.EOFException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.URIUtil;
import org.openhab.binding.argoclima.internal.ArgoClimaBindingConstants;
import org.openhab.binding.argoclima.internal.device_api.elements.IArgoElement;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSidePostRtUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaLocalDevice {
    private final Logger logger = LoggerFactory.getLogger(ArgoClimaLocalDevice.class);
    public final InetAddress ipAddress;
    private final Optional<InetAddress> localIpAddress;
    private final Optional<String> cpuId;
    public int port;
    private final HttpClient client;
    private ArgoDeviceStatus deviceStatus;
    private Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate;
    private Consumer<ThingStatus> onReachableStatusChange;

    public ArgoClimaLocalDevice(InetAddress targetDeviceIpAddress, int port, Optional<InetAddress> localDeviceIpAddress,
            Optional<String> cpuId, HttpClient client, Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate,
            Consumer<ThingStatus> onReachableStatusChange) {
        this.ipAddress = targetDeviceIpAddress;
        this.port = port;
        this.client = client;
        this.deviceStatus = new ArgoDeviceStatus();
        this.localIpAddress = localDeviceIpAddress; // .orElse(targetDeviceIpAddress);
        this.cpuId = cpuId;
        this.onStateUpdate = onStateUpdate;
        this.onReachableStatusChange = onReachableStatusChange;
    }

    // TODO: reverse logic of picking the addresses in other places (and update names?)
    public InetAddress getIpAddressForDirectCommunication() {
        return localIpAddress.orElse(ipAddress);
    }

    private String getDeviceStateQueryUrl() {
        return URIUtil.newURI("http", this.ipAddress.getHostName(), this.port, "/", "HMI=&UPD=0");
    }

    private String getDeviceStateUpdateUrl() {
        return URIUtil.newURI("http", this.ipAddress.getHostName(), this.port, "/",
                String.format("HMI=%s&UPD=1", this.deviceStatus.getDeviceCommandStatus()));
    }

    private String pollForCurrentStatusFromDeviceSync(String url) throws ArgoLocalApiCommunicationException {
        // TODO: consider separate http client and timeouts
        try {
            logger.info("Communication: OPENHAB --> DEVICE: [GET {}]", url);

            ContentResponse resp = this.client.GET(url);

            logger.info("   [response]: OPENHAB <-- DEVICE: [{} {} {} - {} bytes], body=[{}]", resp.getVersion(),
                    resp.getStatus(), resp.getReason(), resp.getContent().length, resp.getContentAsString());

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
            throw new ArgoLocalApiCommunicationException("Device communication error: " + ex.getCause().getMessage(), // TODO
                                                                                                                      // (ex.getCause()
                                                                                                                      // may
                                                                                                                      // return
                                                                                                                      // null)
                    ex.getCause());
        } catch (TimeoutException e) {
            throw new ArgoLocalApiCommunicationException("Timeout: " + e.getMessage(), e);
        }
    }

    public boolean isReachable() {
        // TODO: last successful comms also may qualify?

        try {
            pollForCurrentStatusFromDeviceSync(getDeviceStateQueryUrl());
            return true;
        } catch (ArgoLocalApiCommunicationException e) {
            logger.warn("Device not reachable: {}", e.getMessage());
            return false;
        }
    }

    public Map<ArgoDeviceSettingType, State> queryDeviceForUpdatedState() throws ArgoLocalApiCommunicationException {
        // this.client.setAddressResolutionTimeout(10 *1000);
        // TODO: if not reachable, calling it makes zero sense!

        var deviceResponse = pollForCurrentStatusFromDeviceSync(getDeviceStateQueryUrl());
        this.deviceStatus.fromDeviceString(deviceResponse);
        return this.deviceStatus.getCurrentStateMap();
    }

    public void sendCommandsToDevice() throws ArgoLocalApiCommunicationException {
        var deviceResponse = pollForCurrentStatusFromDeviceSync(getDeviceStateUpdateUrl());
        logger.info("State update command finished. Device response: {}", deviceResponse);
    }

    public boolean handleSettingCommand(ArgoDeviceSettingType settingType, Command command) {
        return this.deviceStatus.getSetting(settingType).handleCommand(command);
    }

    public State getCurrentStateNoPoll(ArgoDeviceSettingType settingType) {
        return this.deviceStatus.getSetting(settingType).getState();
    }

    public boolean hasPendingCommands() {
        var itemsWithPendingUpdates = this.deviceStatus.getItemsWithPendingUpdates();
        logger.info("Items to update: {}", itemsWithPendingUpdates);
        return !this.deviceStatus.getItemsWithPendingUpdates().isEmpty();
        // return this.deviceStatus.hasUpdatesPending();
    }

    public List<ArgoApiDataElement<IArgoElement>> getItemsWithPendingUpdates() {
        return this.deviceStatus.getItemsWithPendingUpdates();
    }

    public void updateDeviceStateFromPostRtRequest(DeviceSidePostRtUpdateDTO fromDevice) {
        if (this.cpuId.isEmpty()) {
            logger.warn(
                    "Got poll update from device {}, but was not able to match it to this device b/c no CPUID is configured. Configure {} setting to allow this mode...",
                    fromDevice.cpuId, ArgoClimaBindingConstants.PARAMETER_DEVICE_CPU_ID);
            return;
        }
        if (!this.cpuId.get().equalsIgnoreCase(fromDevice.cpuId)) {
            logger.warn("Got post update from device [ID={}], but this entity belongs to device [ID={}]. Ignoring...",
                    fromDevice.cpuId, this.cpuId.orElse("???"));
            return;
        }
        var paramArray = fromDevice.dataParam.split(",");
        logger.info("Params are... {}", paramArray.toString());
    }

    public void updateDeviceStateFromPushRequest(String hmiStringFromDevice, String deviceIP, String deviceCpuId) {
        if (this.cpuId.isEmpty() && this.localIpAddress.isEmpty()) {
            logger.warn(
                    "Got poll update from device {}[IP={}], but was not able to match it to this device with IP={}. Configure {} and/or {} settings to allow detection...",
                    deviceCpuId, deviceIP, this.ipAddress.getHostAddress(),
                    ArgoClimaBindingConstants.PARAMETER_DEVICE_CPU_ID,
                    ArgoClimaBindingConstants.PARAMETER_LOCAL_DEVICE_IP);
            return; // unable to match to device
        }

        if (this.cpuId.isPresent() && !this.cpuId.get().equalsIgnoreCase(deviceCpuId)) {
            logger.warn(
                    "Got poll update from device [ID={} | IP={}], but this entity belongs to device [ID={}]. Ignoring...",
                    deviceCpuId, deviceIP, this.cpuId.get());
            return; // direct mismatch
        }

        if (!this.localIpAddress.orElse(this.ipAddress).getHostAddress().equalsIgnoreCase(deviceIP)) {
            logger.warn(
                    "Got poll update from device [ID={} | IP={}], but this entity belongs to device [ID={}]. Ignoring...",
                    deviceCpuId, deviceIP, this.cpuId.orElse("???"),
                    this.localIpAddress.orElse(this.ipAddress).getHostAddress());
            return; // heuristic mismatch
        }

        // TODO: update cpuID || IP
        this.deviceStatus.fromDeviceString(hmiStringFromDevice);
        this.onReachableStatusChange.accept(ThingStatus.ONLINE);
        this.onStateUpdate.accept(this.deviceStatus.getCurrentStateMap());

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
}
