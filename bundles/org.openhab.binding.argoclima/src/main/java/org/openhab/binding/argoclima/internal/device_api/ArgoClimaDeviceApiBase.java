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
package org.openhab.binding.argoclima.internal.device_api;

import java.io.EOFException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.openhab.binding.argoclima.internal.device_api.elements.IArgoElement;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author mbronk - Initial contribution
 *
 */
@NonNullByDefault
public abstract class ArgoClimaDeviceApiBase implements IArgoClimaDeviceAPI {
    private final Logger logger = LoggerFactory.getLogger(ArgoClimaDeviceApiBase.class);
    // public final InetAddress ipAddress;
    // private final Optional<InetAddress> localIpAddress;
    // private final Optional<String> cpuId;
    // public int port;
    private final HttpClient client;
    protected ArgoDeviceStatus deviceStatus;
    protected Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate;
    protected Consumer<ThingStatus> onReachableStatusChange;

    public ArgoClimaDeviceApiBase(HttpClient client, Consumer<Map<ArgoDeviceSettingType, State>> onStateUpdate,
            Consumer<ThingStatus> onReachableStatusChange) {
        this.client = client;
        this.deviceStatus = new ArgoDeviceStatus();
        this.onStateUpdate = onStateUpdate;
        this.onReachableStatusChange = onReachableStatusChange;
    }

    protected abstract URL getDeviceStateQueryUrl();

    protected abstract URL getDeviceStateUpdateUrl();

    protected final static URL uriToURL(String uriStr) {
        try {
            return new URL(uriStr);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to build url from: " + uriStr, e);
        }
    }

    private String pollForCurrentStatusFromDeviceSync(URL url) throws ArgoLocalApiCommunicationException {
        // TODO: consider separate http client and timeouts
        try {
            logger.info("Communication: OPENHAB --> DEVICE: [GET {}]", url);

            ContentResponse resp = this.client.GET(url.toString());

            logger.info("   [response]: OPENHAB <-- DEVICE: [{} {} {} - {} bytes], body=[{}]", resp.getVersion(),
                    resp.getStatus(), resp.getReason(), resp.getContent().length, resp.getContentAsString());

            if (resp.getStatus() != 200) {
                throw new ArgoLocalApiCommunicationException(String.format(
                        "API request yielded invalid response status %d %s (expected HTTP 200 OK). URL was: %s",
                        resp.getStatus(), resp.getReason(), url));
            }
            // logger.warn("Got response {}", resp.getStatus());
            return resp.getContentAsString();
        } catch (InterruptedException ex) {
            logger.info("Interrupted...");
            return "";
        } catch (ExecutionException ex) {
            var cause = Optional.ofNullable(ex.getCause());
            if (cause.isPresent() && cause.get() instanceof EOFException) {
                // logger.warn("Cause is: EOF: {}", ((EOFException) cause).getMessage());
                throw new ArgoLocalApiCommunicationException(
                        "Cause is: EOF: " + ((EOFException) cause.get()).getMessage(), cause.get());
            }
            throw new ArgoLocalApiCommunicationException("Device communication error: " + ex.getCause().getMessage(),
                    ex.getCause());
        } catch (TimeoutException e) {
            throw new ArgoLocalApiCommunicationException("Timeout: " + e.getMessage(), e);
        }
    }

    @Override
    public final Pair<Boolean, String> isReachable() {
        // TODO: last successful comms also may qualify?

        try {
            extractDeviceStatusFromResponse(pollForCurrentStatusFromDeviceSync(getDeviceStateQueryUrl()));
            return Pair.of(true, "");
        } catch (ArgoLocalApiCommunicationException e) {
            logger.warn("Device not reachable: {}", e.getMessage());
            return Pair.of(false,
                    MessageFormat
                            .format("Failed to communicate with Argo HVAC device at [host: {0}, port: {1,number,#}]",
                                    this.getDeviceStateQueryUrl().getHost(),
                                    this.getDeviceStateQueryUrl().getPort() != -1
                                            ? this.getDeviceStateQueryUrl().getPort()
                                            : this.getDeviceStateQueryUrl().getDefaultPort()));
        }
    }

    protected abstract String extractDeviceStatusFromResponse(String apiResponse)
            throws ArgoLocalApiCommunicationException;

    @Override
    public Map<ArgoDeviceSettingType, State> queryDeviceForUpdatedState() throws ArgoLocalApiCommunicationException {
        // this.client.setAddressResolutionTimeout(10 *1000);
        // TODO: if not reachable, calling it makes zero sense!

        var deviceResponse = pollForCurrentStatusFromDeviceSync(getDeviceStateQueryUrl());
        this.deviceStatus.fromDeviceString(extractDeviceStatusFromResponse(deviceResponse));
        return this.deviceStatus.getCurrentStateMap();
    }

    @Override
    public void sendCommandsToDevice() throws ArgoLocalApiCommunicationException {
        var deviceResponse = pollForCurrentStatusFromDeviceSync(getDeviceStateUpdateUrl());
        logger.info("State update command finished. Device response: {}", deviceResponse);
    }

    @Override
    public boolean handleSettingCommand(ArgoDeviceSettingType settingType, Command command) {
        return this.deviceStatus.getSetting(settingType).handleCommand(command);
    }

    @Override
    public State getCurrentStateNoPoll(ArgoDeviceSettingType settingType) {
        return this.deviceStatus.getSetting(settingType).getState();
    }

    @Override
    public boolean hasPendingCommands() {
        var itemsWithPendingUpdates = this.deviceStatus.getItemsWithPendingUpdates();
        logger.info("Items to update: {}", itemsWithPendingUpdates);
        return !this.deviceStatus.getItemsWithPendingUpdates().isEmpty();
        // return this.deviceStatus.hasUpdatesPending();
    }

    @Override
    public List<ArgoApiDataElement<IArgoElement>> getItemsWithPendingUpdates() {
        return this.deviceStatus.getItemsWithPendingUpdates();
    }
}
