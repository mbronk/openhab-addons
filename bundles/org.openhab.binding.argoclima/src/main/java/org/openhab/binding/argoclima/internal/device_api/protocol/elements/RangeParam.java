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

import javax.measure.quantity.Dimensionless;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.protocol.IArgoSettingProvider;
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
public class RangeParam extends ArgoApiElementBase {
    private static final Logger logger = LoggerFactory.getLogger(RangeParam.class);
    private Optional<Number> currentValue = Optional.empty();

    private double minValue;
    private double maxValue;

    public RangeParam(IArgoSettingProvider settingsProvider, double min, double max) {
        super(settingsProvider);
        this.minValue = min;
        this.maxValue = max;
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        // TODO: if double then ?
        currentValue = Optional.of(toInt(responseValue));
        // if (this.minValue.compareTo(minValue) > 1) {
        //
        // }
        // TODO Auto-generated method stub
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get().toString();
    }

    private static State valueToState(Optional<Number> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }

        return new QuantityType<Dimensionless>(value.get(), Units.PERCENT);
    }

    @Override
    public State toState() {
        return valueToState(currentValue);
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        if (command instanceof QuantityType<?>) {
            int newValue = ((QuantityType<?>) command).intValue();
            if (this.currentValue.isEmpty() || this.currentValue.get().intValue() != newValue) {
                if (newValue < minValue) {
                    logger.warn("Requested value: {} would exceed minimum value: {}. Setting: {}.", newValue, minValue,
                            (int) minValue);
                    newValue = (int) minValue;
                }
                if (newValue > maxValue) {
                    logger.warn("Requested value: {} would exceed maximum value: {}. Setting: {}.", newValue, maxValue,
                            (int) maxValue);
                    newValue = (int) maxValue;
                }
                var targetValue = Optional.<Number>of(newValue);
                this.currentValue = targetValue;
                return HandleCommandResult.accepted(Integer.toString(targetValue.get().intValue()),
                        valueToState(targetValue));
            }
            // return Integer.toString(this.currentValue.get().intValue());
        }
        return HandleCommandResult.rejected();
    }
}
