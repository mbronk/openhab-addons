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
package org.openhab.binding.argoclima.internal.device_api.protocol.elements;

import java.util.Optional;

import javax.measure.quantity.Time;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.protocol.IArgoSettingProvider;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.device_api.types.TimerType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class DelayMinutesParam extends ArgoApiElementBase {

    private static final Logger logger = LoggerFactory.getLogger(DelayMinutesParam.class);
    private Optional<Integer> currentValue;
    private final int minValue;
    private final int maxValue;
    private final int step;

    public DelayMinutesParam(IArgoSettingProvider settingsProvider, int min, int max, int step) {
        this(settingsProvider, min, max, step, Optional.empty());
    }

    public DelayMinutesParam(IArgoSettingProvider settingsProvider, int min, int max, int step,
            Optional<Integer> initialValue) {
        super(settingsProvider);
        this.minValue = min;
        this.maxValue = max;
        this.step = step;
        this.currentValue = initialValue;
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        currentValue = Optional.of(adjustRange(toInt(responseValue)));
        // TODO: check range?
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get().toString() + " min";
    }

    @Override
    public String getDeviceApiValue() {
        // TODO Auto-generated method stub
        // TODO: only send when scheduleTimer is RAW/NON-CONFIRMED
        return super.getDeviceApiValue();
    }

    @Override
    public boolean isAlwaysSent() {
        // TODO Auto-generated method stub -> see TimeParam
        return super.isAlwaysSent();
    }

    private static State valueToState(Optional<Integer> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }

        return new QuantityType<Time>(value.get(), Units.MINUTE);
    }

    @Override
    protected State getAsState() {
        return valueToState(currentValue);
    }

    private int adjustRange(int newValue) {
        if (newValue < minValue) {
            logger.warn("Requested value: {} would exceed minimum value: {}. Setting: {}.", newValue, minValue,
                    minValue);
            return minValue;
        }
        if (newValue > maxValue) {
            logger.warn("Requested value: {} would exceed maximum value: {}. Setting: {}.", newValue, maxValue,
                    maxValue);
            return maxValue;
        }

        // TODO: round to nearest step
        return newValue;
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        // TODO: SEND IMMEDIATELY?!
        if (command instanceof QuantityType<?>) {
            int newValue = ((QuantityType<?>) command).intValue();
            if (this.currentValue.isEmpty() || this.currentValue.get().intValue() != newValue) { // TODO: if the same,
                                                                                                 // does not send?!
                var targetValue = Optional.<Integer> of(adjustRange(newValue));
                this.currentValue = targetValue;

                // DO *not* send this value back to device, will only happen on schedule param
                // TODO: if DelayTimer is active -> do it
                // return new HandleCommandResult(false);

                var result = new HandleCommandResult(Integer.toString(targetValue.get().intValue()),
                        valueToState(targetValue));

                var currentTimer = EnumParam.fromType(
                        settingsProvider.getSetting(ArgoDeviceSettingType.ACTIVE_TIMER).getState(), TimerType.class);

                result.setDeferred(currentTimer.isPresent() && currentTimer.get() != TimerType.DELAY_TIMER); // TODO: if
                                                                                                             // current
                                                                                                             // timer is
                                                                                                             // != delay
                                                                                                             // -> make
                // it deferred
                return result;
            }
            // return Integer.toString(this.currentValue.get().intValue());
        }
        return new HandleCommandResult(false);
    }
}
