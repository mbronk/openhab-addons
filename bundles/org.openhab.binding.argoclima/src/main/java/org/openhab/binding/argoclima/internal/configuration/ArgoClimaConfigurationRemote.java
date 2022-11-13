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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.argoclima.internal.exception.ArgoConfigurationException;

/**
 * The {@link ArgoClimaConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaConfigurationRemote extends ArgoClimaConfigurationBase {

    public static final Duration LAST_SEEN_UNAVAILABILITY_THRESHOLD = Duration.ofMinutes(18);
    /**
     * Sample configuration parameters. Replace with your own.
     */
    public String username = "";
    public String password = ""; // TODO: parameterize

    public String getPasswordMasked() {
        return this.password.replaceAll(".", "*");
    }

    public String getPasswordMD5Hash() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
            md.update(password.getBytes());
            byte[] digest = md.digest();
            return DatatypeConverter.printHexBinary(digest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getExtraFieldDescription() {
        return String.format("username=%s, password=%s", username, getPasswordMasked());
    }

    @Override
    protected void validateInternal() throws ArgoConfigurationException {
        if (username.isBlank()) {
            throw new ArgoConfigurationException("Username is empty. Must be set to Argo login");
        }
        if (password.isBlank()) {
            throw new ArgoConfigurationException("Password is empty. Must be set to Argo password");
        }
    }
}