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
package org.openhab.binding.argoclima.internal.device_api.protocol.elements;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

/**
 * Interface for Argo API parameter (individual HMI element)
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public interface IArgoElement {
    /**
     * Return **current** state of the element (including side-effects of any pending commands)
     *
     * @return Device's state as {@link State}
     */
    public State toState();

    /**
     * Updates this API element's state from device's response
     *
     * @param responseValue Raw API input
     * @return State after update
     */
    public State updateFromApiResponse(String responseValue);

    /**
     * Handles channel command
     *
     * @param command The command to handle
     * @param isConfirmable Whether the command result is confirmable by the device
     * @return True - if command has been handled (= accepted by the framework and ready to be sent to device), False -
     *         otherwise
     */
    public boolean handleCommand(Command command, boolean isConfirmable);

    /**
     * Returns the raw Argo command to be sent to the device (if update is pending)
     *
     * @return Command to send to device (if update pending), or {@link ArgoApiElementBase#NO_VALUE NO_VALUE} -
     *         otherwise
     */
    public String getDeviceApiValue();

    /**
     * Return string representation of the current state of the device in a human-friendly format (for logging)
     * Similar to {@link IArgoElement#getLastStateFromDevice()}, but returns mostly a protocol-like value, not
     * necessarily the framework-converted one
     *
     * @return String representation of the element
     */
    @Override
    public String toString();

    /**
     * Notifies that the pending command has been sent
     *
     * @note Used for write-only params, to indicate they have been (hopefully) correctly sent to the device
     */
    public void notifyCommandSent();

    /**
     * Aborts any pending command
     */
    public void abortPendingCommand();

    /**
     * Checks if there's any command to be sent to the device (pending = not yet sent or not confirmed by the device
     * yet)
     *
     * @return True if command pending, False otherwise
     */
    public boolean isUpdatePending();

    /**
     * Returns true if the value is always sent to the device on next communication cycle (opportunistically), even if
     * there was no
     * command (ex. current time)
     *
     * @return True if the value is always sent
     */
    public boolean isAlwaysSent();
}
