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
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class CurrentWeekdayParam extends ArgoApiElementBase {

    private static final Logger logger = LoggerFactory.getLogger(CurrentWeekdayParam.class);

    public CurrentWeekdayParam(IArgoSettingProvider settingsProvider) {
        super(settingsProvider);
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        logger.warn("Got state: {} for a parameter that doesn't support it!", responseValue);
    }

    private static DayOfWeek utcToday() {
        return ZonedDateTime.now(ZoneId.of("UTC")).getDayOfWeek();
    }

    @Override
    public State toState() {
        return new org.openhab.core.library.types.StringType(
                utcToday().getDisplayName(TextStyle.SHORT_STANDALONE, Locale.US));
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        logger.warn("Got command for a parameter that doesn't support it!");
        return HandleCommandResult.rejected(); // Does not handle any commands
    }

    @Override
    public boolean isAlwaysSent() {
        return true;
    }

    @Override
    public String toString() {
        // TODO Auto-generated method stub
        return super.toString();
    }

    @Override
    public String getDeviceApiValue() {
        return Integer.toString(Weekday.ofDay(utcToday()).getIntValue());
    }
}
