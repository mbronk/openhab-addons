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
package org.openhab.binding.argoclima.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationRemote;
import org.openhab.binding.argoclima.internal.device_api.ArgoClimaRemoteDevice;
import org.openhab.binding.argoclima.internal.device_api.IArgoClimaDeviceAPI;
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
    private final HttpClientFactory clientFactory;

    public ArgoClimaHandlerRemote(Thing thing, HttpClientFactory clientFactory) {
        super(thing);
        this.client = clientFactory.getCommonHttpClient();
        this.clientFactory = clientFactory;
    }

    @Override
    protected ArgoClimaConfigurationRemote getConfigInternal() {
        return getConfigAs(ArgoClimaConfigurationRemote.class);
    }

    @Override
    protected IArgoClimaDeviceAPI initializeDeviceApi(ArgoClimaConfigurationRemote config) throws Exception {
        return new ArgoClimaRemoteDevice(this.client, config.getOemServerAddress(), config.oemServerPort,
                config.username, config.getPasswordMD5Hash(), this::updateChannelsFromDevice, this::updateStatus);
    }

    @Override
    public void dispose() {
        super.dispose();
        logger.debug("{}: Disposed", getThing().getUID().getId());
    }
}
