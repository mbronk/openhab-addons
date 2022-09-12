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

import static org.openhab.binding.argoclima.internal.ArgoClimaBindingConstants.CHANNEL_1;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.argoclima.internal.device_api.ArgoClimaLocalDevice;
import org.openhab.binding.argoclima.internal.device_api.passthrough.PassthroughHttpClient;
import org.openhab.binding.argoclima.internal.device_api.passthrough.RemoteArgoApiServerStub;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ArgoClimaHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public class ArgoClimaHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(ArgoClimaHandler.class);

    private Optional<ArgoClimaConfiguration> config;

    private final HttpClient client;
    private final HttpClientFactory clientFactory;
    private Optional<ArgoClimaLocalDevice> deviceApi;
    private @Nullable ScheduledFuture<?> refreshTask;
    // private Optional<Future> oneOffRefreshJob;
    private @Nullable Future<?> initializeFuture;
    private Optional<Future<?>> settingsUpdateFuture = Optional.empty();
    // private Optional<CompletableFuture<Boolean>> sendCommandToDeviceFuture = Optional.empty();
    private final int MAX_API_RETRIES = 3;

    private @Nullable RemoteArgoApiServerStub serverStub;

    private boolean forceRefresh = false;
    private long lastRefreshTime = 0;
    private long apiRetries = 0;
    private final int MAX_UPDATE_RETRIES = 3;

    public ArgoClimaHandler(Thing thing, HttpClientFactory clientFactory) {
        super(thing);
        this.client = clientFactory.getCommonHttpClient();
        this.deviceApi = Optional.empty();
        this.clientFactory = clientFactory;
        this.config = Optional.empty();
    }

    private synchronized void cancelPendingSettingsUpdateJob() {
        settingsUpdateFuture.ifPresent(x -> {
            if (!x.isDone()) {
                logger.info("Cancelling previous update job");
                x.cancel(true);
            }
        });
        settingsUpdateFuture = Optional.empty();
    }

    private void sendCommandsToDeviceAwaitConfirmation(boolean forceRefresh) throws ArgoLocalApiCommunicationException {

        logger.warn("STARTING UPDATES!!!");
        // TODO
        cancelPendingSettingsUpdateJob();

        Supplier<Boolean> fn = () -> {
            try {
                Thread.sleep(100); // naive debounce (not to overflow the device if multiple commands are sent at once)
            } catch (InterruptedException e) {
            }
            var valuesToUpdate = this.deviceApi.get().getItemsWithPendingUpdates();
            logger.info("Will UPDATE the following items: {}", valuesToUpdate);

            int attempt = 0;
            while (true) {
                attempt++;
                try {
                    if (!this.deviceApi.get().hasPendingCommands()) {
                        if (forceRefresh) {
                            this.deviceApi.get().queryDeviceForUpdatedState(); // no updates but refresh was forced
                        } else {
                            logger.info("Nothing to update... skipping"); // update might have occured async
                        }
                        return false; // no update made
                    }

                    this.deviceApi.get().sendCommandsToDevice();
                    if (!this.deviceApi.get().hasPendingCommands()) {
                        logger.info("ALL UPDATED ON 1st try!!");
                        return true;
                    }

                    try {
                        Thread.sleep(3000); // Note: the device takes long to process commands. Giving it a few sec.
                                            // before reconfirming
                    } catch (InterruptedException e) {
                        return true;
                    }
                    this.deviceApi.get().queryDeviceForUpdatedState();
                    if (this.deviceApi.get().hasPendingCommands()) {
                        throw new RuntimeException("Update not confirmed. Value was not set");
                    }
                    return true;

                } catch (Exception ex) {
                    if (attempt < MAX_UPDATE_RETRIES - 1) {
                        try {
                            Thread.sleep(Duration.ofMillis(1500).toMillis());
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            return false;
                        }
                        continue; // try again
                    } else {
                        valuesToUpdate.stream().forEach(x -> x.abortPendingCommand());
                        throw new RuntimeException("Device command failed", ex);
                    }
                }
            }
        };

        synchronized (this) {
            settingsUpdateFuture = Optional.of(scheduler.submit(() -> CompletableFuture.supplyAsync(fn)));
        }
        //
        // Supplier<CompletableFuture<Boolean>> asyncFn = () -> CompletableFuture.supplyAsync(fn);
        //
        // try {
        // sendCommandToDeviceFuture = Optional.of(Retrier.<Boolean>withRetries(scheduler, asyncFn, t -> true, // should
        // // retry
        // MAX_UPDATE_RETRIES, Duration.ofMillis(1500)));
        //
        // // THIS IS CRAP TODO
        // // return sendCommandToDeviceFuture.get().get();
        // // retry on exceptions
        // sendCommandToDeviceFuture.get().exceptionally(x -> {
        // valuesToUpdate.stream().forEach(val -> val.abortPendingCommand());
        // throw new RuntimeException("Failed to send commands to device: " + x.getMessage(), x);
        // }); // .thenApply(CompletableFuture::completedFuture); // NO LONGER ASYNC!!!
        // return true;
        // } catch (Exception e) {
        // valuesToUpdate.stream().forEach(x -> x.abortPendingCommand());
        // throw new ArgoLocalApiCommunicationException("Failed to send commands to device: " + e.getMessage(), e);
        // }

        // abortPendingCommand
        // return false;
    }

    //
    // private boolean sendCommandsToDeviceAwaitConfirmation(boolean forceRefresh)
    // throws ArgoLocalApiCommunicationException {
    //
    // logger.warn("STARTING UPDATES!!!");
    // // TODO
    // sendCommandToDeviceFuture.ifPresent(x -> {
    // logger.info("Cancelling previous update job");
    // x.cancel(true);
    // });
    // sendCommandToDeviceFuture = Optional.empty();
    //
    // // if (settingsUpdateFuture != null) {
    // // settingsUpdateFuture.cancel(true); // ?
    // // settingsUpdateFuture = null;
    // // }
    // //
    //
    // // // safeguard for multiple REFRESH commands
    // // if (isMinimumRefreshTimeExceeded()) {
    //
    // var valuesToUpdate = this.deviceApi.get().getItemsWithPendingUpdates();
    // logger.info("Will UPDATE the following items: {}", valuesToUpdate);
    //
    // Supplier<Boolean> fn = () -> {
    // // return true;
    // try {
    // Thread.sleep(10000);
    // } catch (InterruptedException e2) {
    // // TODO Auto-generated catch block
    // e2.printStackTrace();
    // return false;
    // }
    // logger.warn("SATARTING UPDATES TURBOASYNC UPDATES!!!");
    // if (!this.deviceApi.get().hasPendingCommands()) {
    // if (forceRefresh) {
    // try {
    // this.deviceApi.get().queryDeviceForUpdatedState(); // no updates but refresh was forced
    // } catch (ArgoLocalApiCommunicationException e) {
    // throw new RuntimeException(e);
    // }
    // } else {
    // logger.info("Nothing to update... skipping"); // update might have occured async
    // }
    // return false;
    // }
    // try {
    // this.deviceApi.get().sendCommandsToDevice();
    // } catch (ArgoLocalApiCommunicationException e1) {
    // throw new RuntimeException(e1);
    // }
    // if (!this.deviceApi.get().hasPendingCommands()) {
    // logger.info("ALL UPDATED ON 1st try!!");
    // return false;
    // }
    //
    // try {
    // Thread.sleep(2000); // TODO: this needs *BETTER* delay logic
    // } catch (InterruptedException e) {
    // e.printStackTrace();
    // return false;
    // // TODO Auto-generated catch block
    //
    // }
    // try {
    // this.deviceApi.get().queryDeviceForUpdatedState();
    // } catch (ArgoLocalApiCommunicationException e) {
    // throw new RuntimeException(e);
    // }
    //
    // if (this.deviceApi.get().hasPendingCommands()) {
    // throw new RuntimeException("Update not confirmed. Value was not set");
    // }
    // logger.warn("STOPPING UPDATES!!!");
    // return true;
    // };
    //
    // Supplier<CompletableFuture<Boolean>> asyncFn = () -> CompletableFuture.supplyAsync(fn);
    //
    // try {
    // sendCommandToDeviceFuture = Optional.of(Retrier.<Boolean>withRetries(scheduler, asyncFn, t -> true, // should
    // // retry
    // MAX_UPDATE_RETRIES, Duration.ofMillis(1500)));
    //
    // // THIS IS CRAP TODO
    // // return sendCommandToDeviceFuture.get().get();
    // // retry on exceptions
    // sendCommandToDeviceFuture.get().exceptionally(x -> {
    // valuesToUpdate.stream().forEach(val -> val.abortPendingCommand());
    // throw new RuntimeException("Failed to send commands to device: " + x.getMessage(), x);
    // }); // .thenApply(CompletableFuture::completedFuture); // NO LONGER ASYNC!!!
    // return true;
    // } catch (Exception e) {
    // valuesToUpdate.stream().forEach(x -> x.abortPendingCommand());
    // throw new ArgoLocalApiCommunicationException("Failed to send commands to device: " + e.getMessage(), e);
    // }
    //
    // // abortPendingCommand
    // // return false;
    // }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        try {
            if (command instanceof RefreshType) {
                sendCommandsToDeviceAwaitConfirmation(true); // Irrespective of channel (the API gets all data points in
                                                             // one
                                                             // go)
                return;
            }

            boolean hasUpdates = false;
            if (ArgoClimaBindingConstants.CHANNEL_POWER.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.POWER, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_ACTIVE_TIMER.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.ACTIVE_TIMER, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_CURRENT_TEMPERATURE.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.ACTUAL_TEMPERATURE, command,
                        channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_ECO_MODE.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.ECO_MODE, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_FAN_SPEED.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.FAN_LEVEL, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_FILTER_MODE.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.FILTER_MODE, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_SWING_MODE.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.FLAP_LEVEL, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_I_FEEL_ENABLED.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.I_FEEL_TEMPERATURE, command,
                        channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_DEVICE_LIGHTS.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.LIGHT, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_MODE.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.MODE, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_NIGHT_MODE.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.NIGHT_MODE, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_SET_TEMPERATURE.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.TARGET_TEMPERATURE, command,
                        channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_ACTIVE_TIMER.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.TIMER_TYPE, command, channelUID);
            }
            if (ArgoClimaBindingConstants.CHANNEL_TURBO_MODE.equals(channelUID.getId())) {
                hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.TURBO_MODE, command, channelUID);
            }

            // case DISPLAY_TEMPERATURE_SCALE:
            // break;
            // case ECO_POWER_LIMIT:
            // break;
            // case RESET_TO_FACTORY_SETTINGS:
            // break;
            // case TIMER_0_DELAY_TIME:
            // channelName = ArgoClimaBindingConstants.CHANNEL_DELAY_TIMER;
            // break;
            // case TIMER_N_ENABLED_DAYS:
            // // Scheduletimer
            // break;
            // case TIMER_N_OFF_TIME:
            // break;
            // case TIMER_N_ON_TIME:
            // break;
            // case UNIT_FIRMWARE_VERSION:
            // break;
            // default:
            // break;
            // }

            if (hasUpdates) {
                sendCommandsToDeviceAwaitConfirmation(false);
                // executeStateUpdateWithRetries();
            }
        } catch (ArgoLocalApiCommunicationException ex) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Could not control device at IP address x.x.x.x | " + ex.getMessage());
        }

        if (CHANNEL_1.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                // TODO: handle data refresh
            }

            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information:
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
    }

    // private boolean updateValue(int attemptNumber) {
    // try {
    // if (attemptNumber > 3) {
    //
    // return false;
    // }
    // // safeguard for multiple REFRESH commands
    // if (isMinimumRefreshTimeExceeded()) {
    // if (getThing().getStatus() == ThingStatus.OFFLINE) {
    // // try to re-initialize thing access
    // logger.debug("{}: Re-initialize device", getThing().getUID().getId());
    // initializeThing();
    // return true;
    // }
    //
    // Map<ArgoDeviceSettingType, State> newState = this.deviceApi.get().updateStateFromDevice();
    // logger.info(newState.toString());
    // updateChannelsFromDevice(newState);
    //
    // }
    // } catch (RuntimeException e) {
    // String message = "Runtime exception on update";
    // logger.warn("{}: {}", getThing().getUID().getId(), message, e);
    // return false;
    // }
    // return forceRefresh;
    // }

    private boolean handleIndividualSettingCommand(ArgoDeviceSettingType settingType, Command command,
            ChannelUID channelUID) {

        if (command instanceof RefreshType) {
            // TODO: handle data refresh
            // set value to undef for a while?? drop pending state!!
            return true;
        }
        boolean updateInitiated = this.deviceApi.get().handleSettingCommand(settingType, command);
        if (updateInitiated) {
            State currentState = this.deviceApi.get().getCurrentStateNoPoll(settingType);
            logger.info("State of {} after update: {}", channelUID, currentState);
            updateState(channelUID, currentState); // TODO: assume new state
        }
        return updateInitiated;

        // this.deviceApi.get().
    }

    @Override
    public void initialize() {
        this.config = Optional.of(getConfigAs(ArgoClimaConfiguration.class));
        Objects.requireNonNull(config);

        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly, i.e. any network access must be done in
        // the background initialization below.
        // Also, before leaving this method a thing status from one of ONLINE, OFFLINE or UNKNOWN must be set. This
        // might already be the real thing status in case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.
        if ((config.get().hostname.isEmpty()) || (config.get().refreshInterval < 0)) {
            String message = "Invalid thing configuration";
            logger.warn("{}: {}", getThing().getUID().getId(), message);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
            return;
        }
        try {
            var targetCpuID = config.get().deviceCpuId.isBlank() ? Optional.<String>empty()
                    : Optional.of(config.get().deviceCpuId); // TODO
            var localIpAddress = config.get().localDeviceIP.isBlank() ? Optional.<InetAddress>empty()
                    : Optional.of(InetAddress.getByName(config.get().localDeviceIP)); // TODO

            this.deviceApi = Optional.of(new ArgoClimaLocalDevice(InetAddress.getByName(config.get().hostname),
                    ArgoClimaConfiguration.LOCAL_PORT, localIpAddress, targetCpuID, this.client,
                    this::updateChannelsFromDevice, this::updateStatus));
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            logger.warn("Unable to get local device address", e);
        }

        // TODO: configure all this
        serverStub = new RemoteArgoApiServerStub("127.0.0.1", 8239, this.getThing().getUID().toString(),
                Optional.of(new PassthroughHttpClient("31.14.128.210", 80, clientFactory)), this.deviceApi);
        try {
            serverStub.start();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            logger.error("Failed to start rpc server", e1); // TODO: crash
        }

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.

        updateStatus(ThingStatus.UNKNOWN);

        if (this.config.get().refreshInterval > 0) {
            startAutomaticRefresh();
        }
        initializeFuture = scheduler.submit(this::initializeThing);
        // Example for background initialization:
        // scheduler.execute(() -> {
        // boolean thingReachable = true; // <background task with long running initialization here>
        // // when done do:
        // if (thingReachable) {
        // updateStatus(ThingStatus.ONLINE);
        // } else {
        // updateStatus(ThingStatus.OFFLINE);
        // }
        // });

        // These logging types should be primarily used by bindings
        // logger.trace("Example trace message");
        // logger.debug("Example debug message");
        // logger.warn("Example warn message");
        //
        // Logging to INFO should be avoided normally.
        // See https://www.openhab.org/docs/developer/guidelines.html#f-logging

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    private void startAutomaticRefresh() {
        Runnable refresher = () -> {
            try {
                // safeguard for multiple REFRESH commands
                if (isMinimumRefreshTimeExceeded()) {
                    // Get the current status from the Airconditioner

                    if (getThing().getStatus() == ThingStatus.OFFLINE) {
                        // try to re-initialize thing access
                        logger.debug("{}: Re-initialize device", getThing().getUID().getId());
                        initializeThing();
                        return;
                    }

                    Map<ArgoDeviceSettingType, State> newState = this.deviceApi.get().queryDeviceForUpdatedState();
                    logger.info(newState.toString());
                    updateChannelsFromDevice(newState);
                    apiRetries = 0;

                    // List<Channel> channels = getThing().getChannels();
                    // for (Channel channel : channels) {
                    // publishChannel(channel.getUID());
                    // }

                    // updateStateFromDevice
                    // if (clientSocket.isPresent()) {
                    // device.getDeviceStatus(clientSocket.get());
                    // apiRetries = 0; // the call was successful without an exception
                    // logger.debug("{}: Executing automatic update of values", getThing().getUID().getId());
                    // List<Channel> channels = getThing().getChannels();
                    // for (Channel channel : channels) {
                    // publishChannel(channel.getUID());
                    // }
                    // }
                }
            }
            // catch (GreeException e) {
            // String subcode = "";
            // if (e.getCause() != null) {
            // subcode = " (" + e.getCause().getMessage() + ")";
            // }
            // String message = messages.get("update.exception", e.getMessageString() + subcode);
            // if (getThing().getStatus() == ThingStatus.OFFLINE) {
            // logger.debug("{}: Thing still OFFLINE ({})", getThing().getUID().getId(), message);
            // } else {
            // if (!e.isTimeout()) {
            // logger.info("{}: {}", getThing().getUID().getId(), message);
            // } else {
            // logger.debug("{}: {}", getThing().getUID().getId(), message);
            // }
            //
            // apiRetries++;
            // if (apiRetries > MAX_API_RETRIES) {
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            // apiRetries = 0;
            // }
            // }
            // }
            catch (RuntimeException | ArgoLocalApiCommunicationException e) {
                String message = "Runtime exception on update. Exceeded retries";
                logger.warn("{}: {}", getThing().getUID().getId(), message, e);
                apiRetries++;
                if (apiRetries > MAX_API_RETRIES) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
                    apiRetries = 0;
                }
            }
        };

        if (refreshTask == null) {
            refreshTask = scheduler.scheduleWithFixedDelay(refresher, 0, config.get().refreshInterval,
                    TimeUnit.SECONDS);
            logger.debug("{}: Automatic refresh started ({} second interval)", getThing().getUID().getId(),
                    config.get().refreshInterval);
            forceRefresh = true;
        }
    }

    // public Map<ArgoDeviceSettingType, State> getCurrentStateMap() {
    //
    // }

    private void updateChannelsFromDevice(Map<ArgoDeviceSettingType, State> deviceState) {
        for (Entry<ArgoDeviceSettingType, State> entry : deviceState.entrySet()) {
            String channelName = null;
            switch (entry.getKey()) {
                case ACTIVE_TIMER:
                    channelName = ArgoClimaBindingConstants.CHANNEL_ACTIVE_TIMER;
                    break;
                case ACTUAL_TEMPERATURE:
                    channelName = ArgoClimaBindingConstants.CHANNEL_CURRENT_TEMPERATURE;
                    break;
                case CURRENT_DAY_OF_WEEK:
                    break;
                case CURRENT_TIME:
                    break;
                case DISPLAY_TEMPERATURE_SCALE:
                    break;
                case ECO_MODE:
                    channelName = ArgoClimaBindingConstants.CHANNEL_ECO_MODE;
                    break;
                case ECO_POWER_LIMIT:
                    break;
                case FAN_LEVEL:
                    channelName = ArgoClimaBindingConstants.CHANNEL_FAN_SPEED;
                    break;
                case FILTER_MODE:
                    channelName = ArgoClimaBindingConstants.CHANNEL_FILTER_MODE;
                    break;
                case FLAP_LEVEL:
                    channelName = ArgoClimaBindingConstants.CHANNEL_SWING_MODE;
                    break;
                case I_FEEL_TEMPERATURE:
                    channelName = ArgoClimaBindingConstants.CHANNEL_I_FEEL_ENABLED;
                    break;
                case LIGHT:
                    channelName = ArgoClimaBindingConstants.CHANNEL_DEVICE_LIGHTS;
                    break;
                case MODE:
                    channelName = ArgoClimaBindingConstants.CHANNEL_MODE;
                    // TODO: ModeEX
                    break;
                case NIGHT_MODE:
                    channelName = ArgoClimaBindingConstants.CHANNEL_NIGHT_MODE;
                    break;
                case POWER:
                    channelName = ArgoClimaBindingConstants.CHANNEL_POWER;
                    break;
                case RESET_TO_FACTORY_SETTINGS:
                    break;
                case TARGET_TEMPERATURE:
                    channelName = ArgoClimaBindingConstants.CHANNEL_SET_TEMPERATURE;
                    break;
                case TIMER_0_DELAY_TIME:
                    channelName = ArgoClimaBindingConstants.CHANNEL_DELAY_TIMER;
                    break;
                case TIMER_N_ENABLED_DAYS:
                    // Scheduletimer
                    break;
                case TIMER_N_OFF_TIME:
                    break;
                case TIMER_N_ON_TIME:
                    break;
                case TIMER_TYPE:
                    channelName = ArgoClimaBindingConstants.CHANNEL_ACTIVE_TIMER;
                    break;
                case TURBO_MODE:
                    channelName = ArgoClimaBindingConstants.CHANNEL_TURBO_MODE;
                    break;
                case UNIT_FIRMWARE_VERSION:
                    break;
                default:
                    break;
            }

            if (channelName != null) {
                // TODO and check thing channels?
                // logger.info("Updating {} with {}", channelName, entry.getValue());
                updateState(channelName, entry.getValue());
            }
        }
    }

    private boolean isMinimumRefreshTimeExceeded() {
        long currentTime = Instant.now().toEpochMilli();
        long timeSinceLastRefresh = currentTime - lastRefreshTime;
        if (!forceRefresh && (timeSinceLastRefresh < config.get().refreshInterval * 1000)) {
            return false;
        }
        lastRefreshTime = currentTime;
        return true;
    }

    private void initializeThing() {
        String message = "";
        try {
            // TODO: do a few retries here?
            boolean isAlive = this.deviceApi.get().isReachable();

            if (isAlive) {
                updateStatus(ThingStatus.ONLINE);
                return;
            }
            message = "Device is not alive";
        }

        // if (!clientSocket.isPresent()) {
        // clientSocket = Optional.of(new DatagramSocket());
        // clientSocket.get().setSoTimeout(DATAGRAM_SOCKET_TIMEOUT);
        // }
        // // Find the GREE device
        // deviceFinder.scan(clientSocket.get(), config.ipAddress, false);
        // GreeAirDevice newDevice = deviceFinder.getDeviceByIPAddress(config.ipAddress);
        // if (newDevice != null) {
        // // Ok, our device responded, now let's Bind with it
        // device = newDevice;
        // device.bindWithDevice(clientSocket.get());
        // if (device.getIsBound()) {
        // updateStatus(ThingStatus.ONLINE);
        // return;
        // }
        // }
        //
        // message = messages.get("thinginit.failed");
        // logger.info("{}: {}", thingId, message);
        // } catch (GreeException e) {
        // logger.info("{}: {}", thingId, messages.get("thinginit.exception", e.getMessageString()));
        // }
        // catch (IOException e) {
        // logger.warn("{}: {}", getThing().getUID().getId(), "I/O Error", e);
        // }
        catch (RuntimeException e) {
            logger.warn("{}: {}", getThing().getUID().getId(), "RuntimeException", e);
        }
        // catch (ConnectException e) {
        // // TODO Auto-generated catch block
        // logger.warn("{}: {}", getThing().getUID().getId(), "ConnectException", e);
        // }
        catch (Exception e) {
            // TODO Auto-generated catch block
            logger.warn("{}: {}", getThing().getUID().getId(), "Exception", e);
        }

        if (getThing().getStatus() != ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
        }
        // getThing().set
        this.updateProperties(this.editProperties()); // TODO: wip
        this.updateThingProperties(Map.of(ArgoClimaBindingConstants.PROPERTY_CPU_ID, "something"));
    }

    private void updateThingProperties(Map<String, String> entries) {
        var currentProps = this.editProperties();
        entries.entrySet().stream().forEach(x -> currentProps.put(x.getKey(), x.getValue()));
        this.updateProperties(currentProps);
    }

    private void stopRefreshTask() {
        forceRefresh = false;
        if (refreshTask == null) {
            return;
        }
        ScheduledFuture<?> task = refreshTask;
        if (task != null) {
            task.cancel(true);
        }
        refreshTask = null;
    }

    @Override
    public void dispose() {
        logger.debug("{}: Thing {} is disposing", getThing().getUID().getId(), thing.getUID());
        if (this.deviceApi.isPresent()) {
            // deviceApi.get().close();
            deviceApi = Optional.empty();
        }

        if (this.serverStub != null) {
            this.serverStub.shutdown();
            this.serverStub = null;
        }

        // stopUpdateTask();
        stopRefreshTask();
        if (initializeFuture != null) {
            initializeFuture.cancel(true);
        }

        cancelPendingSettingsUpdateJob();
        // sendCommandToDeviceFuture.ifPresent(x -> x.cancel(true));
        // sendCommandToDeviceFuture = Optional.empty();
    }

}
