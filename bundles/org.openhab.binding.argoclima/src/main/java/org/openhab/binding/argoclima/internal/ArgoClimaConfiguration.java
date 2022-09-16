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
package org.openhab.binding.argoclima.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.i18n.ConfigurationException;

/**
 * The {@link ArgoClimaConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaConfiguration {
    public static enum ConnectionMode {
        LOCAL_CONNECTION,
        REMOTE_API_STUB,
        REMOTE_API_PROXY
    }

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
    public String hostname = "";
    public String localDeviceIP = ""; // TODO: parameterize
    public int localDevicePort = 1001;
    public String deviceCpuId = "";
    public ConnectionMode connectionMode = ConnectionMode.LOCAL_CONNECTION;
    public boolean useLocalConnection = true;
    public int refreshInterval = -1;
    public int stubServerPort = -1;
    public List<String> stubServerListenAddresses = List.of();
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
    private static <T> String getOrDefault(Supplier<T> fn, String defaultValue) {
        try {
            return fn.get().toString();
        } catch (Exception e) {
            return defaultValue + "[raw]";
        }
    }

    @Override
    public String toString() {
        return String.format(
                "Config: { hostname=%s, localDeviceIP=%s, localDevicePort=%d, deviceCpuId=%s, connectionMode=%s, useLocalConnection=%s, refreshInterval=%d, stubServerPort=%d, stubServerListenAddresses=%s, oemServerPort=%d, oemServerAddress=%s,"
                        + "schedule1DayOfWeek=%s, schedule1OnTime=%s, schedule1OffTime=%s, schedule2DayOfWeek=%s, schedule2OnTime=%s, schedule2OffTime=%s, schedule3DayOfWeek=%s, schedule3OnTime=%s, schedule3OffTime=%s, resetToFactoryDefaults=%s}",
                getOrDefault(this::getHostname, hostname), getOrDefault(this::getLocalDeviceIP, localDeviceIP),
                localDevicePort, deviceCpuId, connectionMode, useLocalConnection, refreshInterval, stubServerPort,
                getOrDefault(this::getStubServerListenAddresses, stubServerListenAddresses.toString()), oemServerPort,
                getOrDefault(this::getOemServerAddress, oemServerAddress), schedule1DayOfWeek, schedule1OnTime,
                schedule1OffTime, schedule2DayOfWeek, schedule2OnTime, schedule2OffTime, schedule3DayOfWeek,
                schedule3OnTime, schedule3OffTime, resetToFactoryDefaults);
    }

    public InetAddress getHostname() {
        try {
            return InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            throw new ConfigurationException("Invalid hostname configuration", e);
        }
    }

    public Set<InetAddress> getStubServerListenAddresses() {
        var addresses = stubServerListenAddresses.stream().map(t -> {
            try {
                return Optional.<InetAddress>ofNullable(InetAddress.getByName(t));
            } catch (UnknownHostException e) {
                throw new ConfigurationException("Invalid stubServerListenAddresses configuration", e);
            }
        }).collect(Collectors.toList());

        if (addresses.stream().anyMatch(Optional::isEmpty)) {
            throw new ConfigurationException("Invalid stubServerListenAddresses configuration. Inet address is empty");
        }
        return addresses.stream().map(x -> x.get()).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public InetAddress getLocalDeviceIP() {
        try {
            return InetAddress.getByName(localDeviceIP);
        } catch (UnknownHostException e) {
            throw new ConfigurationException("Invalid localDeviceIP configuration", e);
        }
    }

    public InetAddress getOemServerAddress() {
        try {
            return InetAddress.getByName(oemServerAddress);
        } catch (UnknownHostException e) {
            throw new ConfigurationException("Invalid oemServerAddress configuration", e);
        }
    }

    /**
     * Validate current config
     *
     * @return Error message if config is invalid. Empty string - otherwise
     */
    public String validate() {
        if (hostname.isEmpty()) {
            return "Hostname is empty. Must be set to Argo Air Conditioner's local address";
        }

        if (refreshInterval < 0) {
            return "Refresh interval must be >= 0";
        }

        if (!useLocalConnection && connectionMode == ConnectionMode.LOCAL_CONNECTION) {
            return "Cannot set Use Local Connection to OFF, when connection mode is LOCAL_CONNECTION";
        }

        if (refreshInterval == 0 && connectionMode == ConnectionMode.LOCAL_CONNECTION) {
            return "Cannot set refresh interval to 0, when connection mode is LOCAL_CONNECTION";
        }

        if (localDevicePort < 0 || localDevicePort >= 65536) {
            return "Local Device Port must be in range [0..65536]";
        }

        if (oemServerPort < 0 || oemServerPort >= 65536) {
            return "OEM server port must be in range [0..65536]";
        }

        if (stubServerPort < 0 || stubServerPort >= 65536) {
            return "Stub server port must be in range [0..65536]";
        }

        try { // want the side-effect of these calls
            getHostname();
            getStubServerListenAddresses();
            getLocalDeviceIP();
            getOemServerAddress();
        } catch (Exception e) {
            var msg = e.getMessage();
            if (e.getCause() != null) {
                msg += ". " + e.getCause().getMessage();
            }
            return msg;
        }
        return "";
    }

    // isValid
}
