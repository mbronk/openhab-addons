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

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase.Weekday;
import org.openhab.binding.argoclima.internal.device_api.protocol.IArgoSettingProvider;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.device_api.types.TimerType;
import org.openhab.binding.argoclima.internal.exception.ArgoConfigurationException;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mateusz Bronk - Initial contribution
 *
 */
@NonNullByDefault
public class ActiveTimerModeParam extends EnumParam<TimerType> {
    private static final Logger logger = LoggerFactory.getLogger(ActiveTimerModeParam.class);

    public ActiveTimerModeParam(IArgoSettingProvider settingsProvider) {
        super(settingsProvider, TimerType.class);
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        if (command instanceof StringType) {
            TimerType newTimerType = fromType(command, TimerType.class).get();
            Optional<EnumSet<Weekday>> activeDays = Optional.empty();
            Optional<LocalTime> scheduleOnTime = Optional.empty();
            Optional<LocalTime> scheduleOffTime = Optional.empty();
            boolean isScheduleTimer = false;
            try {
                switch (newTimerType) {
                    case SCHEDULE_TIMER_1:
                        activeDays = Optional.of(settingsProvider.getScheduleProvider().getSchedule1DayOfWeek());
                        scheduleOnTime = Optional.of(settingsProvider.getScheduleProvider().getSchedule1OnTime());
                        scheduleOffTime = Optional.of(settingsProvider.getScheduleProvider().getSchedule1OffTime());
                        isScheduleTimer = true;
                        break;
                    case SCHEDULE_TIMER_2:
                        activeDays = Optional.of(settingsProvider.getScheduleProvider().getSchedule2DayOfWeek());
                        scheduleOnTime = Optional.of(settingsProvider.getScheduleProvider().getSchedule2OnTime());
                        scheduleOffTime = Optional.of(settingsProvider.getScheduleProvider().getSchedule2OffTime());
                        isScheduleTimer = true;
                        break;
                    case SCHEDULE_TIMER_3:
                        // get values from settings
                        activeDays = Optional.of(settingsProvider.getScheduleProvider().getSchedule3DayOfWeek());
                        scheduleOnTime = Optional.of(settingsProvider.getScheduleProvider().getSchedule3OnTime());
                        scheduleOffTime = Optional.of(settingsProvider.getScheduleProvider().getSchedule3OffTime());
                        isScheduleTimer = true;
                        break;
                    default:
                        isScheduleTimer = false;
                        break;
                }
            } catch (ArgoConfigurationException e) {
                // TODO Auto-generated catch block
                logger.warn("{}", e.getMessage());
            }

            if (isScheduleTimer) {
                logger.info("New timer value is: {}. Days={}, On={}, Off={}", newTimerType, activeDays, scheduleOnTime,
                        scheduleOffTime);

                var timerDays = settingsProvider.getSetting(ArgoDeviceSettingType.TIMER_N_ENABLED_DAYS);
                var timerOn = settingsProvider.getSetting(ArgoDeviceSettingType.TIMER_N_ON_TIME);
                var timerOff = settingsProvider.getSetting(ArgoDeviceSettingType.TIMER_N_OFF_TIME);

                timerOn.handleCommand(new DecimalType(
                        TimeParam.fromHhMm(scheduleOnTime.get().getHour(), scheduleOnTime.get().getMinute())));
                timerOff.handleCommand(new DecimalType(
                        TimeParam.fromHhMm(scheduleOffTime.get().getHour(), scheduleOffTime.get().getMinute())));

                timerDays.handleCommand(new DecimalType(WeekdayParam.toRawValue(activeDays.get())));
            }

        }
        return super.handleCommandInternalEx(command);
    }
}
