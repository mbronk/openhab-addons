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
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public interface IArgoElement {

    public State getLastStateFromDevice();

    // public State getCurentState();

    public boolean isUpdatePending();

    public String getDeviceApiValue();

    public State updateFromApiResponse(String responseValue);

    public String toApiSetting();

    public State toState();

    // returns value state
    public boolean handleCommand(Command command);

    @Override
    public String toString();

    public void abortPendingCommand();
}
