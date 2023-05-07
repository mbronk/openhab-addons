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

import java.util.EnumSet;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase.Weekday;
import org.openhab.binding.argoclima.internal.device_api.protocol.IArgoSettingProvider;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
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
public class WeekdayParam extends ArgoApiElementBase {
    private static final Logger logger = LoggerFactory.getLogger(WeekdayParam.class);

    public WeekdayParam(IArgoSettingProvider settingsProvider) {
        super(settingsProvider);
    }
    // public enum Weekday implements IArgoApiEnum {
    // SUNDAY(0x00),
    // MONDAY(0x01),
    // TUESDAY(0x02),
    // WEDNESDAY(0x04),
    // THURSDAY(0x08),
    // FRIDAY(0x10),
    // SATURDAY(0x20);
    //
    // private int value;
    //
    // Weekday(int intValue) {
    // this.value = intValue;
    // }
    //
    // @Override
    // public int getIntValue() {
    // return this.value;
    // }
    // }

    private Optional<EnumSet<Weekday>> currentValue = Optional.empty();

    private static State valueToState(Optional<EnumSet<Weekday>> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }
        return new StringType(value.get().toString()); // TODO:
    }

    public static int toRawValue(EnumSet<Weekday> values) {
        int ret = 0;
        for (Weekday val : values) {
            ret |= val.getIntValue(); // (1 << val.getIntValue());
        }
        return ret;
    }

    public static EnumSet<Weekday> fromRawValue(int value) {
        EnumSet<Weekday> ret = EnumSet.noneOf(Weekday.class);
        for (Weekday val : EnumSet.allOf(Weekday.class)) {
            if ((val.getIntValue() & value) != 0) {
                ret.add(val);
            }
        }
        return ret;
    }

    // = Optional.empty();
    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        int rawValue = toInt(responseValue);

        // TODO check value w/ bitmask
        this.currentValue = Optional.of(fromRawValue(rawValue));
        // TODO Auto-generated method stub
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get().toString();
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
            // TODO: only send when scheduleTimer is RAW/NON-CONFIRMED
            if (currentValue.isPresent()) {
                return Integer.toString(toRawValue(currentValue.get())); // TODO: only send it as long as TimerType is
                                                                         // sent?
            } else {
                // TODO: IF no value set, get which schedule is enabled and get from settings direct
                // TODO: need to know who I am (on or off) :/
                // settingsProvider.getScheduleProvider().getSchedule1OnTime()
            }

        }
        return defaultresult;
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        // TODO handle
        // return toRawValue()
        logger.warn("TODO");

        if (command instanceof DecimalType) {
            var rawCommand = (DecimalType) command;
            var newValue = fromRawValue(rawCommand.intValue());

            this.currentValue = Optional.of(newValue);

            var result = new HandleCommandResult(Integer.toString(rawCommand.intValue()),
                    valueToState(Optional.of(newValue)));
            result.setDeferred(!isScheduleTimerEnabled());
            return result;
        }

        return new HandleCommandResult(false); // This value is NOT send to the device, unless a DelayTimer0 is
                                               // activaterd
    }
}
