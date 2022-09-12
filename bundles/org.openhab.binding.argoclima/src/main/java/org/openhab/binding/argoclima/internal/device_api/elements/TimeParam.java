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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.DateTimeType;
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
    private static final Logger logger = LoggerFactory.getLogger(TimeParam.class);

    private Optional<LocalTime> currentValue = Optional.empty();

    private int minValue;
    private int maxValue;

    public TimeParam() {
        this.minValue = fromHhMm(0, 0);
        this.maxValue = fromHhMm(23, 59);
    }

    public TimeParam(int minValue, int maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    private static State valueToState(Optional<LocalTime> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }
        // todo this is sketchy
        return new DateTimeType(ZonedDateTime.of(LocalDate.now(), value.get(), ZoneId.systemDefault()));
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

        this.currentValue = Optional.of(LocalTime.of(hh, mm));
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
        // TODO:
        // if (newValue < minValue) {
        // logger.warn("Requested value: {} would exceed minimum value: {}. Setting: {}.", newValue, minValue,
        // minValue);
        // newValue = minValue;
        // }
        // if (newValue > maxValue) {
        // logger.warn("Requested value: {} would exceed maximum value: {}. Setting: {}.", newValue, maxValue,
        // maxValue);
        // newValue = maxValue;
        // }
        return new HandleCommandResult(false);
    }

}
