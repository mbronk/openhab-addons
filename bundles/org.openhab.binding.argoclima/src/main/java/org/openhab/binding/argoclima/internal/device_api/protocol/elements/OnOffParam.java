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

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.protocol.IArgoSettingProvider;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class OnOffParam extends ArgoApiElementBase {

    private Optional<Boolean> currentValue = Optional.empty();

    public OnOffParam(IArgoSettingProvider settingsProvider) {
        super(settingsProvider);
    }

    private static final String VALUE_ON = "1";
    private static final String VALUE_OFF = "0";

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        if (OnOffParam.VALUE_ON.equals(responseValue)) {
            this.currentValue = Optional.of(true);
        } else if (OnOffParam.VALUE_OFF.equals(responseValue)) {
            this.currentValue = Optional.of(false);
        } else if (ArgoApiElementBase.NO_VALUE.equals(responseValue)) {
            this.currentValue = Optional.empty();
        } else {
            throw new RuntimeException(String.format("Invalid value of parameter: {}", responseValue)); // TODO: check
                                                                                                        // format string
        }
        // TODO Auto-generated method stub
    }

    private static State valueToState(Optional<Boolean> value) {
        return value.<State> map(v -> OnOffType.from(v)).orElse(UnDefType.UNDEF);
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get() ? "ON" : "OFF";
        // return currentValue.toString();
    }

    @Override
    protected State getAsState() {
        return valueToState(currentValue);
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        if (command instanceof OnOffType) {
            if (((OnOffType) command).equals(OnOffType.ON)) {
                var targetValue = Optional.of(true);
                currentValue = targetValue;
                return new HandleCommandResult(VALUE_ON, valueToState(targetValue));
            } else if (((OnOffType) command).equals(OnOffType.OFF)) {
                var targetValue = Optional.of(false);
                currentValue = targetValue;
                return new HandleCommandResult(VALUE_OFF, valueToState(targetValue));
            }
        }
        return new HandleCommandResult(false);
    }
}
