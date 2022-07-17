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
package org.openhab.binding.argoclima.internal.device_api.types;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public enum OperationMode implements IArgoApiEnum {
    COOL(1),
    DRY(2),
    WARM(3),
    FAN(4),
    AUTO(5);

    private int value;

    OperationMode(int intValue) {
        this.value = intValue;
    }

    @Override
    public int getIntValue() {
        return this.value;
    }

    // @SuppressWarnings("null") // TODO
    // public static Optional<OperationMode> fromInt(int value) {
    // return Arrays.stream(OperationMode.values()).filter(p -> p.ordinal() == value).findFirst();
    // }
}
