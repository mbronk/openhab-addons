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
package org.openhab.binding.argoclima.internal.device_api.protocol;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.IArgoElement;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 *
 * @param <T>
 */
@NonNullByDefault
public class ArgoApiDataElement<T extends IArgoElement> {
    public enum DataElementType {
        READ_WRITE,
        READ_ONLY,
        WRITE_ONLY
    }

    public final int queryResponseIndex;
    public final int statusUpdateRequestIndex;
    private DataElementType type;
    // private @Nullable String rawValue;
    private T rawValue;
    public final ArgoDeviceSettingType settingType;
    // private @Nullable T valueToSet;

    private ArgoApiDataElement(ArgoDeviceSettingType settingType, T rawValue, int queryIndex, int updateIndex,
            DataElementType type) {
        this.settingType = settingType;
        this.queryResponseIndex = queryIndex;
        this.statusUpdateRequestIndex = updateIndex;
        this.type = type;
        this.rawValue = rawValue;
    }

    public void abortPendingCommand() {
        this.rawValue.abortPendingCommand();
    }

    public static ArgoApiDataElement<IArgoElement> readWriteElement(ArgoDeviceSettingType settingType,
            IArgoElement rawValue, int queryIndex, int updateIndex) {
        return new ArgoApiDataElement<>(settingType, rawValue, queryIndex, updateIndex, DataElementType.READ_WRITE);
    }
    //
    // public static <T extends IArgoElement> ArgoApiDataElement<T> readWriteElement(T rawValue, int queryIndex,
    // int updateIndex) {
    // return new ArgoApiDataElement<>(rawValue, queryIndex, updateIndex, DataElementType.READ_WRITE);
    // }

    public static ArgoApiDataElement<IArgoElement> readOnlyElement(ArgoDeviceSettingType settingType,
            IArgoElement rawValue, int queryIndex) {
        return new ArgoApiDataElement<>(settingType, rawValue, queryIndex, -1, DataElementType.READ_ONLY);
    }

    public static ArgoApiDataElement<IArgoElement> writeOnlyElement(ArgoDeviceSettingType settingType,
            IArgoElement rawValue, int updateIndex) {
        return new ArgoApiDataElement<>(settingType, rawValue, -1, updateIndex, DataElementType.WRITE_ONLY);
    }

    // public static <T extends IArgoElement> ArgoApiDataElement<T> readOnlyElement(T rawValue, int queryIndex) {
    // return new ArgoApiDataElement<>(rawValue, queryIndex, -1, DataElementType.READ_ONLY);
    // }
    //
    // public static <T extends IArgoElement> ArgoApiDataElement<T> writeOnlyElement(T rawValue, int updateIndex) {
    // return new ArgoApiDataElement<>(rawValue, -1, updateIndex, DataElementType.WRITE_ONLY);
    // }

    public State fromDeviceResponse(String[] responseElements) {
        if (this.type == DataElementType.READ_WRITE || this.type == DataElementType.READ_ONLY) {
            // this.rawValue = responseElements[queryResponseIndex];
            // State newState =
            return this.rawValue.updateFromApiResponse(responseElements[queryResponseIndex]);
            // TODO: err handling
        }
        /*
         * else if ((this.type == DataElementType.WRITE_ONLY && this.rawValue.toState() != UnDefType.UNDEF)) {
         * return this.rawValue.updateFromApiResponse(""); // TODO - this is baaad
         * }
         */
        return UnDefType.NULL;
    }

    public Optional<Pair<Integer, String>> toDeviceResponse() {
        if (this.rawValue.isUpdatePending() || this.rawValue.isAlwaysSent()) {
            return Optional.of(Pair.of(this.statusUpdateRequestIndex, this.rawValue.getDeviceApiValue()));
        }
        return Optional.empty();
    }

    public boolean isUpdatePending() {
        return this.rawValue.isUpdatePending();
    }

    public boolean shouldBeSentToDevice() {
        return this.rawValue.isUpdatePending() || this.rawValue.isAlwaysSent();
    }

    public void notifyCommandSent() {
        this.rawValue.notifyCommandSent();
    }

    //
    // public String getValue() {
    // return rawValue;
    // }

    public boolean isReadable() {
        return this.type == DataElementType.READ_ONLY || this.type == DataElementType.READ_WRITE
                || (this.type == DataElementType.WRITE_ONLY && this.rawValue.toState() != UnDefType.UNDEF);
    }

    public State getState() {
        return rawValue.toState();
    }

    @Override
    public String toString() {
        return toString(true);
    }

    public String toString(boolean includeType) {
        var prefix = "";
        if (includeType) {
            prefix = this.settingType.toString() + "=";
        }
        return prefix + rawValue.toString();
    }

    public boolean handleCommand(Command command) {
        if (this.type != DataElementType.WRITE_ONLY && this.type != DataElementType.READ_WRITE) {
            return false; // attempting to write a R/O value
        }
        boolean waitForConfirmation = this.type != DataElementType.WRITE_ONLY;

        return rawValue.handleCommand(command, waitForConfirmation);
    }
}
