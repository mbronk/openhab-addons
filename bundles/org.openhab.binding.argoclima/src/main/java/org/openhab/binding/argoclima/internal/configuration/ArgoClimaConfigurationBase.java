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
package org.openhab.binding.argoclima.internal.configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.ArgoClimaConfigProvider;
import org.openhab.binding.argoclima.internal.device_api.types.IArgoApiEnum;
import org.openhab.binding.argoclima.internal.exception.ArgoConfigurationException;

/**
 * The {@link ArgoClimaConfigurationBase} class contains fields mapping thing configuration parameters.
 * Contains common configuration parameters (same for all supported device types).
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public abstract class ArgoClimaConfigurationBase implements IScheduleConfigurationProvider {
    /////////////////////
    // Types
    /////////////////////
    public static enum Weekday implements IArgoApiEnum {
        SUN(0x00),
        MON(0x01),
        TUE(0x02),
        WED(0x04),
        THU(0x08),
        FRI(0x10),
        SAT(0x20);

        private int value;

        Weekday(int intValue) {
            this.value = intValue;
        }

        @Override
        public int getIntValue() {
            return this.value;
        }

        public static Weekday ofDay(DayOfWeek d) {
            switch (d) {
                case SUNDAY:
                    return Weekday.SUN;
                case MONDAY:
                    return Weekday.MON;
                case TUESDAY:
                    return Weekday.TUE;
                case WEDNESDAY:
                    return Weekday.WED;
                case THURSDAY:
                    return Weekday.THU;
                case FRIDAY:
                    return Weekday.FRI;
                case SATURDAY:
                    return Weekday.SAT;
                default:
                    throw new IllegalArgumentException("Invalid day of week");
            }
        }
    }

    @FunctionalInterface
    public interface ConfigValueSupplier<T> {
        public T get() throws ArgoConfigurationException;
    }

    /////////////////////
    // Configuration parameters (defined in thing-types.xml or ArgoClimaConfigProvider)
    /////////////////////
    public String deviceCpuId = "";
    public int refreshInterval = -1;
    public int oemServerPort = -1;
    private String oemServerAddress = "";

    private Set<Weekday> schedule1DayOfWeek = ArgoClimaConfigProvider.DEFAULT_SCHEDULE_WEEKDAYS; // EnumSet.noneOf(Weekday.class);
    private String schedule1OnTime = ArgoClimaConfigProvider.DEFAULT_SCHEDULE_START_TIME;
    private String schedule1OffTime = ArgoClimaConfigProvider.DEFAULT_SCHEDULE_END_TIME;
    private Set<Weekday> schedule2DayOfWeek = ArgoClimaConfigProvider.DEFAULT_SCHEDULE_WEEKDAYS;
    private String schedule2OnTime = ArgoClimaConfigProvider.DEFAULT_SCHEDULE_START_TIME;
    private String schedule2OffTime = ArgoClimaConfigProvider.DEFAULT_SCHEDULE_END_TIME;
    private Set<Weekday> schedule3DayOfWeek = ArgoClimaConfigProvider.DEFAULT_SCHEDULE_WEEKEND;
    private String schedule3OnTime = ArgoClimaConfigProvider.DEFAULT_SCHEDULE_START_TIME;
    private String schedule3OffTime = ArgoClimaConfigProvider.DEFAULT_SCHEDULE_END_TIME;

    public boolean resetToFactoryDefaults = false;

    public InetAddress getOemServerAddress() throws ArgoConfigurationException {
        try {
            return InetAddress.getByName(oemServerAddress);
        } catch (UnknownHostException e) {
            throw new ArgoConfigurationException("Invalid oemServerAddress configuration", oemServerAddress, e);
        }
    }

    /**
     * Converts "raw" {@code Set<Weekday>} into an {@code EnumSet<Weekday>}
     *
     * @implNote Because this configuration parameter is *dynamic* (and deliberately not defined in
     *           {@code thing-types.xml}) when OH is loading a textual thing file, it does not have a full definition
     *           yet, hence CANNOT infer its data type.
     *           The Thing.xtext definition for {@code ModelProperty} allows for arrays, but these are always implicit/
     *           For example {@code schedule1DayOfWeek="MON","TUE"} deserializes as a Collection (and is properly cast
     *           to enum later), however a {@code schedule1DayOfWeek="MON"} deserializes to a String, and causes a
     *           {@link ClassCastException} on access. This impl. accounts for that forced "as-String" interpretation on
     *           load, and coerces such values back to a collection.
     * @param rawInput The value to process
     * @param paramName Name of the textual parameter (for error messaging)
     * @return Converted value
     * @throws ArgoConfigurationException In case the conversion fails
     */
    private EnumSet<Weekday> canonizeWeekdaysAfterDeserialization(Set<Weekday> rawInput, String paramName)
            throws ArgoConfigurationException {
        try {
            var items = rawInput.toArray();
            if (items.length == 1 && !(items[0] instanceof Weekday)) {
                // Text based configuration -> falling back to string parse
                var strValue = StringUtils.strip(items[0].toString(), "[]- \t\"'").trim();
                var daysStr = Arrays.stream(StringUtils.splitByWholeSeparator(strValue, ","));

                var result = EnumSet.noneOf(Weekday.class);
                daysStr.map(ds -> Weekday.valueOf(ds)).forEach(wd -> result.add(wd));
                return result;
            } else {
                // UI/API configuration (nicely strong-typed already)
                return EnumSet.copyOf(rawInput);
            }
        } catch (ClassCastException | IllegalArgumentException e) {
            throw new ArgoConfigurationException(String.format("Invalid %s format", paramName), rawInput.toString(), e);
        }
    }

    @Override
    public EnumSet<Weekday> getSchedule1DayOfWeek() throws ArgoConfigurationException {
        if (schedule1DayOfWeek.isEmpty()) {
            return ArgoClimaConfigProvider.DEFAULT_SCHEDULE_WEEKDAYS;
        }
        return canonizeWeekdaysAfterDeserialization(schedule1DayOfWeek, "schedule1DayOfWeek");
    }

    @Override
    public LocalTime getSchedule1OnTime() throws ArgoConfigurationException {
        try {
            return LocalTime.parse(schedule1OnTime);
        } catch (DateTimeParseException e) {
            throw new ArgoConfigurationException("Invalid schedule1OnTime format", schedule1OnTime, e);
        }
    }

    @Override
    public LocalTime getSchedule1OffTime() throws ArgoConfigurationException {
        try {
            return LocalTime.parse(schedule1OffTime);
        } catch (DateTimeParseException e) {
            throw new ArgoConfigurationException("Invalid schedule1OffTime format", schedule1OffTime, e);
        }
    }

    @Override
    public EnumSet<Weekday> getSchedule2DayOfWeek() throws ArgoConfigurationException {
        if (schedule2DayOfWeek.isEmpty()) {
            return ArgoClimaConfigProvider.DEFAULT_SCHEDULE_WEEKDAYS;
        }
        return canonizeWeekdaysAfterDeserialization(schedule2DayOfWeek, "schedule2DayOfWeek");
    }

    @Override
    public LocalTime getSchedule2OnTime() throws ArgoConfigurationException {
        try {
            return LocalTime.parse(schedule2OnTime);
        } catch (DateTimeParseException e) {
            throw new ArgoConfigurationException("Invalid schedule2OnTime format", schedule2OnTime, e);
        }
    }

    @Override
    public LocalTime getSchedule2OffTime() throws ArgoConfigurationException {
        try {
            return LocalTime.parse(schedule2OffTime);
        } catch (DateTimeParseException e) {
            throw new ArgoConfigurationException("Invalid schedule2OffTime format", schedule2OffTime, e);
        }
    }

    @Override
    public EnumSet<Weekday> getSchedule3DayOfWeek() throws ArgoConfigurationException {
        if (schedule3DayOfWeek.isEmpty()) {
            return ArgoClimaConfigProvider.DEFAULT_SCHEDULE_WEEKDAYS;
        }
        return canonizeWeekdaysAfterDeserialization(schedule3DayOfWeek, "schedule3DayOfWeek");
    }

    @Override
    public LocalTime getSchedule3OnTime() throws ArgoConfigurationException {
        try {
            return LocalTime.parse(schedule3OnTime);
        } catch (DateTimeParseException e) {
            throw new ArgoConfigurationException("Invalid schedule3OnTime format", schedule3OnTime, e);
        }
    }

    @Override
    public LocalTime getSchedule3OffTime() throws ArgoConfigurationException {
        try {
            return LocalTime.parse(schedule3OffTime);
        } catch (DateTimeParseException e) {
            throw new ArgoConfigurationException("Invalid schedule3OffTime format", schedule3OffTime, e);
        }
    }

    /////////////////////
    // Helper functions
    /////////////////////

    /**
     * Utility function for logging only. Gets a parsed value from the supplier function or, exceptionally the raw
     * value. Swallows exceptions.
     *
     * @param <T> Actual type of variable returned by the supplier (parsed)
     * @param fn Parser function
     * @return String param value (if parsed correctly), or the default value post-fixed with {@code [raw]} - on parse
     *         failure.
     */
    protected static <T> String getOrDefault(ConfigValueSupplier<T> fn) {
        try {
            return fn.get().toString();
        } catch (ArgoConfigurationException e) {
            return e.rawValue + "[raw]";
        }
    }

    @Override
    public final String toString() {
        return String.format("Config: { %s, deviceCpuId=%s, refreshInterval=%d, oemServerPort=%d, oemServerAddress=%s,"
                + "schedule1DayOfWeek=%s, schedule1OnTime=%s, schedule1OffTime=%s, schedule2DayOfWeek=%s, schedule2OnTime=%s, schedule2OffTime=%s, schedule3DayOfWeek=%s, schedule3OnTime=%s, schedule3OffTime=%s, resetToFactoryDefaults=%s}",
                getExtraFieldDescription(), deviceCpuId, refreshInterval, oemServerPort,
                getOrDefault(this::getOemServerAddress), getOrDefault(this::getSchedule1DayOfWeek),
                getOrDefault(this::getSchedule1OnTime), getOrDefault(this::getSchedule1OffTime),
                getOrDefault(this::getSchedule2DayOfWeek), getOrDefault(this::getSchedule2OnTime),
                getOrDefault(this::getSchedule2OffTime), getOrDefault(this::getSchedule3DayOfWeek),
                getOrDefault(this::getSchedule3OnTime), getOrDefault(this::getSchedule3OffTime),
                resetToFactoryDefaults);
    }

    /**
     * Return derived class'es extra configuration parameters (for a common {@link toString} implementation)
     *
     * @return Comma-separated list of configuration parameter=value pairs or empty String if derived class does not
     *         introduce any.
     */
    protected abstract String getExtraFieldDescription();

    /**
     * Validate derived configuration
     *
     * @throws ArgoConfigurationException - on validation failure
     */
    protected abstract void validateInternal() throws ArgoConfigurationException;

    /**
     * Validate current config
     *
     * @return Error message if config is invalid. Empty string - otherwise
     */
    public final String validate() {
        if (refreshInterval < 0) {
            return "Refresh interval must be >= 0";
        }

        if (oemServerPort < 0 || oemServerPort >= 65536) {
            return "OEM server port must be in range [0..65536]";
        }

        try {
            // want the side-effect of these calls
            getOemServerAddress();

            getSchedule1DayOfWeek();
            getSchedule1OnTime();
            getSchedule1OffTime();

            getSchedule2DayOfWeek();
            getSchedule2OnTime();
            getSchedule2OffTime();

            getSchedule3DayOfWeek();
            getSchedule3OnTime();
            getSchedule3OffTime();

            validateInternal();
            return "";
        } catch (Exception e) {
            var msg = Optional.ofNullable(e.getMessage());
            var cause = Optional.ofNullable(e.getCause());
            return msg.orElse("Null exception message")
                    .concat(cause.map(c -> "\n\tCause: " + c.getMessage()).orElse(""));
        }
    }
}
