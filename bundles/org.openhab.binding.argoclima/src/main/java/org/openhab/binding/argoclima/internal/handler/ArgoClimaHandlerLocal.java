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
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationLocal;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationLocal.ConnectionMode;
import org.openhab.binding.argoclima.internal.device_api.ArgoClimaLocalDevice;
import org.openhab.binding.argoclima.internal.device_api.IArgoClimaDeviceAPI;
import org.openhab.binding.argoclima.internal.device_api.passthrough.PassthroughHttpClient;
import org.openhab.binding.argoclima.internal.device_api.passthrough.RemoteArgoApiServerStub;
import org.openhab.binding.argoclima.internal.exception.ArgoConfigurationException;
import org.openhab.binding.argoclima.internal.exception.ArgoRemoteServerStubStartupException;
import org.openhab.core.i18n.TimeZoneProvider;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Thing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ArgoClimaHandlerLocal} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaHandlerLocal extends ArgoClimaHandlerBase<ArgoClimaConfigurationLocal> {

    private final Logger logger = LoggerFactory.getLogger(ArgoClimaHandlerLocal.class);

    private final HttpClient client;
    private final HttpClientFactory clientFactory;
    private final TimeZoneProvider timeZoneProvider;
    private @Nullable RemoteArgoApiServerStub serverStub;

    public ArgoClimaHandlerLocal(Thing thing, HttpClientFactory clientFactory, TimeZoneProvider timeZoneProvider) {
        super(thing, true, Duration.ofSeconds(3), Duration.ofSeconds(10), Duration.ofSeconds(20),
                Duration.ofSeconds(120)); // device polls every minute, so give it 2 to catch up
        this.client = clientFactory.getCommonHttpClient();
        this.clientFactory = clientFactory;
        this.timeZoneProvider = timeZoneProvider;
    }

    @Override
    protected ArgoClimaConfigurationLocal getConfigInternal() throws ArgoConfigurationException {
        try {
            return getConfigAs(ArgoClimaConfigurationLocal.class);
        } catch (IllegalArgumentException ex) {
            throw new ArgoConfigurationException("Error loading thing configuration", "", ex);
        }
    }

    @Override
    protected IArgoClimaDeviceAPI initializeDeviceApi(ArgoClimaConfigurationLocal config) throws Exception {
        // TODO Auto-generated method stub

        var deviceApi = new ArgoClimaLocalDevice(config, config.getHostname(), config.getLocalDevicePort(),
                config.getLocalDeviceIP(), config.getDeviceCpuId(), this.client, this.timeZoneProvider,
                this::updateChannelsFromDevice, this::updateStatus, this::updateThingProperties);

        if (config.getConnectionMode() == ConnectionMode.REMOTE_API_PROXY
                || config.getConnectionMode() == ConnectionMode.REMOTE_API_STUB) {
            var passthroughClient = Optional.<PassthroughHttpClient>empty();
            if (config.getConnectionMode() == ConnectionMode.REMOTE_API_PROXY) {
                passthroughClient = Optional.of(new PassthroughHttpClient(config.getOemServerAddress().getHostAddress(),
                        config.getOemServerPort(), clientFactory));
            }

            serverStub = new RemoteArgoApiServerStub(config.getStubServerListenAddresses(), config.getStubServerPort(),
                    this.getThing().getUID().toString(), passthroughClient, Optional.of(deviceApi),
                    config.getShowCleartextPasswords());
            try {
                serverStub.start();
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                logger.error("Failed to start RPC server", e1); // TODO: crash
                throw new ArgoRemoteServerStubStartupException(
                        String.format("[%s mode] Failed to start RPC server at port: %d. Error: %s",
                                config.getConnectionMode(), config.getStubServerPort(), e1.getMessage()));
            }
        }
        return deviceApi;
    }

    @Override
    public void dispose() {
        super.dispose();

        try {
            synchronized (this) {
                if (this.serverStub != null) {
                    this.serverStub.shutdown();
                    this.serverStub = null;
                }
            }
        } catch (Exception e) {
            logger.warn("Exception during handler disposal", e);
        }

        logger.debug("{}: Disposed", getThing().getUID().getId());
    }
}
