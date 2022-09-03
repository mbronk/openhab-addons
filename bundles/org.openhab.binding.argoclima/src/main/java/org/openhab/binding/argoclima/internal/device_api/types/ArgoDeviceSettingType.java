package org.openhab.binding.argoclima.internal.device_api.types;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public enum ArgoDeviceSettingType {
    TARGET_TEMPERATURE,
    ACTUAL_TEMPERATURE,
    POWER,
    MODE,
    FAN_LEVEL,
    FLAP_LEVEL,
    I_FEEL_TEMPERATURE,
    FILTER_MODE,
    ECO_MODE,
    TURBO_MODE,
    NIGHT_MODE,
    LIGHT,
    TIMER_TYPE,
    ECO_POWER_LIMIT,
    RESET_TO_FACTORY_SETTINGS,
    UNIT_FIRMWARE_VERSION,
    DISPLAY_TEMPERATURE_SCALE,
    CURRENT_TIME,
    CURRENT_DAY_OF_WEEK,
    ACTIVE_TIMER,
    TIMER_0_DELAY_TIME,
    TIMER_N_ENABLED_DAYS,
    TIMER_N_ON_TIME,
    TIMER_N_OFF_TIME
}
