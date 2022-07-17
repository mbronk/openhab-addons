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
package org.openhab.binding.argoclima.internal.device_api.passthrough.requests;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;

/**
 * Device's update - sent from AC to manufacturer's remote server (via POST ...CM=UI_RT command)
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class DeviceSidePostRtUpdateDTO {
    public final String command;
    public final String username;
    public final String passwordHash;
    public final String cpuId;
    public final String delParam;
    public final String dataParam;

    private DeviceSidePostRtUpdateDTO(Map<String, String> bodyArgumentMap) {
        this.command = Objects.requireNonNullElse(bodyArgumentMap.get("CM"), "");
        this.username = Objects.requireNonNullElse(bodyArgumentMap.get("USN"), "");
        this.passwordHash = Objects.requireNonNullElse(bodyArgumentMap.get("PSW"), "");
        this.cpuId = Objects.requireNonNullElse(bodyArgumentMap.get("CPU_ID"), "");

        this.delParam = Objects.requireNonNullElse(bodyArgumentMap.get("DEL"), "");
        this.dataParam = Objects.requireNonNullElse(bodyArgumentMap.get("DATA"), "");
    }

    @SuppressWarnings("null")
    public static DeviceSidePostRtUpdateDTO fromDeviceRequestBody(String requestBody) {
        var paramsParsed = new MultiMap<String>();
        UrlEncoded.decodeTo(requestBody, paramsParsed, StandardCharsets.US_ASCII);
        Map<String, String> flattenedParams = paramsParsed.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), x -> paramsParsed.getString(x)));
        return new DeviceSidePostRtUpdateDTO(flattenedParams);
    }

    @Override
    public String toString() {
        return String.format(
                "Device-side POST update:\n\tCommand=%s,\n\tCredentials=[username=%s, password(MD5)=%s],\n\tCPU_ID=%s,\n\tDEL=%s,\n\tDATA=%s.",
                this.command, this.username, this.passwordHash, this.cpuId, this.delParam, this.dataParam);
    }
}
