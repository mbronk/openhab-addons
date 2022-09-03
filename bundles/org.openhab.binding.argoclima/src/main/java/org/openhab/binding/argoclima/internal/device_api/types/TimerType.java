package org.openhab.binding.argoclima.internal.device_api.types;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public enum TimerType implements IArgoApiEnum {
    NO_TIMER(0),
    DELAY_TIMER(1),
    SCHEDULE_TIMER_1(2),
    SCHEDULE_TIMER_2(3),
    SCHEDULE_TIMER_3(4);

    private int value;

    TimerType(int intValue) {
        this.value = intValue;
    }

    @Override
    public int getIntValue() {
        return this.value;
    }
}
