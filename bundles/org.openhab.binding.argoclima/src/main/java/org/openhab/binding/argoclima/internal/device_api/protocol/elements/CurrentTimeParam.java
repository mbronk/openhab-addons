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

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.eclipse.jdt.annotation.NonNullByDefault;
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
public class CurrentTimeParam extends ArgoApiElementBase {
    private static final Logger logger = LoggerFactory.getLogger(CurrentTimeParam.class);

    public CurrentTimeParam(IArgoSettingProvider settingsProvider) {
        super(settingsProvider);
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        logger.warn("Got state: {} for a parameter that doesn't support it!", responseValue);
    }

    private static ZonedDateTime utcNow() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }

    @Override
    protected State getAsState() {
        return new org.openhab.core.library.types.DateTimeType(utcNow());
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        logger.warn("Got command for a parameter that doesn't support it!");
        return new HandleCommandResult(false); // Does not handle any commands
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
        // var test = this.settingsProvider.getSetting(ArgoDeviceSettingType.ACTIVE_TIMER);
        // logger.warn("TODO REMOVEME: Active timer is now: {}", test);
        var t = utcNow();
        return Integer.toString(TimeParam.fromHhMm(t.getHour(), t.getMinute()));
    }

}
