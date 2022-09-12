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
package org.openhab.binding.argoclima.internal.device_api;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.elements.IArgoElement;
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
public class ArgoApiDataElement<T extends @NonNull IArgoElement> {
    public enum DataElementType {
        READ_WRITE,
        READ_ONLY,
        WRITE_ONLY
    }

    private int queryResponseIndex;
    private int statusUpdateRequestIndex;
    private DataElementType type;
    // private @Nullable String rawValue;
    private T rawValue;
    // private @Nullable T valueToSet;

    private ArgoApiDataElement(T rawValue, int queryIndex, int updateIndex, DataElementType type) {
        this.queryResponseIndex = queryIndex;
        this.statusUpdateRequestIndex = updateIndex;
        this.type = type;
        this.rawValue = rawValue;
    }

    public void abortPendingCommand() {
        this.rawValue.abortPendingCommand();
    }

    public static ArgoApiDataElement<IArgoElement> readWriteElement(IArgoElement rawValue, int queryIndex,
            int updateIndex) {
        return new ArgoApiDataElement<>(rawValue, queryIndex, updateIndex, DataElementType.READ_WRITE);
    }
    //
    // public static <T extends IArgoElement> ArgoApiDataElement<T> readWriteElement(T rawValue, int queryIndex,
    // int updateIndex) {
    // return new ArgoApiDataElement<>(rawValue, queryIndex, updateIndex, DataElementType.READ_WRITE);
    // }

    public static ArgoApiDataElement<IArgoElement> readOnlyElement(IArgoElement rawValue, int queryIndex) {
        return new ArgoApiDataElement<>(rawValue, queryIndex, -1, DataElementType.READ_ONLY);
    }

    public static ArgoApiDataElement<IArgoElement> writeOnlyElement(IArgoElement rawValue, int updateIndex) {
        return new ArgoApiDataElement<>(rawValue, -1, updateIndex, DataElementType.WRITE_ONLY);
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
        return UnDefType.NULL;
    }

    public Optional<Pair<Integer, String>> toDeviceResponse() {
        if (this.rawValue.isUpdatePending()) {
            return Optional.of(Pair.of(this.statusUpdateRequestIndex, this.rawValue.getDeviceApiValue()));
        }
        return Optional.empty();
    }

    public boolean isUpdatePending() {
        return this.rawValue.isUpdatePending();
    }

    //
    // public String getValue() {
    // return rawValue;
    // }

    public boolean isReadable() {
        return this.type == DataElementType.READ_ONLY || this.type == DataElementType.READ_WRITE;
    }

    public State getState() {
        return rawValue.toState();
    }

    @Override
    public String toString() {
        return rawValue.toString();
    }

    public boolean handleCommand(Command command) {
        if (this.type != DataElementType.READ_ONLY && this.type != DataElementType.READ_WRITE) {
            return false; // attempting to write a R/O value
        }

        return rawValue.handleCommand(command);
    }
}
