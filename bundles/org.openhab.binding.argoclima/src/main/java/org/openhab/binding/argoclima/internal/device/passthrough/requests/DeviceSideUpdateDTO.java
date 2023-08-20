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
package org.openhab.binding.argoclima.internal.device.passthrough.requests;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.DatatypeConverter;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Device's update - sent from AC to manufacturer's remote server (via GET ...?CM=GET_UI_FLG command)
 * <p>
 * These are the most common updates the device sends routinely to the vendor server
 *
 * @implNote The "SETUP" part is a particular goldmine for interesting stuff not available anywhere else
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class DeviceSideUpdateDTO {
    /////////////
    // TYPES
    /////////////
    /**
     * Provides parsing of "SETUP" part of query string, which seems to be base-16 encoded binary blob
     *
     * @implNote The values are based on guesswork and reverse engineering. Notable unknowns:
     *           - 4-byte value on bits 128-131 (TZ config?)
     *           - 20-byte value on bits 176-195 (?? - some value seems to be embedded on bytes 4..15 of it)
     *           - 34-byte value on bits 222-255 (?? - seem to be some reserved field + trailing padding "ABCD..u ")
     *
     * @author Mateusz Bronk - Initial contribution
     */
    public class UiFlgSetupParam {
        /** The value as sent by the device (Base16) */
        public final String rawString;

        /** The binary value upon conversion (empty on conversion failure) */
        private Optional<byte[]> bytes = Optional.empty();

        /** The WiFi SSID embedded on bytes 0..31 of the blob (or empty - on parse failure) */
        public Optional<String> wifiSSID = Optional.empty();

        /** The WiFi password embedded on bytes 32..63 of the blob (or empty - on parse failure) */
        public Optional<String> wifiPassword = Optional.empty();

        /** The UI username embedded on bytes 64..79 of the blob (or empty - on parse failure) */
        public Optional<String> username = Optional.empty();

        /** The UI password embedded on bytes 80..111 of the blob (or empty - on parse failure) */
        public Optional<String> password = Optional.empty();

        /** The local IPv4 of the device embedded on bytes 112-127 of the blob (or empty - on parse failure) */
        public Optional<String> localIP = Optional.empty();

        /**
         * The installed(?) WiFi firmware version embedded on bytes 132-137 of the blob (or empty - on parse failure)
         */
        public Optional<String> wifiVersionInstalled = Optional.empty();

        /**
         * The available(?) WiFi firmware version embedded on bytes 138-143 of the blob (or empty - on parse failure)
         */
        public Optional<String> wifiVersionAvailable = Optional.empty();

        /**
         * The installed(?) Unit firmware version embedded on bytes 164-169 of the blob (or empty - on parse failure)
         */
        public Optional<String> unitVersionInstalled = Optional.empty();

        /**
         * The available(?) Unit firmware version embedded on bytes 170-175 of the blob (or empty - on parse failure)
         */
        public Optional<String> unitVersionAvailable = Optional.empty();

        /**
         * ISO-formated local time (ending with whitespace) embedded on bytes 196-221 of the blob (or empty - on parse
         * failure)
         *
         * @implNote Parsed as a 32-byte array for simplicity sake
         */
        public Optional<String> localTime = Optional.empty();

        /**
         * C-tor
         *
         * @param rawString The raw 'setup' param string send by device
         * @param showCleartextPasswords If true, do not mask passwords the device sends with {@code ***}
         *            Note this is not a security feature (passwords are still sent!)
         */
        public UiFlgSetupParam(String rawString, boolean showCleartextPasswords) {
            this.rawString = rawString;
            try {
                this.bytes = Optional.of(DatatypeConverter.parseHexBinary(rawString));
                var bb = ByteBuffer.wrap(this.bytes.get());

                // helper structures to parse with
                var byte32arr = new byte[32];
                var byte16arr = new byte[16];
                var byte6arr = new byte[6];

                bb.get(byte32arr);
                this.wifiSSID = Optional.of(new String(byte32arr).trim());

                bb.get(byte32arr);
                this.wifiPassword = Optional.of(new String(byte32arr).trim()); // yep, it is passed through to vendor's
                                                                               // servers **as plaintext**. Over plain
                                                                               // HTTP! :///
                if (!showCleartextPasswords) {
                    this.wifiPassword = Optional.of(this.wifiPassword.get().replaceAll(".", "*"));
                }

                bb.position(0x40);
                bb.get(byte16arr);
                this.username = Optional.of(new String(byte16arr).trim());
                bb.get(byte32arr);
                this.password = Optional.of(new String(byte32arr).trim());
                if (!showCleartextPasswords) {
                    this.password = Optional.of(this.password.get().replaceAll(".", "*"));
                }
                bb.get(byte16arr);
                this.localIP = Optional.of(new String(byte16arr).trim());

                bb.position(0x84);
                bb.get(byte6arr);
                this.wifiVersionInstalled = Optional.of(new String(byte6arr).trim());
                bb.get(byte6arr);
                this.wifiVersionAvailable = Optional.of(new String(byte6arr).trim());

                bb.position(0xa4);
                bb.get(byte6arr);
                this.unitVersionInstalled = Optional.of(new String(byte6arr).trim());
                bb.get(byte6arr);
                this.unitVersionAvailable = Optional.of(new String(byte6arr).trim());

                bb.position(0xc4);
                bb.get(byte32arr);
                this.localTime = Optional.of(new String(byte32arr).trim());
            } catch (IllegalArgumentException | BufferUnderflowException ex) {
                logger.trace("Unrecognized device setup string: {}. Exception: {}", rawString, ex.getMessage());
                this.bytes = Optional.empty(); // Removing raw bytes just to indicate we failed parsing somewhere
            }
        }

        @Override
        public String toString() {
            return String.format(
                    "Device-side setup data:\n\twifi=[SSID=%s, password=%s],\n\tUser:password=%s:%s,\n\tIP=%s,\n\tWifi FW=[Installed=%s | Available=%s],\n\tUnit FW=[Installed=%s | Available=%s]\n\tLocal time=%s",
                    this.wifiSSID.orElse("???"), this.wifiPassword.orElse("???"), this.username.orElse("???"),
                    this.password.orElse("???"), this.localIP.orElse("???"), this.wifiVersionInstalled.orElse("???"),
                    this.wifiVersionAvailable.orElse("???"), this.unitVersionInstalled.orElse("???"),
                    this.unitVersionAvailable.orElse("???"), this.localTime.orElse("???"));
        }
    }

    /////////////
    // FIELDS
    /////////////
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /** The {@code CM} part of the request. Seems to be fixed to {@code UI_FLG} for this format */
    public final String command;

    /** The {@code USN} part of the request. Carries the web UI username */
    public final String username;

    /** The {@code PSW} part of the request. Carries a MD5 of web UI password */
    public final String passwordHash;

    /** The {@code IP} part of the request. Carries a local IP of the HVAC device */
    public final String deviceIp;

    /** The {@code FW_OU} part of the request. Carries current version of unit's firmware */
    public final String unitFirmware;

    /** The {@code FW_UI} part of the request. Carries current version of WiFi firmware */
    public final String wifiFirmware;

    /** The {@code CPU_ID} part of the request. Carries a unique HVAC device chip ID */
    public final String cpuId;

    /**
     * The {@code HMI} part of the request. Carries current status of the HVAC and may be parsed using
     * {@link org.openhab.binding.argoclima.internal.device.api.protocol.ArgoDeviceStatus#fromDeviceString(String) }
     */
    public final String currentValues;

    /** The {@code TZ} part of the request. Carries device's local timezone(?) */
    public final String timezoneId;

    /** The {@code SETUP} part of the request. Carries rich setup data, including passwords etc. */
    public final UiFlgSetupParam setup;

    /** The {@code SERVER_ID} part of the request. Carries a the vendor's remote server DNS name */
    public final String remoteServerId;

    /**
     * Private c-tor (from pre-parsed request)
     *
     * @param parameterMap The body parameters converted to a K->V map
     * @param showCleartextPasswords If true, do not mask passwords the device sends with {@code ***}
     *            Note this is not a security feature (passwords are still sent!)
     */
    private DeviceSideUpdateDTO(Map<String, String> parameterMap, boolean showCleartextPasswords) {
        this.command = Objects.requireNonNullElse(parameterMap.get("CM"), "");
        this.username = Objects.requireNonNullElse(parameterMap.get("USN"), "");
        this.passwordHash = Objects.requireNonNullElse(parameterMap.get("PSW"), "");
        this.deviceIp = Objects.requireNonNullElse(parameterMap.get("IP"), "");
        this.unitFirmware = Objects.requireNonNullElse(parameterMap.get("FW_OU"), "");
        this.wifiFirmware = Objects.requireNonNullElse(parameterMap.get("FW_UI"), "");
        this.cpuId = Objects.requireNonNullElse(parameterMap.get("CPU_ID"), "");
        this.currentValues = Objects.requireNonNullElse(parameterMap.get("HMI"), "");
        this.timezoneId = Objects.requireNonNullElse(parameterMap.get("TZ"), "");
        this.setup = new UiFlgSetupParam(Objects.requireNonNullElse(parameterMap.get("SETUP"), ""),
                showCleartextPasswords);
        this.remoteServerId = Objects.requireNonNullElse(parameterMap.get("SERVER_ID"), "");
    }

    /**
     * Named c-tor (from device-side request)
     *
     * @param request The request sent by the device
     * @param showCleartextPasswords If true, do not mask passwords the device sends with {@code ***}
     *            Note this is not a security feature (passwords are still sent!)
     * @return Parsed DTO
     */
    public static DeviceSideUpdateDTO fromDeviceRequest(HttpServletRequest request, boolean showCleartextPasswords) {
        Map<String, String> flattenedParams = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, x -> (x.getValue().length < 1) ? "" : x.getValue()[0]));
        return new DeviceSideUpdateDTO(flattenedParams, showCleartextPasswords);
    }

    @Override
    public String toString() {
        return String.format(
                "Device-side update:\n\tCommand=%s,\n\tCredentials=[username=%s, password(MD5)=%s],\n\tIP=%s,\n\tFW=[Unit=%s | Wifi=%s],\n\tCPU_ID=%s,\n\tParameters=%s,\n\tSetup={%s},\n\tRemoteServer=%s.",
                this.command, this.username, this.passwordHash, this.deviceIp, this.unitFirmware, this.wifiFirmware,
                this.cpuId, this.currentValues, this.setup.toString().replaceAll("(?m)^", "\t"), this.remoteServerId);
    }
}
