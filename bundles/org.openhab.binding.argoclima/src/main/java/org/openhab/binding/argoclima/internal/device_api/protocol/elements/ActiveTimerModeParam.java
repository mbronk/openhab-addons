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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
 * Timer mode parameter (handling schedule timers as well as delay timer) - special class of enum, as the timers are not
 * fully standalone elements
 *
 * @author Mateusz Bronk - Initial contribution
 *
 */
@NonNullByDefault
public class ActiveTimerModeParam extends EnumParam<TimerType> {
    private static final Logger logger = LoggerFactory.getLogger(ActiveTimerModeParam.class);

    /**
     * C-tor
     *
     * @param settingsProvider the settings provider (getting device state as well as schedule configuration)
     */
    public ActiveTimerModeParam(IArgoSettingProvider settingsProvider) {
        super(settingsProvider, TimerType.class);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Does pre-work for schedule timers and - if one of them is selected - injects (sends commands) to appropriate
     * elements.
     * Coordinates multiple timer parameters (ex. for TIMER1, need to fetch schedule1 params for day of week, on time
     * and off time), and finally lets the super class handle THIS setting
     *
     */
    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        if (!(command instanceof StringType)) {
            return HandleCommandResult.rejected(); // Unsupported command type, nothing to do anyway
        }

        var requestedValue = fromType(command, TimerType.class);
        if (requestedValue.isEmpty()) {
            return HandleCommandResult.rejected(); // Value not valid for a timer enum, rejecting command as a whole
        }
        TimerType newTimerType = requestedValue.orElseThrow(); // Boilerplate, guaranteed no-throw at this point

        if (!EnumSet.of(TimerType.SCHEDULE_TIMER_1, TimerType.SCHEDULE_TIMER_2, TimerType.SCHEDULE_TIMER_3)
                .contains(newTimerType)) {
            return super.handleCommandInternalEx(command); // Not a schedule timer requested -> handle regularly
        }

        // This boilerplate could be refactored and cut down significantly (but... well...)
        // Using Nullables to cut on the Optionals (and redundant orElseThrow()), where the value impossible to be a
        // null
        @Nullable
        EnumSet<Weekday> activeDays = null;
        @Nullable
        LocalTime scheduleOnTime = null;
        @Nullable
        LocalTime scheduleOffTime = null;
        try { // get values from settings
            switch (newTimerType) {
                case SCHEDULE_TIMER_1:
                    activeDays = settingsProvider.getScheduleProvider().getSchedule1DayOfWeek();
                    scheduleOnTime = settingsProvider.getScheduleProvider().getSchedule1OnTime();
                    scheduleOffTime = settingsProvider.getScheduleProvider().getSchedule1OffTime();
                    break;
                case SCHEDULE_TIMER_2:
                    activeDays = settingsProvider.getScheduleProvider().getSchedule2DayOfWeek();
                    scheduleOnTime = settingsProvider.getScheduleProvider().getSchedule2OnTime();
                    scheduleOffTime = settingsProvider.getScheduleProvider().getSchedule2OffTime();
                    break;
                case SCHEDULE_TIMER_3:
                    activeDays = settingsProvider.getScheduleProvider().getSchedule3DayOfWeek();
                    scheduleOnTime = settingsProvider.getScheduleProvider().getSchedule3OnTime();
                    scheduleOffTime = settingsProvider.getScheduleProvider().getSchedule3OffTime();
                    break;
                default:
                    throw new IllegalStateException("Unsupported schedule timer type"); // Just a fail-safe, can never
                                                                                        // happen due to the early
                                                                                        // return above
            }
        } catch (ArgoConfigurationException e) {
            logger.warn("Invalid schedule configuration for {}. Error: {}", newTimerType, e.getMessage());
            return HandleCommandResult.rejected(); // This technically won't ever happen as invalid config would fail
                                                   // binding startup (aka. way before control ever reaches this place)
        }

        logger.info("New timer value is: {}. Days={}, On={}, Off={}", newTimerType, activeDays, scheduleOnTime,
                scheduleOffTime);

        // get the elements that need to update with additional commands (now that the timer has been selected)
        var timerDays = settingsProvider.getSetting(ArgoDeviceSettingType.TIMER_N_ENABLED_DAYS);
        var timerOn = settingsProvider.getSetting(ArgoDeviceSettingType.TIMER_N_ON_TIME);
        var timerOff = settingsProvider.getSetting(ArgoDeviceSettingType.TIMER_N_OFF_TIME);

        // send the respective commands
        timerOn.handleCommand(
                new DecimalType(TimeParam.fromHhMm(scheduleOnTime.getHour(), scheduleOnTime.getMinute())));
        timerOff.handleCommand(
                new DecimalType(TimeParam.fromHhMm(scheduleOffTime.getHour(), scheduleOffTime.getMinute())));
        timerDays.handleCommand(new DecimalType(WeekdayParam.toRawValue(activeDays)));

        // finally go back to handling the timer type (as a regular enum)
        return super.handleCommandInternalEx(command);
    }
}
