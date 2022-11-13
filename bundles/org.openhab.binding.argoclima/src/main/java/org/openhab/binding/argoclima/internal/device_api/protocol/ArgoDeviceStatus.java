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
package org.openhab.binding.argoclima.internal.device_api.protocol;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.configuration.IScheduleConfigurationProvider;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.ActiveTimerModeParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.CurrentTimeParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.CurrentWeekdayParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.DelayMinutesParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.EnumParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.FwVersionParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.IArgoElement;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.OnOffParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.RangeParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.TemperatureParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.TimeParam;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.TimeParam.TimeParamType;
import org.openhab.binding.argoclima.internal.device_api.protocol.elements.WeekdayParam;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.device_api.types.FanLevel;
import org.openhab.binding.argoclima.internal.device_api.types.FlapLevel;
import org.openhab.binding.argoclima.internal.device_api.types.OperationMode;
import org.openhab.binding.argoclima.internal.device_api.types.TemperatureScale;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoDeviceStatus implements IArgoSettingProvider {

    private final Logger logger = LoggerFactory.getLogger(ArgoDeviceStatus.class);

    private final IScheduleConfigurationProvider scheduleSettingsProvider;

    public ArgoDeviceStatus(IScheduleConfigurationProvider scheduleSettingsProvider) {
        this.scheduleSettingsProvider = scheduleSettingsProvider;
    }

    //
    // private class TimerApiElements {
    // public final EnumParam<TimerType> timerType = new EnumParam<>(TimerType.class);
    // public final DelayMinutesParam delayTimerValue = new DelayMinutesParam(TimeParam.fromHhMm(0, 10),
    // TimeParam.fromHhMm(19, 50), 10, Optional.of(60));
    //
    // public final WeekdayParam scheduleTimerEnabledDays = new WeekdayParam();
    // public final TimeParam scheduleTimerOnTime = new TimeParam();
    // public final TimeParam scheduleTimerOffTime = new TimeParam();
    // }
    //
    // private final TimerApiElements timerApiElements = new TimerApiElements();

    private final List<ArgoApiDataElement<IArgoElement>> allElements = List.of(
            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.TARGET_TEMPERATURE,
                    new TemperatureParam(this, 19.0, 36.0, 0.5), 0, 0),
            ArgoApiDataElement.readOnlyElement(ArgoDeviceSettingType.ACTUAL_TEMPERATURE,
                    new TemperatureParam(this, 19.0, 36.0, 0.1), 1),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.POWER, new OnOffParam(this), 2, 2),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.MODE, new EnumParam<>(this, OperationMode.class),
                    3, 3),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.FAN_LEVEL, new EnumParam<>(this, FanLevel.class),
                    4, 4),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.FLAP_LEVEL,
                    new EnumParam<>(this, FlapLevel.class), 5, 5),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.I_FEEL_TEMPERATURE, new OnOffParam(this), 6, 6),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.FILTER_MODE, new OnOffParam(this), 7, 7),
            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.ECO_MODE, new OnOffParam(this), 8, 8),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.TURBO_MODE, new OnOffParam(this), 9, 9),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.NIGHT_MODE, new OnOffParam(this), 10, 10),
            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.LIGHT, new OnOffParam(this), 11, 11),
            // Map.entry(ArgoDeviceSettingType.ACTIVE_TIMER, ArgoApiDataElement.readWriteElement(// timerApiElements,
            // new EnumParam<>(this, TimerType.class), 12, 12)),
            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.ACTIVE_TIMER, new ActiveTimerModeParam(this), 12,
                    12),

            // ?????
            // Map.entry(ArgoDeviceSettingType.CURRENT_DAY_OF_WEEK,
            // ArgoApiDataElement.readWriteElement(new WeekdayParam(), 18, 18)), // TODO: js interface had
            // // write-only!

            ArgoApiDataElement.writeOnlyElement(ArgoDeviceSettingType.CURRENT_DAY_OF_WEEK,
                    new CurrentWeekdayParam(this), 18), // TODO: js interface had
            // write-only!

            ArgoApiDataElement.writeOnlyElement(ArgoDeviceSettingType.TIMER_N_ENABLED_DAYS, new WeekdayParam(this), 19), // TODO:
                                                                                                                         // js
                                                                                                                         // interface
                                                                                                                         // had
            // write-only!
            // Map.entry(ArgoDeviceSettingType.CURRENT_TIME,
            // ArgoApiDataElement.readWriteElement(new CurrentTimeParam(), 20, 20)), // TODO:

            ArgoApiDataElement.writeOnlyElement(ArgoDeviceSettingType.CURRENT_TIME, new CurrentTimeParam(this), 20), // TODO:
            // js
            // interface
            // had
            // write-only!
            // TODO: should be a different type (interval!)
            ArgoApiDataElement.writeOnlyElement(ArgoDeviceSettingType.TIMER_0_DELAY_TIME,
                    new DelayMinutesParam(this, TimeParam.fromHhMm(0, 10), TimeParam.fromHhMm(19, 50), 10,
                            Optional.of(60)),
                    21), // TODO:
                         // js
                         // interface
                         // had
                         // write-only!

            ArgoApiDataElement.writeOnlyElement(ArgoDeviceSettingType.TIMER_N_ON_TIME,
                    new TimeParam(this, TimeParamType.ON), 22), // TODO:
            // js
            // interface
            // had
            // write-only!

            ArgoApiDataElement.writeOnlyElement(ArgoDeviceSettingType.TIMER_N_OFF_TIME,
                    new TimeParam(this, TimeParamType.OFF), 23), // TODO:
            // js
            // interface
            // had
            // write-only!

            ArgoApiDataElement.writeOnlyElement(ArgoDeviceSettingType.RESET_TO_FACTORY_SETTINGS, new OnOffParam(this),
                    24),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.ECO_POWER_LIMIT, new RangeParam(this, 30, 99), 22,
                    25),

            ArgoApiDataElement.readWriteElement(ArgoDeviceSettingType.DISPLAY_TEMPERATURE_SCALE,
                    new EnumParam<>(this, TemperatureScale.class), 24, 26),

            ArgoApiDataElement.readOnlyElement(ArgoDeviceSettingType.UNIT_FIRMWARE_VERSION, new FwVersionParam(this),
                    23)

    // todo
    );

    private final Map<ArgoDeviceSettingType, ArgoApiDataElement<IArgoElement>> dataElements = allElements.stream()
            .collect(Collectors.toMap(k -> k.settingType, Function.identity()));

    //
    // private final Map<ArgoDeviceSettingType, ArgoApiDataElement<IArgoElement>> dataElements = Map.ofEntries(
    // Map.entry(ArgoDeviceSettingType.TARGET_TEMPERATURE,
    // ArgoApiDataElement.readWriteElement(new TemperatureParam(this, 19.0, 36.0, 0.5), 0, 0)),
    // Map.entry(ArgoDeviceSettingType.ACTUAL_TEMPERATURE,
    // ArgoApiDataElement.readOnlyElement(new TemperatureParam(this, 19.0, 36.0, 0.1), 1)),
    // // ArgoApiDataElement.readOnlyElement(new TemperatureParam(), 1)),
    //
    // Map.entry(ArgoDeviceSettingType.POWER, ArgoApiDataElement.readWriteElement(new OnOffParam(this), 2, 2)),
    //
    // Map.entry(ArgoDeviceSettingType.MODE,
    // ArgoApiDataElement.readWriteElement(new EnumParam<>(this, OperationMode.class), 3, 3)),
    //
    // Map.entry(ArgoDeviceSettingType.FAN_LEVEL,
    // ArgoApiDataElement.readWriteElement(new EnumParam<>(this, FanLevel.class), 4, 4)),
    //
    // Map.entry(ArgoDeviceSettingType.FLAP_LEVEL,
    // ArgoApiDataElement.readWriteElement(new EnumParam<>(this, FlapLevel.class), 5, 5)),
    //
    // Map.entry(ArgoDeviceSettingType.I_FEEL_TEMPERATURE,
    // ArgoApiDataElement.readWriteElement(new OnOffParam(this), 6, 6)),
    // Map.entry(ArgoDeviceSettingType.FILTER_MODE,
    // ArgoApiDataElement.readWriteElement(new OnOffParam(this), 7, 7)),
    // Map.entry(ArgoDeviceSettingType.ECO_MODE, ArgoApiDataElement.readWriteElement(new OnOffParam(this), 8, 8)),
    // Map.entry(ArgoDeviceSettingType.TURBO_MODE,
    // ArgoApiDataElement.readWriteElement(new OnOffParam(this), 9, 9)),
    // Map.entry(ArgoDeviceSettingType.NIGHT_MODE,
    // ArgoApiDataElement.readWriteElement(new OnOffParam(this), 10, 10)),
    // Map.entry(ArgoDeviceSettingType.LIGHT, ArgoApiDataElement.readWriteElement(new OnOffParam(this), 11, 11)),
    // // Map.entry(ArgoDeviceSettingType.ACTIVE_TIMER, ArgoApiDataElement.readWriteElement(// timerApiElements,
    // // new EnumParam<>(this, TimerType.class), 12, 12)),
    // Map.entry(ArgoDeviceSettingType.ACTIVE_TIMER, ArgoApiDataElement.readWriteElement(// timerApiElements,
    // new ActiveTimerModeParam(this), 12, 12)),
    //
    // // ?????
    // // Map.entry(ArgoDeviceSettingType.CURRENT_DAY_OF_WEEK,
    // // ArgoApiDataElement.readWriteElement(new WeekdayParam(), 18, 18)), // TODO: js interface had
    // // // write-only!
    // Map.entry(ArgoDeviceSettingType.CURRENT_DAY_OF_WEEK,
    // ArgoApiDataElement.writeOnlyElement(new CurrentWeekdayParam(this), 18)), // TODO: js interface had
    // // write-only!
    //
    // Map.entry(ArgoDeviceSettingType.TIMER_N_ENABLED_DAYS,
    // ArgoApiDataElement.readWriteElement(new WeekdayParam(this), 19, 19)), // TODO: js interface had
    // // write-only!
    // // Map.entry(ArgoDeviceSettingType.CURRENT_TIME,
    // // ArgoApiDataElement.readWriteElement(new CurrentTimeParam(), 20, 20)), // TODO:
    // Map.entry(ArgoDeviceSettingType.CURRENT_TIME,
    // ArgoApiDataElement.writeOnlyElement(new CurrentTimeParam(this), 20)), // TODO:
    // // js
    // // interface
    // // had
    // // write-only!
    // Map.entry(ArgoDeviceSettingType.TIMER_0_DELAY_TIME, // TODO: should be a different type (interval!)
    // ArgoApiDataElement.writeOnlyElement(new DelayMinutesParam(this, TimeParam.fromHhMm(0, 10),
    // TimeParam.fromHhMm(19, 50), 10, Optional.of(60)), 21)), // TODO:
    // // js
    // // interface
    // // had
    // // write-only!
    //
    // Map.entry(ArgoDeviceSettingType.TIMER_N_ON_TIME,
    // ArgoApiDataElement.writeOnlyElement(new TimeParam(this), 22)), // TODO:
    // // js
    // // interface
    // // had
    // // write-only!
    // Map.entry(ArgoDeviceSettingType.TIMER_N_OFF_TIME,
    // ArgoApiDataElement.writeOnlyElement(new TimeParam(this), 23)), // TODO:
    // // js
    // // interface
    // // had
    // // write-only!
    // Map.entry(ArgoDeviceSettingType.RESET_TO_FACTORY_SETTINGS,
    // ArgoApiDataElement.writeOnlyElement(new OnOffParam(this), 24)),
    // Map.entry(ArgoDeviceSettingType.ECO_POWER_LIMIT,
    // ArgoApiDataElement.readWriteElement(new RangeParam(this, 30, 99), 22, 25)),
    // Map.entry(ArgoDeviceSettingType.DISPLAY_TEMPERATURE_SCALE,
    // ArgoApiDataElement.readWriteElement(new EnumParam<>(this, TemperatureScale.class), 24, 26)),
    // Map.entry(ArgoDeviceSettingType.UNIT_FIRMWARE_VERSION,
    // ArgoApiDataElement.readOnlyElement(new FwVersionParam(this), 23))
    //
    // // todo
    // );

    @Override
    public ArgoApiDataElement<IArgoElement> getSetting(ArgoDeviceSettingType type) {
        if (dataElements.containsKey(type)) {
            return Objects.requireNonNull(dataElements.get(type));
        }
        throw new RuntimeException("Wrong setting type: " + type.toString());
    }

    @Override
    public String toString() {
        return dataElements.entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .map(x -> String.format("%s=%s", x.getKey(), x.getValue().toString(false)))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    public Map<ArgoDeviceSettingType, State> getCurrentStateMap() {
        return dataElements.entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .filter(x -> x.getValue().isReadable())
                .collect(Collectors.toMap(Map.Entry::getKey, y -> y.getValue().getState()));
    }

    public void fromDeviceString(String deviceOutput) {
        String[] values = deviceOutput.split(",");
        if (values.length != 39) {
            throw new RuntimeException("Invalid device API response: " + deviceOutput);
        }
        synchronized (this) {
            dataElements.entrySet().stream().forEach(v -> v.getValue().fromDeviceResponse(values));
        }
        logger.info(this.toString());
    }

    public String getDeviceCommandStatus() {
        String[] commands = new String[36];
        Arrays.fill(commands, "N");

        dataElements.entrySet().stream().filter(x -> x.getValue().shouldBeSentToDevice())
                .map(x -> x.getValue().toDeviceResponse()).forEach(p -> {
                    if (p.orElseThrow().getLeft() < 0 || p.orElseThrow().getLeft() > commands.length) {
                        throw new RuntimeException(String.format(
                                "Attempting to set device command %d := %s, while only commands 0..%d are supported",
                                p.orElseThrow().getLeft(), p.orElseThrow().getRight(), commands.length));
                    }
                    commands[p.orElseThrow().getLeft()] = p.orElseThrow().getRight();
                });

        // TODO: add current time setting (can override internally etc, maybe?

        return String.join(",", commands);
    }

    public boolean hasUpdatesPending() {
        return this.dataElements.values().stream().anyMatch(x -> x.isUpdatePending());
    }

    public List<ArgoApiDataElement<IArgoElement>> getItemsWithPendingUpdates() {
        // logger.info("Items with update pending: {}",
        // this.dataElements.values().stream().filter(x -> x.isUpdatePending()).toArray());

        return this.dataElements.values().stream().filter(x -> x.isUpdatePending())
                .sorted((x, y) -> Integer.compare(x.statusUpdateRequestIndex, y.statusUpdateRequestIndex))
                .collect(Collectors.toList());
    }

    @Override
    public IScheduleConfigurationProvider getScheduleProvider() {
        // TODO Auto-generated method stub
        return this.scheduleSettingsProvider;
    }
}

// 210,220,0,1,0,7,1,0,0,0,0,1,0,0,101,1,101,1,1,0,0,N,75,1416,0,N,N,N,N,N,N,N,N,N,N,N,N,N,N
