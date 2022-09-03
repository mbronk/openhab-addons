package org.openhab.binding.argoclima.internal.device_api.types;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public enum FanLevel implements IArgoApiEnum {
    AUTO(0),
    LEVEL_1(1),
    LEVEL_2(2),
    LEVEL_3(3),
    LEVEL_4(4),
    LEVEL_5(5),
    LEVEL_6(6);

    private int value;

    FanLevel(int intValue) {
        this.value = intValue;
    }

    @Override
    public int getIntValue() {
        return this.value;
    }
}
