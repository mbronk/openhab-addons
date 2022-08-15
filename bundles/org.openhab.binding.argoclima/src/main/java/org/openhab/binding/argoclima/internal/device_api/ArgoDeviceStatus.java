package org.openhab.binding.argoclima.internal.device_api;

import java.util.Map;
import java.util.stream.Collectors;

import org.openhab.binding.argoclima.internal.ArgoClimaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArgoDeviceStatus {

    private final Logger logger = LoggerFactory.getLogger(ArgoClimaHandler.class);
    // 210, //targetTemp
    // 213, //setTemp
    // 0, //operating
    // 1, //mode
    // 0, //fan
    // 7, //remote_temperature
    // 1, //eco
    // 0, //turbo
    // 0, //night
    // 0, //light
    // 0, //timer
    // 1, //current_weekday
    // 0, //timer_weekdays
    // 0, //time
    // 101, //delaytimer duration
    // 1, //timer_on
    // 101, //timer_off
    // 1, //eco_limit
    // 1, //unit
    // 0, //fw_version
    // 0,N,75,1416,0,N,N,N,N,N,N,N,N,N,N,N,N,N,N
    //

    private Map<String, ArgoApiDataElement> dataElements = Map.ofEntries(
            Map.entry("target_temp", ArgoApiDataElement.readWriteElement(0, 0)),
            Map.entry("actual_temp", ArgoApiDataElement.readOnlyElement(1)),
            Map.entry("on_off", ArgoApiDataElement.readWriteElement(2, 2)),
            Map.entry("mode", ArgoApiDataElement.readWriteElement(3, 3)),
            Map.entry("fan", ArgoApiDataElement.readWriteElement(4, 4)),
            Map.entry("swing", ArgoApiDataElement.readWriteElement(5, 5)),
            Map.entry("remote_temperature", ArgoApiDataElement.readWriteElement(6, 6)),
            // ?
            Map.entry("filter_mode", ArgoApiDataElement.readWriteElement(7, 7)),
            Map.entry("eco_mode", ArgoApiDataElement.readWriteElement(8, 8)),
            Map.entry("turbo_mode", ArgoApiDataElement.readWriteElement(9, 9)),
            Map.entry("night_mode", ArgoApiDataElement.readWriteElement(10, 10)),
            Map.entry("light_mode", ArgoApiDataElement.readWriteElement(11, 11)),
            Map.entry("timer_type", ArgoApiDataElement.readWriteElement(12, 12)),
            // ??
            Map.entry("current_weekday", ArgoApiDataElement.writeOnlyElement(18)),
            Map.entry("timer_weekdays", ArgoApiDataElement.writeOnlyElement(19)),
            Map.entry("time", ArgoApiDataElement.writeOnlyElement(20)),
            Map.entry("delay_timer_duration", ArgoApiDataElement.writeOnlyElement(21)),
            Map.entry("timer_on", ArgoApiDataElement.writeOnlyElement(22)),
            Map.entry("timer_off", ArgoApiDataElement.writeOnlyElement(23)),
            Map.entry("reset", ArgoApiDataElement.writeOnlyElement(24)),
            Map.entry("eco_limit", ArgoApiDataElement.readWriteElement(22, 25)),
            Map.entry("unit_of_measure", ArgoApiDataElement.readWriteElement(24, 26)),
            Map.entry("firmware_version", ArgoApiDataElement.readOnlyElement(23)));

    private String[] values;

    @Override
    public String toString() {
        // return String.join(",", values);

        return dataElements.entrySet().stream().map(x -> String.format("%s=%s", x.getKey(), x.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));

    }

    public void fromDeviceString(String deviceOutput) {
        values = deviceOutput.split(",");
        if (values.length != 39) {
            throw new RuntimeException("Invalid device API response: " + deviceOutput);
        }
        dataElements.entrySet().stream().forEach(v -> v.getValue().fromDeviceResponse(values));

        // logger.info(String.join(",", values));
        logger.info(this.toString());
    }
}

// 210,220,0,1,0,7,1,0,0,0,0,1,0,0,101,1,101,1,1,0,0,N,75,1416,0,N,N,N,N,N,N,N,N,N,N,N,N,N,N