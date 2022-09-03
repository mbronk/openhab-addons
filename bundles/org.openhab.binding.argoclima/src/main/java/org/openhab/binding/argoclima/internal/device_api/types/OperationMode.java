package org.openhab.binding.argoclima.internal.device_api.types;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public enum OperationMode implements IArgoApiEnum {
    COOL(1),
    DRY(2),
    WARM(3),
    FAN(4),
    AUTO(5);

    private int value;

    OperationMode(int intValue) {
        this.value = intValue;
    }

    @Override
    public int getIntValue() {
        return this.value;
    }

    // @SuppressWarnings("null") // TODO
    // public static Optional<OperationMode> fromInt(int value) {
    // return Arrays.stream(OperationMode.values()).filter(p -> p.ordinal() == value).findFirst();
    // }
}
