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

import java.util.EnumSet;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase.Weekday;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class WeekdayParam extends ArgoApiElementBase {

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

    private static int toRawValue(EnumSet<Weekday> values) {
        int ret = 0;
        for (Weekday val : values) {
            ret |= val.getIntValue(); // (1 << val.getIntValue());
        }
        return ret;
    }

    private static EnumSet<Weekday> fromRawValue(int value) {
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
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        // TODO handle
        // return toRawValue()
        return new HandleCommandResult(false);
    }
}
