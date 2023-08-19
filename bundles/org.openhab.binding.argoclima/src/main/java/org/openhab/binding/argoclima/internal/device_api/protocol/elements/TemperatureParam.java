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

import java.util.Objects;
import java.util.Optional;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.protocol.IArgoSettingProvider;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The element for controlling/receiving temperature
 * <p>
 * Device API always communicates in degrees Celsius, even if the display unit (configurable) is Fahrenheit.
 * <p>
 * While the settable temperature seems to be by 0.5 °C (at least this is what the remote API does), the reported temp.
 * is by 0.1 °C and technically the device accepts setting values with such precision. This is not practiced though, not
 * to introduce unknown side-effects
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class TemperatureParam extends ArgoApiElementBase {
    private static final Logger logger = LoggerFactory.getLogger(TemperatureParam.class);

    private final double minValue;
    private final double maxValue;
    private final double step;
    private Optional<Double> currentValue = Optional.empty();

    /**
     * C-tor
     *
     * @param settingsProvider the settings provider (getting device state as well as schedule configuration)
     * @param min Minimum value of this timer (in minutes)
     * @param max Maximum value of this timer (in minutes)
     * @param step Minimum step of the timer (values will be rounded to nearest step, increments/decrements will move by
     *            step). Step dictates the resolution of this param
     */
    public TemperatureParam(IArgoSettingProvider settingsProvider, double min, double max, double step) {
        super(settingsProvider);
        this.minValue = min;
        this.maxValue = max;
        this.step = step;
    }

    private static State valueToState(Optional<Double> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }
        return new QuantityType<Temperature>(value.get(), SIUnits.CELSIUS);
    }

    // @Override
    // public String toApiSetting() {
    // // TODO Auto-generated method stub
    // return null;
    // }
    // TODO
    // /**
    // * @see {@link ArgoApiElementBase#adjustRange}
    // */
    // private int adjustRange(int newValue) {
    // return ArgoApiElementBase.adjustRange(newValue, minValue, maxValue, Optional.of(step), " min").intValue();
    // }
    //
    // /**
    // * @see {@link ArgoApiElementBase#adjustRangeWithAmplification}
    // */
    // private int adjustRangeWithAmplification(int newValue) {
    // return ArgoApiElementBase.adjustRangeWithAmplification(newValue, currentValue, minValue, maxValue, step, " min")
    // .intValue();
    // }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        int rawValue = 0;
        try {
            rawValue = Integer.parseInt(responseValue);
        } catch (NumberFormatException e) {
            throw new RuntimeException(String.format("The value %s is not a valid integer", responseValue), e);
        }
        // TODO: check range
        this.currentValue = Optional.of(rawValue / 10.0);
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get().toString() + " °C";
    }

    @Override
    public State toState() {
        return valueToState(currentValue);
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        if (command instanceof QuantityType<?>) { // TODO
            var rawCommand = (QuantityType<?>) command;
            var valueCelscius = rawCommand.toUnit(SIUnits.CELSIUS);
            double newValue = Objects.requireNonNull(valueCelscius).doubleValue();

            if (this.currentValue.isEmpty() || this.currentValue.get().doubleValue() != newValue) {
                if (newValue < minValue) {
                    logger.warn("Requested value: {} °C would exceed minimum value: {} °C. Setting: {} °C.", newValue,
                            minValue, minValue);
                    newValue = minValue;
                }
                if (newValue > maxValue) {
                    logger.warn("Requested value: {} °C would exceed maximum value: {} °C. Setting: {} °C.", newValue,
                            maxValue, maxValue);
                    newValue = maxValue;
                }

                var targetValue = Optional.<Double>of(newValue);
                this.currentValue = targetValue;
                return HandleCommandResult.accepted(Integer.toUnsignedString((int) (targetValue.get() * 10.0)),
                        valueToState(targetValue));
            }
            // return Integer.toUnsignedString((int) (this.currentValue.get() * 10.0));
        }
        return HandleCommandResult.rejected();
    }
}
