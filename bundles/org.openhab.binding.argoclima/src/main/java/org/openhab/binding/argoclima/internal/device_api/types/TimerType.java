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
package org.openhab.binding.argoclima.internal.device_api.types;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.configuration.IScheduleConfigurationProvider.ScheduleTimerType;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public enum TimerType implements IArgoApiEnum {
    NO_TIMER(0),
    DELAY_TIMER(1),
    SCHEDULE_TIMER_1(2),
    SCHEDULE_TIMER_2(3),
    SCHEDULE_TIMER_3(4);

    private int value;

    TimerType(int intValue) {
        this.value = intValue;
    }

    @Override
    public int getIntValue() {
        return this.value;
    }

    public static ScheduleTimerType toScheduleTimerType(TimerType val) {
        switch (val) {
            case SCHEDULE_TIMER_1:
                return ScheduleTimerType.SCHEDULE_1;
            case SCHEDULE_TIMER_2:
                return ScheduleTimerType.SCHEDULE_2;
            case SCHEDULE_TIMER_3:
                return ScheduleTimerType.SCHEDULE_3;
            default:
                throw new IllegalArgumentException(
                        String.format("Unable to convert TimerType: %s to ScheduleTimerType", val));
        }
    }

    public static TimerType fromScheduleTimerType(ScheduleTimerType val) {
        switch (val) {
            case SCHEDULE_1:
                return TimerType.SCHEDULE_TIMER_1;
            case SCHEDULE_2:
                return TimerType.SCHEDULE_TIMER_2;
            case SCHEDULE_3:
                return TimerType.SCHEDULE_TIMER_3;
            default:
                throw new IllegalArgumentException(
                        String.format("Unable to convert ScheduleTimerType: %s to TimerType", val));
        }
    }
}
