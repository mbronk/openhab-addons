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
package org.openhab.binding.argoclima.internal.exception;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The class {@code ArgoLocalApiCommunicationException} is thrown in case of any issues when communicating with the Argo
 * HVAC device
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoLocalApiCommunicationException extends Exception {

    private static final long serialVersionUID = 7770599701572999260L;

    /**
     * @param message
     */
    public ArgoLocalApiCommunicationException(String message) {
        super(message);
        // TODO Auto-generated constructor stub
    }

    /**
     * @param message
     * @param cause
     */
    public ArgoLocalApiCommunicationException(String message, @Nullable Throwable cause) {
        super(message, cause);
        // TODO Auto-generated constructor stub
    }

    @Override
    public @Nullable String getMessage() {
        var msg = super.getMessage();
        if (this.getCause() != null) {
            msg += ". Caused by: " + Objects.requireNonNull(this.getCause()).getMessage();
        }
        return msg;
    }
}
