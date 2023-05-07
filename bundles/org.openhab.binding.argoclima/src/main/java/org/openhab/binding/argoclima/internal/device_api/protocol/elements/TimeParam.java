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
import org.openhab.core.library.types.DecimalType;
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
public class TimeParam extends ArgoApiElementBase {
    public static enum TimeParamType {
        ON,
        OFF
    }

    private static final Logger logger = LoggerFactory.getLogger(TimeParam.class);

    private Optional<Integer> currentValue = Optional.of(25);// Optional.empty(); //TODO

    private int minValue;
    private int maxValue;
    private final TimeParamType paramType;

    public TimeParam(IArgoSettingProvider settingsProvider, TimeParamType paramType) {
        this(settingsProvider, paramType, fromHhMm(0, 0), fromHhMm(23, 59));
    }

    public TimeParam(IArgoSettingProvider settingsProvider, TimeParamType paramType, int minValue, int maxValue) {
        super(settingsProvider);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.paramType = paramType;
    }

    private static State valueToState(Optional<Integer> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }
        return new QuantityType<Time>(value.get(), Units.MINUTE);
        // todo this is sketchy
        // return new DateTimeType(ZonedDateTime.of(LocalDate.now(), value.get(), ZoneId.systemDefault()));
    }

    public static int fromHhMm(int hour, int minute) {
        // TODO assertions
        return hour * 60 + minute;
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        // TODO Auto-generated method stub
        int raw = toInt(responseValue);
        int hh = Math.floorDiv(raw, 60);
        int mm = raw - hh;

        // this.currentValue = Optional.of(LocalTime.of(hh, mm));
        this.currentValue = Optional.of(hh * 60 + mm);
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        int hh = currentValue.get().intValue() / 60;
        int mm = currentValue.get().intValue() % 60;
        return String.format("%02d:%02d", hh, mm);
    }

    @Override
    protected State getAsState() {
        return valueToState(currentValue);
    }

    @Override
    public boolean isAlwaysSent() {
        // logger.warn("isScheduleTimerEnabled={}", isScheduleTimerEnabled());
        return isScheduleTimerEnabled();
    }

    @Override
    public String getDeviceApiValue() {
        var defaultresult = super.getDeviceApiValue();
        if (defaultresult == NO_VALUE && isScheduleTimerEnabled()) {
            if (currentValue.isPresent()) {
                // TODO: only send when scheduleTimer is RAW/NON-CONFIRMED
                return Integer.toString(currentValue.get()); // TODO: only send it as long as TimerType is sent?
            } else {
                // TODO: IF no value set, get which schedule is enabled and get from settings direct
                // TODO: need to know who I am (on or off) :/
                if (paramType == TimeParamType.ON) {

                }
                // settingsProvider.getScheduleProvider().getSchedule1OnTime()
            }
        }
        return defaultresult;
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        // logger.error("Setting TIME param {}", command);
        // TODO:
        if (command instanceof DecimalType) {
            var rawCommand = (DecimalType) command;
            // if (command instanceof QuantityType<?>) {
            var newValue = rawCommand.intValue();
            if (newValue < minValue) {
                logger.warn("Requested value: {} would exceed minimum value: {}. Setting: {}.", newValue, minValue,
                        minValue);
                newValue = minValue;
            }
            if (newValue > maxValue) {
                logger.warn("Requested value: {} would exceed maximum value: {}. Setting: {}.", newValue, maxValue,
                        maxValue);
                newValue = maxValue;
            }

            // TODO: current value needs to be set on higher level
            this.currentValue = Optional.of(newValue);

            var result = new HandleCommandResult(Integer.toString(newValue), valueToState(Optional.of(newValue)));
            result.setDeferred(!isScheduleTimerEnabled());
            return result;
        }

        return new HandleCommandResult(false); // This value is NOT send to the device, unless a DelayTimer0 is
                                               // activaterd
    }
}
