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
package org.openhab.binding.argoclima.internal.handler;

import java.time.Duration;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationRemote;
import org.openhab.binding.argoclima.internal.device_api.ArgoClimaRemoteDevice;
import org.openhab.binding.argoclima.internal.device_api.IArgoClimaDeviceAPI;
import org.openhab.binding.argoclima.internal.exception.ArgoConfigurationException;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Thing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ArgoClimaHandlerRemote} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaHandlerRemote extends ArgoClimaHandlerBase<ArgoClimaConfigurationRemote> {

    private final Logger logger = LoggerFactory.getLogger(ArgoClimaHandlerRemote.class);
    private final HttpClient client;
    private final TimeZoneProvider timeZoneProvider;

    public ArgoClimaHandlerRemote(Thing thing, HttpClientFactory clientFactory, TimeZoneProvider timeZoneProvider) {
        super(thing, true, Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(60));
        this.client = clientFactory.getCommonHttpClient();
        this.timeZoneProvider = timeZoneProvider;
    }

    @Override
    protected ArgoClimaConfigurationRemote getConfigInternal() throws ArgoConfigurationException {
        try {
            return getConfigAs(ArgoClimaConfigurationRemote.class);
        } catch (IllegalArgumentException ex) {
            throw new ArgoConfigurationException("Error loading thing configuration", "", ex);
        }
    }

    @Override
    protected IArgoClimaDeviceAPI initializeDeviceApi(ArgoClimaConfigurationRemote config) throws Exception {
        return new ArgoClimaRemoteDevice(config, this.client, this.timeZoneProvider, config.getOemServerAddress(),
                config.oemServerPort, config.username, config.getPasswordMD5Hash(), this::updateChannelsFromDevice,
                this::updateStatus, this::updateThingProperties);
    }

    @Override
    public void dispose() {
        super.dispose();
        logger.debug("{}: Disposed", getThing().getUID().getId());
    }
}
