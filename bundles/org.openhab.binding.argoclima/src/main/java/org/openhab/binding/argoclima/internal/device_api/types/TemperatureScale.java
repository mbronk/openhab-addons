package org.openhab.binding.argoclima.internal.device_api.types;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public enum TemperatureScale implements IArgoApiEnum {
    SCALE_CELSCIUS(0),
    SCALE_FARHENHEIT(1);

    private int value;

    TemperatureScale(int intValue) {
        this.value = intValue;
    }

    @Override
    public int getIntValue() {
        return this.value;
    }
}
