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
package org.openhab.binding.argoclima.internal.configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.i18n.ConfigurationException;

/**
 * The {@link ArgoClimaConfigurationBase} class contains fields mapping thing configuration parameters.
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public abstract class ArgoClimaConfigurationBase {
    public static enum Weekday {
        MON,
        TUE,
        WED,
        THU,
        FRI,
        SAT,
        SUN
    }

    /**
     * Sample configuration parameters. Replace with your own.
     */
    public String deviceCpuId = "";
    public int refreshInterval = -1;
    public int oemServerPort = -1;
    public String oemServerAddress = "";

    public List<Weekday> schedule1DayOfWeek = List.of();
    public String schedule1OnTime = "";
    public String schedule1OffTime = "";
    public List<Weekday> schedule2DayOfWeek = List.of();
    public String schedule2OnTime = "";
    public String schedule2OffTime = "";
    public List<Weekday> schedule3DayOfWeek = List.of();
    public String schedule3OnTime = "";
    public String schedule3OffTime = "";

    public boolean resetToFactoryDefaults = false;
    // public String password = ""; <-- for remote device only

    // public static final int LOCAL_PORT = 1001;
    // hvacChangeDebounce

    //
    // /**
    // * The currentTemperatureOffset is configureable in case the user wants to offset this temperature for calibration
    // * of the temperature sensor.
    // */
    // public BigDecimal currentTemperatureOffset = new BigDecimal(0.0);
    //
    protected static <T> String getOrDefault(Supplier<T> fn, String defaultValue) {
        try {
            return fn.get().toString();
        } catch (Exception e) {
            return defaultValue + "[raw]";
        }
    }

    @Override
    public final String toString() {
        return String.format("Config: { %s, deviceCpuId=%s, refreshInterval=%d, oemServerPort=%d, oemServerAddress=%s,"
                + "schedule1DayOfWeek=%s, schedule1OnTime=%s, schedule1OffTime=%s, schedule2DayOfWeek=%s, schedule2OnTime=%s, schedule2OffTime=%s, schedule3DayOfWeek=%s, schedule3OnTime=%s, schedule3OffTime=%s, resetToFactoryDefaults=%s}",
                getExtraFieldDescription(), deviceCpuId, refreshInterval, oemServerPort,
                getOrDefault(this::getOemServerAddress, oemServerAddress), schedule1DayOfWeek, schedule1OnTime,
                schedule1OffTime, schedule2DayOfWeek, schedule2OnTime, schedule2OffTime, schedule3DayOfWeek,
                schedule3OnTime, schedule3OffTime, resetToFactoryDefaults);
    }

    protected abstract String getExtraFieldDescription();

    public InetAddress getOemServerAddress() {
        try {
            return InetAddress.getByName(oemServerAddress);
        } catch (UnknownHostException e) {
            throw new ConfigurationException("Invalid oemServerAddress configuration", e);
        }
    }

    protected abstract String validateInternal() throws Exception;

    /**
     * Validate current config
     *
     * @return Error message if config is invalid. Empty string - otherwise
     */
    public final String validate() {
        if (refreshInterval < 0) {
            return "Refresh interval must be >= 0";
        }

        if (oemServerPort < 0 || oemServerPort >= 65536) {
            return "OEM server port must be in range [0..65536]";
        }

        try { // want the side-effect of these calls
            getOemServerAddress();
            return validateInternal();
        } catch (Exception e) {
            var msg = e.getMessage();
            if (e.getCause() != null) {
                msg += ". " + Objects.requireNonNull(e.getCause()).getMessage();
            }
            return Objects.requireNonNullElse(msg, "Exception cause is null");
        }
    }

    // isValid
}
