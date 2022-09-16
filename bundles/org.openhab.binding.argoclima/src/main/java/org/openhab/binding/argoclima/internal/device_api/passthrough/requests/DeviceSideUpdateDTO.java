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
package org.openhab.binding.argoclima.internal.device_api.passthrough.requests;

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
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class DeviceSideUpdateDTO {
    private static final Logger logger = LoggerFactory.getLogger(DeviceSideUpdateDTO.class);

    public final String command;
    public final String username;
    public final String passwordHash;
    public final String deviceIp;
    public final String unitFirmware;
    public final String wifiFirmware;
    public final String cpuId;
    public final String currentValues;
    public final String timezoneId;
    public final UiFlgSetupParam setup;
    public final String remoteServerId;

    private DeviceSideUpdateDTO(Map<String, String> parameterMap) {
        this.command = Objects.requireNonNullElse(parameterMap.get("CM"), "");
        this.username = Objects.requireNonNullElse(parameterMap.get("USN"), "");
        this.passwordHash = Objects.requireNonNullElse(parameterMap.get("PSW"), "");
        this.deviceIp = Objects.requireNonNullElse(parameterMap.get("IP"), "");
        this.unitFirmware = Objects.requireNonNullElse(parameterMap.get("FW_OU"), "");
        this.wifiFirmware = Objects.requireNonNullElse(parameterMap.get("FW_UI"), "");
        this.cpuId = Objects.requireNonNullElse(parameterMap.get("CPU_ID"), "");
        this.currentValues = Objects.requireNonNullElse(parameterMap.get("HMI"), "");
        this.timezoneId = Objects.requireNonNullElse(parameterMap.get("TZ"), "");
        this.setup = new UiFlgSetupParam(Objects.requireNonNullElse(parameterMap.get("SETUP"), ""));
        this.remoteServerId = Objects.requireNonNullElse(parameterMap.get("SERVER_ID"), "");
    }

    public static DeviceSideUpdateDTO fromDeviceRequest(HttpServletRequest request) {
        Map<String, String> flattenedParams = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, x -> (x.getValue().length < 1) ? "" : x.getValue()[0]));
        return new DeviceSideUpdateDTO(flattenedParams);
    }

    @Override
    public String toString() {
        return String.format(
                "Device-side update:\n\tCommand=%s,\n\tCredentials=[username=%s, password(MD5)=%s],\n\tIP=%s,\n\tFW=[Unit=%s | Wifi=%s],\n\tCPU_ID=%s,\n\tParameters=%s,\n\tSetup={%s},\n\tRemoteServer=%s.",
                this.command, this.username, this.passwordHash, this.deviceIp, this.unitFirmware, this.wifiFirmware,
                this.cpuId, this.currentValues, this.setup.toString().replaceAll("(?m)^", "\t"), this.remoteServerId);
    }

    public class UiFlgSetupParam {
        public final String rawString;
        private Optional<byte[]> bytes = Optional.empty();
        public Optional<String> wifiSSID = Optional.empty();
        public Optional<String> wifiPassword = Optional.empty();
        public Optional<String> username = Optional.empty();
        public Optional<String> password = Optional.empty();
        public Optional<String> localIP = Optional.empty();
        public Optional<String> wifiVersionInstalled = Optional.empty();
        public Optional<String> wifiVersionAvailable = Optional.empty();
        public Optional<String> unitVersionInstalled = Optional.empty();
        public Optional<String> unitVersionAvailable = Optional.empty();
        public Optional<String> localTime = Optional.empty();

        public UiFlgSetupParam(String rawString) {
            this.rawString = rawString;
            try {
                this.bytes = Optional.of(DatatypeConverter.parseHexBinary(rawString));

                var bb = ByteBuffer.wrap(this.bytes.get());
                var byte32arr = new byte[32];
                var byte16arr = new byte[16];
                var byte6arr = new byte[6];

                bb.get(byte32arr);
                this.wifiSSID = Optional.of(new String(byte32arr).trim());

                bb.get(byte32arr);
                this.wifiPassword = Optional.of(new String(byte32arr).trim()); // yep, it is passed through to vendor's
                                                                               // servers **as plaintext**. Over plain
                                                                               // HTTP! :///
                this.wifiPassword = Optional.of(this.wifiPassword.get().replaceAll(".", "*"));

                bb.position(0x40);
                bb.get(byte16arr);
                this.username = Optional.of(new String(byte16arr).trim());
                bb.get(byte32arr);
                this.password = Optional.of(new String(byte32arr).trim());
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
                this.bytes = Optional.empty();
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
}