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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.argoclima.internal.ArgoClimaBindingConstants;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase;
import org.openhab.binding.argoclima.internal.device_api.IArgoClimaDeviceAPI;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
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
public abstract class ArgoClimaHandlerBase<ConfigT extends ArgoClimaConfigurationBase> extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(ArgoClimaHandlerBase.class);
    private Optional<IArgoClimaDeviceAPI> deviceApi = Optional.empty(); // todo
    private Optional<ConfigT> config = Optional.empty();

    private @Nullable ScheduledFuture<?> refreshTask;
    private @Nullable Future<?> initializeFuture;
    private Optional<Future<?>> settingsUpdateFuture = Optional.empty();
    private final int MAX_API_RETRIES = 3;
    private boolean forceRefresh = false;
    private long lastRefreshTime = 0;
    private long apiRetries = 0;
    private final int MAX_UPDATE_RETRIES = 3;

    public ArgoClimaHandlerBase(Thing thing) {
        super(thing);
    }

    protected abstract ConfigT getConfigInternal();

    protected abstract IArgoClimaDeviceAPI initializeDeviceApi(ConfigT config) throws Exception;

    @Override
    public final void initialize() {
        this.config = Optional.of(getConfigInternal());
        Objects.requireNonNull(config);
        logger.info("Running with config: {}", config.get().toString());

        if (config.get().resetToFactoryDefaults) {
            config.get().resetToFactoryDefaults = false;
            var configUpdated = editConfiguration();
            configUpdated.put("resetToFactoryDefaults", false);
            updateConfiguration(configUpdated); // TODO: if using file-based config, this will fail?
            // getThing().set
        }

        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly, i.e. any network access must be done in
        // the background initialization below.
        // Also, before leaving this method a thing status from one of ONLINE, OFFLINE or UNKNOWN must be set. This
        // might already be the real thing status in case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.
        var configValidationError = config.get().validate();
        if (!configValidationError.isEmpty()) {
            var message = "Invalid thing configuration. " + configValidationError;
            logger.warn("{}: {}", getThing().getUID().getId(), message);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
            return;
        }

        try {
            this.deviceApi = Optional.of(initializeDeviceApi(config.get()));
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            logger.warn("Failed to initialize Device API", e1); // TODO: crash
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR, e1.getMessage());
            return;
        }

        // TODO: configure all this

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

    @Override
    public final void handleCommand(ChannelUID channelUID, Command command) {

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
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.ACTUAL_TEMPERATURE, command, channelUID);
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
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.I_FEEL_TEMPERATURE, command, channelUID);
        }
        if (ArgoClimaBindingConstants.CHANNEL_DEVICE_LIGHTS.equals(channelUID.getId())) {
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.LIGHT, command, channelUID);
        }
        if (ArgoClimaBindingConstants.CHANNEL_MODE.equals(channelUID.getId())) {
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.MODE, command, channelUID);
        }
        if (ArgoClimaBindingConstants.CHANNEL_MODE_EX.equals(channelUID.getId())) {
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.MODE, command, channelUID);
        }
        if (ArgoClimaBindingConstants.CHANNEL_NIGHT_MODE.equals(channelUID.getId())) {
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.NIGHT_MODE, command, channelUID);
        }
        if (ArgoClimaBindingConstants.CHANNEL_SET_TEMPERATURE.equals(channelUID.getId())) {
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.TARGET_TEMPERATURE, command, channelUID);
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
    }

    @Override
    public void dispose() {
        logger.debug("{}: Thing {} is disposing", getThing().getUID().getId(), thing.getUID());
        if (this.deviceApi.isPresent()) {
            // deviceApi.get().close(); //TODO:cancel http requests!
            deviceApi = Optional.empty();
        }

        try {
            stopRefreshTask();
        } catch (Exception e) {
            logger.warn("Exception during handler disposal", e);
        }

        try {
            if (initializeFuture != null) {
                initializeFuture.cancel(true);
            }
        } catch (Exception e) {
            logger.warn("Exception during handler disposal", e);
        }

        try {
            cancelPendingSettingsUpdateJob();
        } catch (Exception e) {
            logger.warn("Exception during handler disposal", e);
        }
        logger.debug("{}: Disposed", getThing().getUID().getId());
        // sendCommandToDeviceFuture.ifPresent(x -> x.cancel(true));
        // sendCommandToDeviceFuture = Optional.empty();
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

    private final void startAutomaticRefresh() {
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
                }
            }

            catch (RuntimeException | ArgoLocalApiCommunicationException e) {
                logger.warn("Thing {}. Polling for device-side update for HVAC device failed [{} of {}]. Error=[{}]",
                        getThing().getUID().getId(), // this.deviceApi.get().getIpAddressForDirectCommunication(),
                        apiRetries + 1, MAX_API_RETRIES, e.getMessage()); // TODO: lower to debug
                apiRetries++;
                if (apiRetries >= MAX_API_RETRIES) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Polling for device-side update failed. Unable to communicate with HVAC device"
                                    // + this.deviceApi.get().getIpAddressForDirectCommunication().toString() //TODO:
                                    // restore sth
                                    + ". Retries exceeded");
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

    protected final void updateChannelsFromDevice(Map<ArgoDeviceSettingType, State> deviceState) {
        for (Entry<ArgoDeviceSettingType, State> entry : deviceState.entrySet()) {
            var channelNames = Set.<String>of();
            switch (entry.getKey()) {
                case ACTIVE_TIMER:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_ACTIVE_TIMER);
                    break;
                case ACTUAL_TEMPERATURE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_CURRENT_TEMPERATURE);
                    break;
                case CURRENT_DAY_OF_WEEK:
                    break;
                case CURRENT_TIME:
                    break;
                case DISPLAY_TEMPERATURE_SCALE:
                    break;
                case ECO_MODE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_ECO_MODE);
                    break;
                case ECO_POWER_LIMIT:
                    break;
                case FAN_LEVEL:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_FAN_SPEED);
                    break;
                case FILTER_MODE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_FILTER_MODE);
                    break;
                case FLAP_LEVEL:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_SWING_MODE);
                    break;
                case I_FEEL_TEMPERATURE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_I_FEEL_ENABLED);
                    break;
                case LIGHT:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_DEVICE_LIGHTS);
                    break;
                case MODE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_MODE,
                            ArgoClimaBindingConstants.CHANNEL_MODE_EX);
                    break;
                case NIGHT_MODE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_NIGHT_MODE);
                    break;
                case POWER:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_POWER);
                    break;
                case RESET_TO_FACTORY_SETTINGS:
                    break;
                case TARGET_TEMPERATURE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_SET_TEMPERATURE);
                    break;
                case TIMER_0_DELAY_TIME:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_DELAY_TIMER);
                    break;
                case TIMER_N_ENABLED_DAYS:
                    // Scheduletimer
                    break;
                case TIMER_N_OFF_TIME:
                    break;
                case TIMER_N_ON_TIME:
                    break;
                case TIMER_TYPE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_ACTIVE_TIMER);
                    break;
                case TURBO_MODE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_TURBO_MODE);
                    break;
                case UNIT_FIRMWARE_VERSION:
                    break;
                default:
                    break;
            }

            if (!channelNames.isEmpty()) {
                channelNames.forEach(chnl -> updateState(chnl, entry.getValue()));
                // TODO and check thing channels?
                // logger.info("Updating {} with {}", channelName, entry.getValue());
            }
        }
    }

    private final boolean isMinimumRefreshTimeExceeded() {
        long currentTime = Instant.now().toEpochMilli();
        long timeSinceLastRefresh = currentTime - lastRefreshTime;
        if (!forceRefresh && (timeSinceLastRefresh < config.get().refreshInterval * 1000)) {
            return false;
        }
        lastRefreshTime = currentTime;
        return true;
    }

    private final void initializeThing() {
        String message = "";
        try {
            // TODO: do a few retries here?
            var isAlive = this.deviceApi.get().isReachable();

            if (isAlive.getLeft()) {
                updateStatus(ThingStatus.ONLINE);
                return;
            }
            message = isAlive.getRight();
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

    private final void updateThingProperties(Map<String, String> entries) {
        var currentProps = this.editProperties();
        entries.entrySet().stream().forEach(x -> currentProps.put(x.getKey(), x.getValue()));
        this.updateProperties(currentProps);
    }

    private final synchronized void stopRefreshTask() {
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

    private final synchronized void cancelPendingSettingsUpdateJob() {
        settingsUpdateFuture.ifPresent(x -> {
            if (!x.isDone()) {
                logger.info("Cancelling previous update job");
                x.cancel(true);
            }
        });
        settingsUpdateFuture = Optional.empty();
    }

    private final void sendCommandsToDeviceAwaitConfirmation(boolean forceRefresh) {

        logger.warn("STARTING UPDATES!!!");
        // TODO
        cancelPendingSettingsUpdateJob();

        Supplier<Boolean> fn = () -> {
            try {
                // do a better debounce?
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
                        throw new ArgoLocalApiCommunicationException("Update not confirmed. Value was not set");
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
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                                "Could not control device at IP address x.x.x.x | " + ex.getMessage());
                        // throw new ArgoLocalApiCommunicationException("Failed to send commands to device: " +
                        // e.getMessage(), e);
                        throw new RuntimeException("Device command failed", ex); // TODO: this has no effect
                    }
                }
            }
        };

        synchronized (this) {
            settingsUpdateFuture = Optional.of(scheduler.submit(() -> CompletableFuture.supplyAsync(fn)));
        }
    }

    private final boolean handleIndividualSettingCommand(ArgoDeviceSettingType settingType, Command command,
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
}
