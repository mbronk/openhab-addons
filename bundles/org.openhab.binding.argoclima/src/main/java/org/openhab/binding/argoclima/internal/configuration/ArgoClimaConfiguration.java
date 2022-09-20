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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.i18n.ConfigurationException;

/**
 * The {@link ArgoClimaConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaConfiguration extends ArgoClimaConfigurationBase {
    public static enum ConnectionMode {
        LOCAL_CONNECTION,
        REMOTE_API_STUB,
        REMOTE_API_PROXY
    }

    /**
     * Sample configuration parameters. Replace with your own.
     */
    public String hostname = "";
    public String localDeviceIP = ""; // TODO: parameterize
    public int localDevicePort = 1001;
    public ConnectionMode connectionMode = ConnectionMode.LOCAL_CONNECTION;
    public boolean useLocalConnection = true;
    public int stubServerPort = -1;
    public List<String> stubServerListenAddresses = List.of();

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

    @Override
    protected String getExtraFieldDescription() {
        return String.format(
                "hostname=%s, localDeviceIP=%s, localDevicePort=%d, connectionMode=%s, useLocalConnection=%s, stubServerPort=%d, stubServerListenAddresses=%s",
                getOrDefault(this::getHostname, hostname), getOrDefault(this::getLocalDeviceIP, localDeviceIP),
                localDevicePort, connectionMode, useLocalConnection, stubServerPort,
                getOrDefault(this::getStubServerListenAddresses, stubServerListenAddresses.toString()));
    }

    @Override
    protected String validateInternal() throws Exception {
        if (hostname.isEmpty()) {
            return "Hostname is empty. Must be set to Argo Air Conditioner's local address";
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

        if (stubServerPort < 0 || stubServerPort >= 65536) {
            return "Stub server port must be in range [0..65536]";
        }

        // want the side-effect of these calls
        getHostname();
        getStubServerListenAddresses();
        getLocalDeviceIP();
        return "";
    }

    // isValid
}
