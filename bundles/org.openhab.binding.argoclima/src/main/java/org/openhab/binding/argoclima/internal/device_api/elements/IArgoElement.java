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
package org.openhab.binding.argoclima.internal.device_api.elements;

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
     * Gets last live state returned from the device (ignores any pending commands)
     *
     * @return Device's state as {@link State}
     */
    public State getLastStateFromDevice();

    /**
     * Checks if there's any command to be sent to the device (not confirmed by the device yet)
     *
     * @return True if command pending, False otherwise
     */
    public boolean isUpdatePending();

    /**
     * Returns the raw Argo command to send to the device (if update is pending)
     *
     * @return Command to send to device (if update pending), or {@code NO_VALUE} otherwise
     */
    public String getDeviceApiValue();

    /**
     * Updates this API element's state from device's response
     *
     * @param responseValue Raw API input
     * @return State after update
     */
    public State updateFromApiResponse(String responseValue);

    /**
     * Return current state of the element (including side-effects of any pending commands)
     *
     * @return Device's state as {@link State}
     */
    public State toState();

    /**
     * Handles channel command
     *
     * @param command The command to handle
     * @return True - if command has been handled, False - otherwise
     */
    public boolean handleCommand(Command command);

    /**
     * Aborts any pending command
     */
    public void abortPendingCommand();

    @Override
    public String toString();
}
