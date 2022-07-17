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
package org.openhab.binding.argoclima.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link ArgoClimaBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaBindingConstants {

    private static final String BINDING_ID = "argoclima";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ARGOCLIMA_LOCAL = new ThingTypeUID(BINDING_ID, "argoclima");

    // List of all Channel ids
    public static final String CHANNEL_1 = "channel1";
}
