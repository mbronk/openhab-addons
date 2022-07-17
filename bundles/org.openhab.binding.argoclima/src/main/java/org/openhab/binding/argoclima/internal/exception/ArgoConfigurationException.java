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
package org.openhab.binding.argoclima.internal.exception;

/**
 * @author bronk
 *
 */
public class ArgoConfigurationException extends Exception {

    private static final long serialVersionUID = 174501670495658964L;
    public final String rawValue;

    private static String getMessageEx(String message, String paramValue) {
        return String.format("%s. Value: [%s].", message, paramValue);
    }

    /**
     * @param message
     */
    public ArgoConfigurationException(String message) {
        super(message);
        this.rawValue = "";
    }

    public ArgoConfigurationException(String message, String paramValue) {
        super(getMessageEx(message, paramValue));
        this.rawValue = paramValue;
    }

    public ArgoConfigurationException(String message, String paramValue, Throwable e) {
        super(getMessageEx(message, paramValue), e);
        this.rawValue = paramValue;
    }
}
