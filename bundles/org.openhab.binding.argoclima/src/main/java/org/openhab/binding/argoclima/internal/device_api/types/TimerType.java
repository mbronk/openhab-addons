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
package org.openhab.binding.argoclima.internal.device_api.types;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
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
