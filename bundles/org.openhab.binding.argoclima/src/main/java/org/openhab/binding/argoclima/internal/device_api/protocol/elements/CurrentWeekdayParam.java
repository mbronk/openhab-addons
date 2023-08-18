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

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase.Weekday;
import org.openhab.binding.argoclima.internal.device_api.protocol.IArgoSettingProvider;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The element reporting current day of week to the device
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class CurrentWeekdayParam extends ArgoApiElementBase {
    private static final Logger logger = LoggerFactory.getLogger(CurrentWeekdayParam.class);

    /**
     * C-tor
     *
     * @param settingsProvider the settings provider (getting device state as well as schedule configuration)
     */
    public CurrentWeekdayParam(IArgoSettingProvider settingsProvider) {
        super(settingsProvider);
    }

    private static DayOfWeek utcToday() {
        return ZonedDateTime.now(ZoneId.of("UTC")).getDayOfWeek();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This element doesn't really get any device-side commands
     */
    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        logger.warn("Got state: {} for a parameter that doesn't support it!", responseValue);
    }

    @Override
    public State toState() {
        return new org.openhab.core.library.types.StringType(
                utcToday().getDisplayName(TextStyle.SHORT_STANDALONE, Locale.US));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The current day of week is always sent
     */
    @Override
    public boolean isAlwaysSent() {
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Specialized implementation, always providing latest *today* value
     *
     * @implNote deliberately using ordinal, not getIntValue() here as the latter is for bitmasks!
     */
    @Override
    public String getDeviceApiValue() {
        return Integer.toString(Weekday.ofDay(utcToday()).ordinal());
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        logger.warn("Got command for a parameter that doesn't support it!");
        return HandleCommandResult.rejected(); // Does not handle any commands
    }
}
