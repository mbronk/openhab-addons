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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.ArgoClimaHandler;
import org.openhab.binding.argoclima.internal.device_api.elements.EnumParam;
import org.openhab.binding.argoclima.internal.device_api.elements.FwVersionParam;
import org.openhab.binding.argoclima.internal.device_api.elements.IArgoElement;
import org.openhab.binding.argoclima.internal.device_api.elements.OnOffParam;
import org.openhab.binding.argoclima.internal.device_api.elements.RangeParam;
import org.openhab.binding.argoclima.internal.device_api.elements.TemperatureParam;
import org.openhab.binding.argoclima.internal.device_api.elements.TimeParam;
import org.openhab.binding.argoclima.internal.device_api.elements.WeekdayParam;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.device_api.types.FanLevel;
import org.openhab.binding.argoclima.internal.device_api.types.FlapLevel;
import org.openhab.binding.argoclima.internal.device_api.types.OperationMode;
import org.openhab.binding.argoclima.internal.device_api.types.TemperatureScale;
import org.openhab.binding.argoclima.internal.device_api.types.TimerType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoDeviceStatus {

    private final Logger logger = LoggerFactory.getLogger(ArgoClimaHandler.class);

    private Map<ArgoDeviceSettingType, ArgoApiDataElement<IArgoElement>> dataElements = Map.ofEntries(
            Map.entry(ArgoDeviceSettingType.TARGET_TEMPERATURE,
                    ArgoApiDataElement.readWriteElement(new TemperatureParam(19.0, 36.0, 0.5), 0, 0)),
            Map.entry(ArgoDeviceSettingType.ACTUAL_TEMPERATURE,
                    ArgoApiDataElement.readOnlyElement(new TemperatureParam(19.0, 36.0, 0.5), 1)),
            // ArgoApiDataElement.readOnlyElement(new TemperatureParam(), 1)),

            Map.entry(ArgoDeviceSettingType.POWER, ArgoApiDataElement.readWriteElement(new OnOffParam(), 2, 2)),

            Map.entry(ArgoDeviceSettingType.MODE,
                    ArgoApiDataElement.readWriteElement(new EnumParam<>(OperationMode.class), 3, 3)),

            Map.entry(ArgoDeviceSettingType.FAN_LEVEL,
                    ArgoApiDataElement.readWriteElement(new EnumParam<>(FanLevel.class), 4, 4)),

            Map.entry(ArgoDeviceSettingType.FLAP_LEVEL,
                    ArgoApiDataElement.readWriteElement(new EnumParam<>(FlapLevel.class), 5, 5)),

            Map.entry(ArgoDeviceSettingType.I_FEEL_TEMPERATURE,
                    ArgoApiDataElement.readWriteElement(new OnOffParam(), 6, 6)),
            Map.entry(ArgoDeviceSettingType.FILTER_MODE, ArgoApiDataElement.readWriteElement(new OnOffParam(), 7, 7)),
            Map.entry(ArgoDeviceSettingType.ECO_MODE, ArgoApiDataElement.readWriteElement(new OnOffParam(), 8, 8)),
            Map.entry(ArgoDeviceSettingType.TURBO_MODE, ArgoApiDataElement.readWriteElement(new OnOffParam(), 9, 9)),
            Map.entry(ArgoDeviceSettingType.NIGHT_MODE, ArgoApiDataElement.readWriteElement(new OnOffParam(), 10, 10)),
            Map.entry(ArgoDeviceSettingType.LIGHT, ArgoApiDataElement.readWriteElement(new OnOffParam(), 11, 11)),
            Map.entry(ArgoDeviceSettingType.ACTIVE_TIMER,
                    ArgoApiDataElement.readWriteElement(new EnumParam<>(TimerType.class), 12, 12)),

            // ?????
            Map.entry(ArgoDeviceSettingType.CURRENT_DAY_OF_WEEK,
                    ArgoApiDataElement.readWriteElement(new WeekdayParam(), 18, 18)), // TODO: js interface had
                                                                                      // write-only!
            Map.entry(ArgoDeviceSettingType.TIMER_N_ENABLED_DAYS,
                    ArgoApiDataElement.readWriteElement(new WeekdayParam(), 19, 19)), // TODO: js interface had
                                                                                      // write-only!
            Map.entry(ArgoDeviceSettingType.CURRENT_TIME, ArgoApiDataElement.readWriteElement(new TimeParam(), 20, 20)), // TODO:
                                                                                                                         // js
                                                                                                                         // interface
                                                                                                                         // had
                                                                                                                         // write-only!
            Map.entry(ArgoDeviceSettingType.TIMER_0_DELAY_TIME, // TODO: should be a different type (interval!)
                    ArgoApiDataElement
                            .writeOnlyElement(new TimeParam(TimeParam.fromHhMm(0, 0), TimeParam.fromHhMm(23, 30)), 21)), // TODO:
                                                                                                                         // js
                                                                                                                         // interface
                                                                                                                         // had
                                                                                                                         // write-only!
            Map.entry(ArgoDeviceSettingType.TIMER_N_ON_TIME, ArgoApiDataElement.writeOnlyElement(new TimeParam(), 22)), // TODO:
                                                                                                                        // js
                                                                                                                        // interface
                                                                                                                        // had
                                                                                                                        // write-only!
            Map.entry(ArgoDeviceSettingType.TIMER_N_OFF_TIME, ArgoApiDataElement.writeOnlyElement(new TimeParam(), 23)), // TODO:
                                                                                                                         // js
                                                                                                                         // interface
                                                                                                                         // had
                                                                                                                         // write-only!
            Map.entry(ArgoDeviceSettingType.RESET_TO_FACTORY_SETTINGS,
                    ArgoApiDataElement.writeOnlyElement(new OnOffParam(), 24)),
            Map.entry(ArgoDeviceSettingType.ECO_POWER_LIMIT,
                    ArgoApiDataElement.readWriteElement(new RangeParam(30, 99), 22, 25)),
            Map.entry(ArgoDeviceSettingType.DISPLAY_TEMPERATURE_SCALE,
                    ArgoApiDataElement.readWriteElement(new EnumParam<>(TemperatureScale.class), 24, 26)),
            Map.entry(ArgoDeviceSettingType.UNIT_FIRMWARE_VERSION,
                    ArgoApiDataElement.readOnlyElement(new FwVersionParam(), 23))

    // todo
    );

    public ArgoApiDataElement<IArgoElement> getSetting(ArgoDeviceSettingType type) {
        if (dataElements.containsKey(type)) {
            return dataElements.get(type);
        }
        throw new RuntimeException("Wrong setting type");
    }

    @Override
    public String toString() {
        return dataElements.entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .map(x -> String.format("%s=%s", x.getKey(), x.getValue())).collect(Collectors.joining(", ", "{", "}"));

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

        dataElements.entrySet().stream().filter(x -> x.getValue().isUpdatePending())
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
        logger.info("Items with update pending: {}",
                this.dataElements.values().stream().filter(x -> x.isUpdatePending()).toArray());

        return this.dataElements.values().stream().filter(x -> x.isUpdatePending()).collect(Collectors.toList());
    }
}

// 210,220,0,1,0,7,1,0,0,0,0,1,0,0,101,1,101,1,1,0,0,N,75,1416,0,N,N,N,N,N,N,N,N,N,N,N,N,N,N