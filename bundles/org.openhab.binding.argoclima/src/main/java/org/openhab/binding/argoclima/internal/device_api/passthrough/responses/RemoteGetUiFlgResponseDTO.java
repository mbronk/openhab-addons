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
package org.openhab.binding.argoclima.internal.device_api.passthrough.responses;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class RemoteGetUiFlgResponseDTO {

    public final static class UiFlgResponsePreamble {
        public int Flag_0_Request_POST_UI_RT = 0;
        public int Flag_1_Always_Zero = 0;
        public int Flag_2_Always_One = 1;
        public int Flag_3_Update_Wifi_FW = 0;
        public int Flag_4_Update_Unit_FW = 0;
        public int Flag_5_Has_New_Update = 0;
        final static Pattern preambleRx = Pattern.compile("^[|](\\d[|]){6}$");

        public UiFlgResponsePreamble() {
        }

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

        public static UiFlgResponsePreamble fromResponseString(String preambleString) {
            // Preamble: |1|0|1|1|0|0|
            if (!preambleRx.matcher(preambleString).matches()) {
                throw new IllegalArgumentException("preambleString");
            }
            var flags = Stream.of(preambleString.substring(1).split("[|]")).mapToInt(Integer::parseInt).toArray();
            return new UiFlgResponsePreamble(flags);
        }

        public String toResponseString() {
            return String.format("|%d|%d|%d|%d|%d|%d|", Flag_0_Request_POST_UI_RT, Flag_1_Always_Zero,
                    Flag_2_Always_One, Flag_3_Update_Wifi_FW, Flag_4_Update_Unit_FW, Flag_5_Has_New_Update);
        }
    }

    public final static class UiFlgResponseCommmands {
        final static int COMMANDS_LENGTH = 36;
        final String[] commands = new String[COMMANDS_LENGTH];

        public UiFlgResponseCommmands() {
            Arrays.fill(this.commands, "N");
        }

        private UiFlgResponseCommmands(String[] commands) {
            if (commands.length != COMMANDS_LENGTH) {
                throw new IllegalArgumentException("commands");
            }
            System.arraycopy(commands, 0, this.commands, 0, COMMANDS_LENGTH);
        }

        public static UiFlgResponseCommmands fromResponseString(String commandString) {
            // N,N,N,N,N,N,N,N,N,N,N,0,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N
            String[] values = commandString.split(",");
            if (values.length != COMMANDS_LENGTH) {
                throw new IllegalArgumentException("commandString");
            }
            return new UiFlgResponseCommmands(values);
        }

        public String toResponseString() {
            return String.join(",", commands);
        }
    }

    public final static class UiFlgResponseUpd {
        final static String CANNED_RESPONSE = "[|0|||]";
        final String contents;

        public UiFlgResponseUpd() {
            this.contents = CANNED_RESPONSE;
        }

        private UiFlgResponseUpd(String contents) {
            this.contents = contents;
        }

        public static UiFlgResponseUpd fromResponseString(String updString) {
            return new UiFlgResponseUpd(updString);
        }

        public String toResponseString() {
            return contents;
        }
    }

    public final static class UiFlgResponseACN {
        final static String CANNED_RESPONSE = "ACN_FREE <br>\t\t";
        final String contents;

        public UiFlgResponseACN() {
            this.contents = CANNED_RESPONSE;
        }

        private UiFlgResponseACN(String contents) {
            this.contents = contents;
        }

        public static UiFlgResponseACN fromResponseString(String updString) {
            return new UiFlgResponseACN(updString);
        }

        public String toResponseString() {
            return contents;
        }
    }

    // {|1|0|1|1|0|0|N,N,N,N,N,N,N,N,N,N,N,0,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N,N|}[|0|||]ACN_FREE <br>

    // String[] commands = new String[36];
    // Arrays.fill(commands, "N");
    final static Pattern GET_UI_FLG_RESPONSE_PATTERN = Pattern.compile(
            "^[\\{](?<preamble>([|]\\d)+[|])(?<commands>[^|]+)[|][\\}](?<updsuffix>\\[[^\\]]+\\])(?<acn>.*$)",
            Pattern.CASE_INSENSITIVE);
    final static String RESPONSE_FORMAT = "{%s%s|}%s%s";

    public UiFlgResponsePreamble preamble;
    public UiFlgResponseCommmands commands;
    public UiFlgResponseUpd updSuffix;
    public UiFlgResponseACN acnSuffix;

    public RemoteGetUiFlgResponseDTO() {
        this.preamble = new UiFlgResponsePreamble();
        this.commands = new UiFlgResponseCommmands();
        this.updSuffix = new UiFlgResponseUpd();
        this.acnSuffix = new UiFlgResponseACN();
    }

    private RemoteGetUiFlgResponseDTO(UiFlgResponsePreamble preamble, UiFlgResponseCommmands commands,
            UiFlgResponseUpd updSuffix, UiFlgResponseACN acnSuffix) {
        this.preamble = preamble;
        this.commands = commands;
        this.updSuffix = updSuffix;
        this.acnSuffix = acnSuffix;
    }

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

    public String toResponseString() {
        return String.format(RESPONSE_FORMAT, this.preamble.toResponseString(), this.commands.toResponseString(),
                this.updSuffix.toResponseString(), this.acnSuffix.toResponseString());
    }
}
