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
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class TemperatureParam extends ArgoApiElementBase {
    private static final Logger logger = LoggerFactory.getLogger(TemperatureParam.class);

    private double minValue;
    private double maxValue;
    private Optional<Double> currentValue = Optional.empty();
    private double step;

    public TemperatureParam(IArgoSettingProvider settingsProvider, double min, double max, double step) {
        super(settingsProvider);
        this.minValue = min;
        this.maxValue = max;
        this.step = step;
    }

    public TemperatureParam(IArgoSettingProvider settingsProvider) {
        super(settingsProvider);
        this.minValue = Double.NEGATIVE_INFINITY;
        this.maxValue = Double.POSITIVE_INFINITY;
        this.step = 0.01;
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
    protected State getAsState() {
        return valueToState(currentValue);
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        if (command instanceof QuantityType<?>) {
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
                return new HandleCommandResult(Integer.toUnsignedString((int) (targetValue.get() * 10.0)),
                        valueToState(targetValue));
            }
            // return Integer.toUnsignedString((int) (this.currentValue.get() * 10.0));
        }
        return new HandleCommandResult(false);
    }
}
