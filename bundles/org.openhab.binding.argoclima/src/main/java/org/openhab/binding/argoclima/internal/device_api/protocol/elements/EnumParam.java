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

import java.util.EnumSet;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.device_api.protocol.IArgoSettingProvider;
import org.openhab.binding.argoclima.internal.device_api.types.IArgoApiEnum;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 *
 * @param <E>
 */
@NonNullByDefault
public class EnumParam<E extends Enum<E> & IArgoApiEnum> extends ArgoApiElementBase {
    private static final Logger logger = LoggerFactory.getLogger(EnumParam.class);
    private Optional<E> currentValue = Optional.empty();
    private Class<E> cls;

    public EnumParam(IArgoSettingProvider settingsProvider, Class<E> cls) {
        super(settingsProvider);
        this.cls = cls;
        this.currentValue = Optional.empty();
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        // TODO Auto-generated method stub
        int rawValue = 0;
        try {
            rawValue = Integer.parseInt(responseValue);
        } catch (NumberFormatException e) {
            throw new RuntimeException(String.format("The value %s is not a valid integer", responseValue), e);
        }

        this.currentValue = this.fromInt(rawValue);
    }

    // @SuppressWarnings("null") // TODO
    private Optional<E> fromInt(int value) {
        return EnumSet.allOf(this.cls).stream().filter(p -> p.getIntValue() == value).findFirst();
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get().toString();
        // return currentValue.toString();
    }

    private static <E extends Enum<E> & IArgoApiEnum> State valueToState(Optional<E> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }
        return new StringType(value.get().toString());
    }

    @Override
    public State toState() {
        return valueToState(currentValue);
    }

    /**
     * Gets the raw enum value from {@link Command} or {@link State}
     *
     * @param <E>
     * @param value
     * @param cls
     * @return
     */
    public static <E extends Enum<E> & IArgoApiEnum> Optional<E> fromType(Type value, Class<E> cls) {
        if (value instanceof StringType) {
            String newValue = ((StringType) value).toFullString();
            try {
                return Optional.of(Enum.valueOf(cls, newValue));
            } catch (IllegalArgumentException ex) {
                logger.warn("Failed to convert value: {} to enum. {}", value, ex.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        if (command instanceof StringType) {
            E val = fromType(command, cls).get();
            if (this.currentValue.isEmpty() || this.currentValue.get().compareTo(val) != 0) {

                var newRawValue = Optional.of(val);

                this.currentValue = newRawValue;
                return HandleCommandResult.accepted(Integer.toString(newRawValue.get().getIntValue()),
                        valueToState(newRawValue));
            }
        }

        return HandleCommandResult.rejected();
    }

    // protected @Nullable String handleCommandInternal(Command command) {
    // if (command instanceof QuantityType<?>) {
    // int newValue = ((QuantityType<?>) command).intValue();
    // if (this.currentValue.isEmpty() || this.currentValue.get().intValue() != newValue) {
    // this.currentValue = Optional.of(newValue);
    // }
    // return Integer.toString(this.currentValue.get().intValue());
    // }
    // return null; // TODO
    // }
}
