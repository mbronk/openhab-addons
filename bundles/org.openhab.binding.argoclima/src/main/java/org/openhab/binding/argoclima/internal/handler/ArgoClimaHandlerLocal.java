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

import java.net.InetAddress;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfiguration;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfiguration.ConnectionMode;
import org.openhab.binding.argoclima.internal.device_api.ArgoClimaLocalDevice;
import org.openhab.binding.argoclima.internal.device_api.IArgoClimaDeviceAPI;
import org.openhab.binding.argoclima.internal.device_api.passthrough.PassthroughHttpClient;
import org.openhab.binding.argoclima.internal.device_api.passthrough.RemoteArgoApiServerStub;
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
public class ArgoClimaHandlerLocal extends ArgoClimaHandlerBase<ArgoClimaConfiguration> {

    private final Logger logger = LoggerFactory.getLogger(ArgoClimaHandlerLocal.class);

    private final HttpClient client;
    private final HttpClientFactory clientFactory;
    private final TimeZoneProvider timeZoneProvider;
    private @Nullable RemoteArgoApiServerStub serverStub;

    public ArgoClimaHandlerLocal(Thing thing, HttpClientFactory clientFactory, TimeZoneProvider timeZoneProvider) {
        super(thing);
        this.client = clientFactory.getCommonHttpClient();
        this.clientFactory = clientFactory;
        this.timeZoneProvider = timeZoneProvider;
    }

    @Override
    protected ArgoClimaConfiguration getConfigInternal() {
        return getConfigAs(ArgoClimaConfiguration.class);
    }

    @Override
    protected IArgoClimaDeviceAPI initializeDeviceApi(ArgoClimaConfiguration config) throws Exception {
        // TODO Auto-generated method stub

        var targetCpuID = config.deviceCpuId.isBlank() ? Optional.<String>empty() : Optional.of(config.deviceCpuId); // TODO
        var localIpAddress = config.localDeviceIP.isBlank() ? Optional.<InetAddress>empty()
                : Optional.of(config.getLocalDeviceIP()); // TODO

        var deviceApi = new ArgoClimaLocalDevice(config.getHostname(), config.localDevicePort, localIpAddress,
                targetCpuID, this.client, this.timeZoneProvider, this::updateChannelsFromDevice, this::updateStatus,
                this::updateThingProperties);

        if (config.connectionMode == ConnectionMode.REMOTE_API_PROXY
                || config.connectionMode == ConnectionMode.REMOTE_API_STUB) {
            var passthroughClient = Optional.<PassthroughHttpClient>empty();
            if (config.connectionMode == ConnectionMode.REMOTE_API_PROXY) {
                passthroughClient = Optional.of(new PassthroughHttpClient(config.getOemServerAddress().getHostAddress(),
                        config.oemServerPort, clientFactory));
            }

            serverStub = new RemoteArgoApiServerStub(config.getStubServerListenAddresses(), config.stubServerPort,
                    this.getThing().getUID().toString(), passthroughClient, Optional.of(deviceApi));
            try {
                serverStub.start();
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                logger.error("Failed to start RPC server", e1); // TODO: crash
                throw new ArgoRemoteServerStubStartupException(
                        String.format("[%s mode] Failed to start RPC server at port: %d. Error: %s",
                                config.connectionMode, config.stubServerPort, e1.getMessage()));
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
