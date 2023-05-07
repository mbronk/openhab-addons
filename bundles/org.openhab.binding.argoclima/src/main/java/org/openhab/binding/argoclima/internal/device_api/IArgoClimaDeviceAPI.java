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
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.passthrough.requests.DeviceSidePostRtUpdateDTO;
import org.openhab.binding.argoclima.internal.device_api.protocol.ArgoApiDataElement;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.IArgoElement;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public interface IArgoClimaDeviceAPI {

    // TODO: reverse logic of picking the addresses in other places (and update names?)
    InetAddress getIpAddressForDirectCommunication();

    Pair<Boolean, String> isReachable();

    Map<ArgoDeviceSettingType, State> queryDeviceForUpdatedState() throws ArgoLocalApiCommunicationException;

    /**
     * Returns last-retrieved device state (does *not* re-query the device)
     *
     * @return
     */
    Map<ArgoDeviceSettingType, State> getLastStateReadFromDevice();

    /**
     * Returns currently known properties of the device (from last-read state)
     *
     * @apiNote Does *not* query the device on its own
     *
     * @return
     */
    Map<String, String> getCurrentDeviceProperties();

    void sendCommandsToDevice() throws ArgoLocalApiCommunicationException;

    boolean handleSettingCommand(ArgoDeviceSettingType settingType, Command command);

    State getCurrentStateNoPoll(ArgoDeviceSettingType settingType);

    boolean hasPendingCommands();

    List<ArgoApiDataElement<IArgoElement>> getItemsWithPendingUpdates();

    void updateDeviceStateFromPostRtRequest(DeviceSidePostRtUpdateDTO fromDevice);

    void updateDeviceStateFromPushRequest(String hmiStringFromDevice, String deviceIP, String deviceCpuId);
}
