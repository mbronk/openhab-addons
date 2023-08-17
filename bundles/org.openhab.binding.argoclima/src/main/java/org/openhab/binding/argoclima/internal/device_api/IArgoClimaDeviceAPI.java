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

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.protocol.ArgoApiDataElement;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.IArgoElement;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

/**
 * Interface for communication with Argo device(regardless of method)
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public interface IArgoClimaDeviceAPI {

    /**
     * Check if Argo device is reachable (this check MAY trigger device communications!)
     * <p>
     * For local connection the checking is live (and synchronous!).
     * For remote connection, the status is updated based off of last device's communication
     *
     * @return A Pair of {@code <REACHABLE, ERROR (if unreachable)>}
     */
    Pair<Boolean, String> isReachable();

    /**
     * Query the Argo device for updated state.
     * <p>
     * This ALWAYS triggers new device communication
     *
     * @return A map of {@code Setting->Value} read from device
     * @throws ArgoLocalApiCommunicationException thrown when unable to communicate with the Argo device
     */
    Map<ArgoDeviceSettingType, State> queryDeviceForUpdatedState() throws ArgoLocalApiCommunicationException;

    /**
     * Returns last-retrieved device state
     * <p>
     * This does *NOT* re-query the device
     *
     * @return A map of {@code Setting->Value} read from cache
     */
    Map<ArgoDeviceSettingType, State> getLastStateReadFromDevice();

    /**
     * Returns currently known properties of the device (from last-read state)
     *
     * @apiNote Does *not* query the device on its own
     *
     * @return A key-value map of device properties (both static/from configuration as well as the dynamic - read from
     *         device)
     */
    SortedMap<String, String> getCurrentDeviceProperties();

    /**
     * Send any pending commands to the device (upon synchronizing with freshest device-side state)
     *
     * @throws ArgoLocalApiCommunicationException thrown when unable to communicate with the Argo device
     */
    void sendCommandsToDevice() throws ArgoLocalApiCommunicationException;

    /**
     * Handle any setting command from UI
     *
     * @param settingType The name of setting receiving the value
     * @param command The command/new value
     * @return True - if command has been handled, False - otherwise
     */
    boolean handleSettingCommand(ArgoDeviceSettingType settingType, Command command);

    /**
     * Get the current value of a setting
     *
     * @param settingType The name of setting queried
     * @return Current value of the setting
     */
    State getCurrentStateNoPoll(ArgoDeviceSettingType settingType);

    /**
     * Check if there are any commands pending send to the device
     *
     * @return True if there are commands pending, False otherwise
     */
    boolean hasPendingCommands();

    /**
     * Get items which have pending updates
     *
     * @return List of settings that have updates pending
     */
    List<ArgoApiDataElement<IArgoElement>> getItemsWithPendingUpdates();
}