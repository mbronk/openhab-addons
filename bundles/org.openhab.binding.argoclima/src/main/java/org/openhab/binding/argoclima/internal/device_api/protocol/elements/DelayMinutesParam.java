/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.argoclima.internal.device_api.protocol.elements;

import java.util.Optional;

import javax.measure.quantity.Time;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.protocol.ArgoDeviceStatus;
import org.openhab.binding.argoclima.internal.device_api.protocol.IArgoSettingProvider;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.device_api.types.TimerType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delay timer element (accepting values in minutes and constrained in both range and precision)
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class DelayMinutesParam extends ArgoApiElementBase {

    private static final Logger logger = LoggerFactory.getLogger(DelayMinutesParam.class);
    private final int minValue;
    private final int maxValue;
    private final int step;
    private Optional<Integer> currentValue;

    /**
     * C-tor
     *
     * @param settingsProvider the settings provider (getting device state as well as schedule configuration)
     * @param min Minimum value of this timer (in minutes)
     * @param max Maximum value of this timer (in minutes)
     * @param step Minimum step of the timer (values will be rounded to nearest step, increments/decrements will move by
     *            step)
     * @param initialValue The initial value of this setting, in minutes (since the value is write-only, need to provide
     *            a value for the increments/decrements to work)
     */
    public DelayMinutesParam(IArgoSettingProvider settingsProvider, int min, int max, int step,
            Optional<Integer> initialValue) {
        super(settingsProvider);
        this.minValue = min;
        this.maxValue = max;
        this.step = step;
        this.currentValue = initialValue;
    }

    /**
     * Converts the raw value to framework-compatible {@link State}
     *
     * @param value Value to convert
     * @return Converted value (or {@code UNDEF} on conversion failure)
     */
    private static State valueToState(Optional<Integer> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }

        return new QuantityType<Time>(value.get(), Units.MINUTE);
    }

    private int adjustRange(int newValue) {
        if (newValue < minValue) {
            logger.warn("Requested value: {} would exceed minimum value: {}. Setting: {}.", newValue, minValue,
                    minValue);
            return minValue;
        }
        if (newValue > maxValue) {
            logger.warn("Requested value: {} would exceed maximum value: {}. Setting: {}.", newValue, maxValue,
                    maxValue);
            return maxValue;
        }

        // round to nearest step
        return (int) Math.round((double) newValue / step) * step;
    }

    /**
     * Normalizes the incoming value (respecting steps), with amplification of movement
     * <p>
     * Ex. if the step is 10, current value is 50 and the new value is 51... while 50 is still a closest, we're moving
     * to a full next step (60), not to ignore user's intent to change something
     *
     * @param newValue the raw value to update
     * @return Sanitized value (with amplified movement)
     */
    private int adjustRangeWithAmplification(int newValue) {
        int normalized = adjustRange(newValue);

        if (currentValue.isEmpty() || normalized == newValue || normalized <= minValue || normalized >= maxValue) {
            return normalized; // there was no previous value or normalization didn't remove any precision or reached a
                               // boundary -> new normalized value wins
        }

        final int thisValue = currentValue.orElseThrow();

        if (normalized != thisValue) {
            return normalized; // the normalized value changed enough to be meaningful -> use it
        }

        // Value before normalization has moved, but not enough to move a step (and would have been ignored). Let's
        // amplify that effect and add a new step
        var movementDirection = Integer.signum(newValue - normalized);
        return normalized + movementDirection * step;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote The currently used context of this class (on/off schedule time) has WRITE-ONLY elements, hence this
     *           method is unlikely to ever be called
     */
    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        strToInt(responseValue).ifPresent(raw -> {
            currentValue = Optional.of(adjustRange(raw));
        });
    }

    @Override
    public State toState() {
        return valueToState(currentValue);
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get().toString() + " min";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Timer delay value is always sent to the device together with Timer=Delay command
     * (so that the clock resets)
     */
    @Override
    public boolean isAlwaysSent() {
        return isDelayTimerBeingActivated();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The delay timer value should be send whenever there's an active change (command) to a delay timer (technically
     * flipping from Delay timer back to the Delay timer, w/o changing the delay value should re-arm the timer)
     */
    @Override
    public String getDeviceApiValue() {
        var defaultResult = super.getDeviceApiValue();

        if (defaultResult != ArgoDeviceStatus.NO_VALUE || currentValue.isEmpty() || !isDelayTimerBeingActivated()) {
            return defaultResult; // There's already a pending command recognized by binding, or delay timer is has no
                                  // pending command -
                                  // we're good to go with the default
        }

        // There's a pending change to Delay timer -> let's send our value then
        return Integer.toString(currentValue.orElseThrow());
    }

    /**
     * Checks if Delay timer is currently being commanded to become active on the device (pending commands!)
     *
     * @return True, if delay timer is currently being activated on the device, False otherwise
     */
    private boolean isDelayTimerBeingActivated() {
        var setting = settingsProvider.getSetting(ArgoDeviceSettingType.ACTIVE_TIMER);
        var currentTimerValue = EnumParam.fromType(setting.getState(), TimerType.class);

        var isDelayCurrentlySet = currentTimerValue.map(t -> t.equals(TimerType.DELAY_TIMER)).orElse(false);

        return isDelayCurrentlySet && setting.hasInFlightCommand();
    }

    /**
     * Checks if Delay timer is active already (or being commanded to do so)
     * <p>
     * Used to defer timer value updates in case there's no timer action ongoing (no need to send the timer value to the
     * device)
     *
     * @return True, if delay timer is currently active on the device, False otherwise
     */
    private final boolean isDelayTimerCurrentlyActive() {
        var currentTimer = EnumParam
                .fromType(settingsProvider.getSetting(ArgoDeviceSettingType.ACTIVE_TIMER).getState(), TimerType.class);

        return currentTimer.map(t -> t.equals(TimerType.DELAY_TIMER)).orElse(false);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Since this method rounds to next step and some updates may be missed, we're forcing any direction
     *           movements to move a full step through {@link #adjustRangeWithAmplification(int)}
     */
    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {

        int newRawValue;

        if (command instanceof Number) {
            newRawValue = ((Number) command).intValue(); // Raw value, not unit-aware

            if (command instanceof QuantityType<?>) { // let's try to get it with unit (opportunistically)

                var inMinutes = ((QuantityType<?>) command).toUnit(Units.MINUTE);
                if (null != inMinutes) {
                    newRawValue = inMinutes.intValue();
                }
            }
        } else if (command instanceof IncreaseDecreaseType) {
            var asCommand = (IncreaseDecreaseType) command;
            var base = this.currentValue.orElse(adjustRange((this.minValue + this.maxValue) / 2));
            if (asCommand.equals(IncreaseDecreaseType.INCREASE)) {
                base += step;
            } else if (asCommand.equals(IncreaseDecreaseType.DECREASE)) {
                base -= step;
            }
            newRawValue = base;
        } else {
            return HandleCommandResult.rejected(); // unsupported type of command
        }

        newRawValue = adjustRangeWithAmplification(newRawValue);

        // Not checking if current value is the same as requested (delay timer set resets the clock)
        this.currentValue = Optional.of(newRawValue);

        // Accept the command (and if it was sent when no timer was active, make it deferred)
        return HandleCommandResult.accepted(Integer.toString(newRawValue), valueToState(Optional.of(newRawValue)))
                .setDeferred(!isDelayTimerCurrentlyActive());
    }
}
