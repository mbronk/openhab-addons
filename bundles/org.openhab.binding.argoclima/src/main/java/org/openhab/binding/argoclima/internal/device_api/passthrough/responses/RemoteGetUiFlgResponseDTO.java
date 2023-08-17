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
package org.openhab.binding.argoclima.internal.device_api.passthrough.responses;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.protocol.ArgoDeviceStatus;

/**
 * Cloud-side response to GET UI_FLG command - sent from manufacturer's remote server back to HVAC
 *
 * @implNote Example full response is like
 *           {@code {|1|0|1|0|0|0|N,N,N,2,0,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N|}[|0|||]]}
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class RemoteGetUiFlgResponseDTO {
    /////////////
    // TYPES
    /////////////
    /**
     * The preamble part of the response containing flags
     *
     * @implNote Example: {@code |1|0|1|0|0|0|}
     * @author Mateusz Bronk - Initial contribution
     */
    public final static class UiFlgResponsePreamble {
        final static Pattern preambleRx = Pattern.compile("^[|](\\d[|]){6}$");

        /** Request the HVAC to send an immediate update via POST (used on cloud-side updates) */
        public int Flag_0_Request_POST_UI_RT = 0;

        /** Unknown purpose, always zero */
        public int Flag_1_Always_Zero = 0;

        /** Unknown purpose, always one */
        public int Flag_2_Always_One = 1;

        /** Request to update WiFi firmware of the device */
        public int Flag_3_Update_Wifi_FW = 0;

        /** Request to update Unit firmware of the device */
        public int Flag_4_Update_Unit_FW = 0;

        /** Cloud has new updates for the device - request to apply (silently, with no beep) */
        public int Flag_5_Has_New_Update = 0;

        /**
         * Default C-tor (empty, if constructed vanilla)
         */
        public UiFlgResponsePreamble() {
        }

        /**
         * Private c-tor (from pre-parsed preamble headers)
         *
         * @param flags Parsed preamble
         */
        private UiFlgResponsePreamble(int[] flags) {
            if (flags.length != 6) {
                throw new IllegalArgumentException("flags");
            }
            this.Flag_0_Request_POST_UI_RT = flags[0]; // When Device sends DEL=1, remote API requests it
            this.Flag_1_Always_Zero = flags[1];
            this.Flag_2_Always_One = flags[2];
            this.Flag_3_Update_Wifi_FW = flags[3];
            this.Flag_4_Update_Unit_FW = flags[4];
            this.Flag_5_Has_New_Update = flags[5];
        }

        /**
         * Named c-tor
         *
         * @param preambleString The preamble string from parsed response
         * @return This DTO
         */
        public static UiFlgResponsePreamble fromResponseString(String preambleString) {
            // Preamble: |1|0|1|1|0|0|
            if (!preambleRx.matcher(preambleString).matches()) {
                throw new IllegalArgumentException("preambleString");
            }
            var flags = Stream.of(preambleString.substring(1).split("[|]")).mapToInt(Integer::parseInt).toArray();
            return new UiFlgResponsePreamble(flags);
        }

        /**
         * Converts internal representation back to Argo-compatible preamble
         *
         * @return Preamble in proto-friendly format
         */
        public String toResponseString() {
            return String.format("|%d|%d|%d|%d|%d|%d|", Flag_0_Request_POST_UI_RT, Flag_1_Always_Zero,
                    Flag_2_Always_One, Flag_3_Update_Wifi_FW, Flag_4_Update_Unit_FW, Flag_5_Has_New_Update);
        }
    }

    /**
     * The "body" of the response (36-element), compatible with HMI command syntax produced by
     * {@link ArgoDeviceStatus#getDeviceCommandStatus()}
     *
     * @implNote Example: {@code N,N,N,2,0,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N}
     * @author Mateusz Bronk - Initial contribution
     */
    public final static class UiFlgResponseCommmands {
        final static int COMMANDS_LENGTH = 36;
        final String[] commands = new String[COMMANDS_LENGTH];

        /**
         * Default C-tor (empty, if constructed vanilla)
         */
        public UiFlgResponseCommmands() {
            Arrays.fill(this.commands, "N");
        }

        /**
         * Private c-tor (from pre-parsed "body")
         *
         * @param commands The device commands to execute (HMI-like syntax)
         */
        private UiFlgResponseCommmands(String[] commands) {
            if (commands.length != COMMANDS_LENGTH) {
                throw new IllegalArgumentException("commands");
            }
            System.arraycopy(commands, 0, this.commands, 0, COMMANDS_LENGTH);
        }

        /**
         * Named c-tor
         *
         * @param commandString The "command" part of parsed response
         * @return This DTO
         */
        public static UiFlgResponseCommmands fromResponseString(String commandString) {
            String[] values = commandString.split(",");
            if (values.length != COMMANDS_LENGTH) {
                throw new IllegalArgumentException("commandString");
            }
            return new UiFlgResponseCommmands(values);
        }

        /**
         * Converts internal representation back to Argo-compatible body
         *
         * @return Commands in proto-friendly format
         */
        public String toResponseString() {
            return String.join(",", commands);
        }
    }

    /**
     * The "suffix" part of the response (of unknown purpose)
     *
     * @implNote Example: {@code [|0|||]}
     * @author Mateusz Bronk - Initial contribution
     */
    public final static class UiFlgResponseUpd {
        final static String CANNED_RESPONSE = "[|0|||]";
        final String contents;

        /**
         * Default C-tor (empty, if constructed vanilla)
         */
        public UiFlgResponseUpd() {
            this.contents = CANNED_RESPONSE;
        }

        /**
         * Private c-tor (from pre-parsed "body")
         *
         * @param contents Actual postamble contents (pre-parsed)
         */
        private UiFlgResponseUpd(String contents) {
            this.contents = contents;
        }

        /**
         * Named c-tor
         *
         * @param updString The actual UPD (postamble) string sent
         * @return This DTO
         */
        public static UiFlgResponseUpd fromResponseString(String updString) {
            return new UiFlgResponseUpd(updString);
        }

        /**
         * Converts internal representation back to Argo-compatible postamble
         *
         * @return Postamble in proto-friendly format
         */
        public String toResponseString() {
            return contents;
        }
    }

    /**
     * The trailing part of the response (seems to be included as a server indicating something to the effect of
     * 'Connection: Close')
     *
     * @implNote Example: {@code ACN_FREE <br>\t\t}
     * @author Mateusz Bronk - Initial contribution
     */
    public final static class UiFlgResponseACN {
        final static String CANNED_RESPONSE = "ACN_FREE <br>\t\t";
        final String contents;

        /**
         * Default C-tor (do a connection close)
         */
        public UiFlgResponseACN() {
            this.contents = CANNED_RESPONSE;
        }

        /**
         * Private c-tor (from parsed part of response)
         *
         * @param contents The pre-parsed suffix
         */
        private UiFlgResponseACN(String contents) {
            this.contents = contents;
        }

        /**
         * Named c-tor (from raw response)
         *
         * @param updString The trailing part of response
         * @return This DTO
         */
        public static UiFlgResponseACN fromResponseString(String updString) {
            return new UiFlgResponseACN(updString);
        }

        /**
         * Converts internal representation back to Argo-compatible suffix
         *
         * @return Suffix in proto-friendly format
         */
        public String toResponseString() {
            return contents;
        }
    }

    /////////////
    // FIELDS
    /////////////
    final static Pattern GET_UI_FLG_RESPONSE_PATTERN = Pattern.compile(
            "^[\\{](?<preamble>([|]\\d)+[|])(?<commands>[^|]+)[|][\\}](?<updsuffix>\\[[^\\]]+\\])(?<acn>.*$)",
            Pattern.CASE_INSENSITIVE);
    final static String RESPONSE_FORMAT = "{%s%s|}%s%s";

    public UiFlgResponsePreamble preamble;
    public UiFlgResponseCommmands commands;
    public UiFlgResponseUpd updSuffix;
    public UiFlgResponseACN acnSuffix;

    /**
     * Default c-tor (synthetic response)
     */
    public RemoteGetUiFlgResponseDTO() {
        this.preamble = new UiFlgResponsePreamble();
        this.commands = new UiFlgResponseCommmands();
        this.updSuffix = new UiFlgResponseUpd();
        this.acnSuffix = new UiFlgResponseACN();
    }

    /**
     * Private c-tor (from pre-parsed actual response)
     *
     * @param preamble The preamble part of actual response
     * @param commands The command part of actual response
     * @param updSuffix The postamble part of actual response
     * @param acnSuffix The connection suffix part of actual response
     */
    private RemoteGetUiFlgResponseDTO(UiFlgResponsePreamble preamble, UiFlgResponseCommmands commands,
            UiFlgResponseUpd updSuffix, UiFlgResponseACN acnSuffix) {
        this.preamble = preamble;
        this.commands = commands;
        this.updSuffix = updSuffix;
        this.acnSuffix = acnSuffix;
    }

    /**
     * Named c-for (from actual upstream response)
     *
     * @param getUiFlgResponse The response body
     * @return This DTO
     */
    public static RemoteGetUiFlgResponseDTO fromResponseString(String getUiFlgResponse) {
        var matcher = GET_UI_FLG_RESPONSE_PATTERN.matcher(getUiFlgResponse);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("getUiFlgResponse");
        }

        return new RemoteGetUiFlgResponseDTO(UiFlgResponsePreamble.fromResponseString(matcher.group("preamble")),
                UiFlgResponseCommmands.fromResponseString(matcher.group("commands")),
                UiFlgResponseUpd.fromResponseString(matcher.group("updsuffix")),
                UiFlgResponseACN.fromResponseString(matcher.group("acn")));
    }

    /**
     * Converts internal representation back to Argo-compatible suffix
     *
     * @return UI_FLG response body in proto-friendly format
     */
    public String toResponseString() {
        return String.format(RESPONSE_FORMAT, this.preamble.toResponseString(), this.commands.toResponseString(),
                this.updSuffix.toResponseString(), this.acnSuffix.toResponseString());
    }
}
