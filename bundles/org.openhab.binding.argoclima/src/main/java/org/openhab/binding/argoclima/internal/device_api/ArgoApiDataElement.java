package org.openhab.binding.argoclima.internal.device_api;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

@NonNullByDefault
public class ArgoApiDataElement<T extends org.openhab.binding.argoclima.internal.device_api.elements.IArgoElement> {
    public enum DataElementType {
        READ_WRITE,
        READ_ONLY,
        WRITE_ONLY
    }

    private int queryResponseIndex;
    private int statusUpdateRequestIndex;
    private DataElementType type;
    // private @Nullable String rawValue;
    private T lastReadValue;
    // private @Nullable T valueToSet;

    private ArgoApiDataElement(T rawValue, int queryIndex, int updateIndex, DataElementType type) {
        this.queryResponseIndex = queryIndex;
        this.statusUpdateRequestIndex = updateIndex;
        this.type = type;
        this.lastReadValue = rawValue;
    }

    public static <T extends org.openhab.binding.argoclima.internal.device_api.elements.IArgoElement> ArgoApiDataElement<T> readWriteElement(T rawValue, int queryIndex,
            int updateIndex) {
        return new ArgoApiDataElement<>(rawValue, queryIndex, updateIndex, DataElementType.READ_WRITE);
    }

    public static <T extends org.openhab.binding.argoclima.internal.device_api.elements.IArgoElement> ArgoApiDataElement<T> readOnlyElement(T rawValue, int queryIndex) {
        return new ArgoApiDataElement<>(rawValue, queryIndex, -1, DataElementType.READ_ONLY);
    }

    public static <T extends org.openhab.binding.argoclima.internal.device_api.elements.IArgoElement> ArgoApiDataElement<T> writeOnlyElement(T rawValue,
            int updateIndex) {
        return new ArgoApiDataElement<>(rawValue, -1, updateIndex, DataElementType.WRITE_ONLY);
    }

    public State fromDeviceResponse(String[] responseElements) {
        if (this.type == DataElementType.READ_WRITE || this.type == DataElementType.READ_ONLY) {
            // this.rawValue = responseElements[queryResponseIndex];
            // State newState =
            return this.lastReadValue.updateFromApiResponse(responseElements[queryResponseIndex]);
            // TODO: err handling
        }
        return UnDefType.NULL;
    }

    public @Nullable Pair<Integer, String> toDeviceResponse() {
        if (this.lastReadValue.isUpdatePending()) {
            return Pair.of(this.statusUpdateRequestIndex, this.lastReadValue.getDeviceApiValue());
        }
        return null;
    }

    public boolean isUpdatePending() {
        return this.lastReadValue.isUpdatePending();
    }

    //
    // public String getValue() {
    // return rawValue;
    // }

    public boolean isReadable() {
        return this.type == DataElementType.READ_ONLY || this.type == DataElementType.READ_WRITE;
    }

    public State getState() {
        return lastReadValue.toState();
    }

    @Override
    public String toString() {
        return lastReadValue.toString();
    }

    public boolean handleCommand(Command command) {
        if (this.type != DataElementType.READ_ONLY && this.type != DataElementType.READ_WRITE) {
            return false; // attempting to write a R/O value
        }

        return lastReadValue.handleCommand(command);
    }
}
