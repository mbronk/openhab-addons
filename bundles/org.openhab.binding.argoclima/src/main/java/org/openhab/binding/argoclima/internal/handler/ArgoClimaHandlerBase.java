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
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.argoclima.internal.ArgoClimaBindingConstants;
import org.openhab.binding.argoclima.internal.configuration.ArgoClimaConfigurationBase;
import org.openhab.binding.argoclima.internal.device_api.IArgoClimaDeviceAPI;
import org.openhab.binding.argoclima.internal.device_api.types.ArgoDeviceSettingType;
import org.openhab.binding.argoclima.internal.exception.ArgoConfigurationException;
import org.openhab.binding.argoclima.internal.exception.ArgoLocalApiCommunicationException;
import org.openhab.core.library.types.OnOffType;
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
    // private boolean forceRefresh = false;
    private long lastRefreshTime = 0;
    private long apiRetries = 0;
    // private final int MAX_UPDATE_RETRIES = 3;
    private final boolean SEND_COMMAND_AWAIT_CONFIRMATION; // = true;
    private final Duration SEND_COMMAND_STATUS_POLL_FREQUENCY; // = Duration.ofSeconds(3);
    private final Duration SEND_COMMAND_RESUBMIT_FREQUENCY; // = Duration.ofSeconds(10);
    private final Duration SEND_COMMAND_MAX_WAIT_TIME; // = Duration.ofSeconds(30);

    public ArgoClimaHandlerBase(Thing thing, boolean awaitConfirmationAfterSend, Duration poolFrequencyAfterSend,
            Duration sendRetryFrequency, Duration sendMaxRetryTime) {
        super(thing);
        this.SEND_COMMAND_AWAIT_CONFIRMATION = awaitConfirmationAfterSend;
        this.SEND_COMMAND_STATUS_POLL_FREQUENCY = poolFrequencyAfterSend;
        this.SEND_COMMAND_RESUBMIT_FREQUENCY = sendRetryFrequency;
        this.SEND_COMMAND_MAX_WAIT_TIME = sendMaxRetryTime;
    }

    protected abstract ConfigT getConfigInternal() throws ArgoConfigurationException;

    protected abstract IArgoClimaDeviceAPI initializeDeviceApi(ConfigT config) throws Exception;

    @Override
    public final void initialize() {
        try {
            this.config = Optional.of(getConfigInternal());
            Objects.requireNonNull(config);
        } catch (ArgoConfigurationException | NullPointerException ex) {
            logger.warn("{}: {}", getThing().getUID().getId(), ex.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, ex.getMessage());
            return;
        }
        logger.info("Running with config: {}", config.get().toString());

        // if (config.get().resetToFactoryDefaults) {
        // config.get().resetToFactoryDefaults = false;
        // var configUpdated = editConfiguration();
        // configUpdated.put("resetToFactoryDefaults", false);
        // updateConfiguration(configUpdated); // TODO: if using file-based config, this will fail?
        // // getThing().set
        // }

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
            lastRefreshTime = Instant.now().toEpochMilli(); // Skips 1st refresh cycle (no need, initializer will do it
                                                            // instead)
            startAutomaticRefresh();
        }
        initializeFuture = scheduler.submit(this::initializeThing);

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
                                                         // one go)
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

        if (ArgoClimaBindingConstants.CHANNEL_TURBO_MODE.equals(channelUID.getId())) {
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.TURBO_MODE, command, channelUID);
        }

        if (ArgoClimaBindingConstants.CHANNEL_TEMPERATURE_DISPLAY_UNIT.equals(channelUID.getId())) {
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.DISPLAY_TEMPERATURE_SCALE, command,
                    channelUID);
        }
        if (ArgoClimaBindingConstants.CHANNEL_ECO_POWER_LIMIT.equals(channelUID.getId())) {
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.ECO_POWER_LIMIT, command, channelUID);
        }

        if (ArgoClimaBindingConstants.CHANNEL_DELAY_TIMER.equals(channelUID.getId())) {
            hasUpdates |= handleIndividualSettingCommand(ArgoDeviceSettingType.TIMER_0_DELAY_TIME, command, channelUID);
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

                    updateStateFromDevice(false);
                    // Map<ArgoDeviceSettingType, State> newState = this.deviceApi.get().queryDeviceForUpdatedState();
                    // // logger.info(newState.toString());
                    // updateChannelsFromDevice(newState);
                    // updateThingProperties(this.deviceApi.get().getCurrentDeviceProperties());
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
                                    + ". Retries exceeded. | " + e.getMessage());
                    apiRetries = 0;
                }
            }
        };

        if (refreshTask == null) {
            refreshTask = scheduler.scheduleWithFixedDelay(refresher, 0, config.get().refreshInterval,
                    TimeUnit.SECONDS);
            logger.debug("{}: Automatic refresh started ({} second interval)", getThing().getUID().getId(),
                    config.get().refreshInterval);
            // forceRefresh = true;
        }
    }

    // public Map<ArgoDeviceSettingType, State> getCurrentStateMap() {
    //
    // }

    protected final void updateChannelsFromDevice(Map<ArgoDeviceSettingType, State> deviceState) {
        for (Entry<ArgoDeviceSettingType, State> entry : deviceState.entrySet()) {
            var channelNames = Set.<String> of();
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
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_TEMPERATURE_DISPLAY_UNIT);
                    break;
                case ECO_MODE:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_ECO_MODE);
                    break;
                case ECO_POWER_LIMIT:
                    channelNames = Set.of(ArgoClimaBindingConstants.CHANNEL_ECO_POWER_LIMIT);
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

            // Initialize write-only channels to default value
            // if (isLinked(ArgoClimaBindingConstants.CHANNEL_DELAY_TIMER)) {
            // // TODO
            // var delayTimerValue = deviceState.get(ArgoDeviceSettingType.TIMER_0_DELAY_TIME);
            // if (delayTimerValue == null) {
            // delayTimerValue = new QuantityType<Time>(10, Units.MINUTE);
            // // var x = new QuantityType<Time>(10, Units.MINUTE);
            // // delayTimerValue = new org.openhab.core.library.types.DecimalType(10);
            // // TODO: set it in the value, not here !!
            // }
            // updateState(ArgoClimaBindingConstants.CHANNEL_DELAY_TIMER, delayTimerValue);
            // }
            // thing.getChannel(ArgoClimaBindingConstants.CHANNEL_DELAY_TIMER).

            //
        }
    }

    private final void updateStateFromDevice(boolean useCachedState) throws ArgoLocalApiCommunicationException {
        var newState = (useCachedState) ? this.deviceApi.get().getLastStateReadFromDevice()
                : this.deviceApi.get().queryDeviceForUpdatedState();
        // logger.info(newState.toString());
        updateChannelsFromDevice(newState);
        updateThingProperties(this.deviceApi.get().getCurrentDeviceProperties());
    }

    private final boolean isMinimumRefreshTimeExceeded() {
        long currentTime = Instant.now().toEpochMilli();
        long timeSinceLastRefresh = currentTime - lastRefreshTime;
        if (// !forceRefresh &&
        (timeSinceLastRefresh < config.get().refreshInterval * 1000)) {
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
                // this.updateThingProperties(Map.of(ArgoClimaBindingConstants.PROPERTY_CPU_ID, "something"));
                updateStateFromDevice(true);
                // var newState = this.deviceApi.get().getLastStateReadFromDevice();
                // // logger.info(newState.toString());
                // updateChannelsFromDevice(newState);
                // updateThingProperties(this.deviceApi.get().getCurrentDeviceProperties());

                if (config.get().resetToFactoryDefaults) {
                    var resetSent = this.deviceApi.get()
                            .handleSettingCommand(ArgoDeviceSettingType.RESET_TO_FACTORY_SETTINGS, OnOffType.ON);
                    if (resetSent) {
                        State currentState = this.deviceApi.get()
                                .getCurrentStateNoPoll(ArgoDeviceSettingType.RESET_TO_FACTORY_SETTINGS);
                        logger.info("State of {} after update: {}", "RESET", currentState);
                        // updateState(channelUID, currentState); // TODO: assume new state
                        sendCommandsToDeviceAwaitConfirmation(false);

                        config.get().resetToFactoryDefaults = false;
                        var configUpdated = editConfiguration();
                        configUpdated.put("resetToFactoryDefaults", false);
                        updateConfiguration(configUpdated); // TODO: if using file-based config, this will fail?
                    }

                    // getThing().set
                }

                // boolean updateInitiated = this.deviceApi.get().handleSettingCommand(settingType, command);
                // if (updateInitiated) {
                // State currentState = this.deviceApi.get().getCurrentStateNoPoll(settingType);
                // logger.info("State of {} after update: {}", channelUID, currentState);
                // updateState(channelUID, currentState); // TODO: assume new state
                // }

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
        // this.updateProperties(this.editProperties()); // TODO: wip
        // this.updateThingProperties(Map.of(ArgoClimaBindingConstants.PROPERTY_CPU_ID, "something"));
    }

    protected final void updateThingProperties(Map<String, String> entries) {
        var currentProps = this.editProperties();
        entries.entrySet().stream().forEach(x -> currentProps.put(x.getKey(), x.getValue()));
        this.updateProperties(currentProps);
    }

    private final synchronized void stopRefreshTask() {
        // forceRefresh = false;
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
                logger.trace("Cancelling previous update job");
                x.cancel(true);
            }
        });
        settingsUpdateFuture = Optional.empty();
    }

    private final void sendCommandsToDeviceAwaitConfirmation(boolean forceRefresh) {
        if (SEND_COMMAND_STATUS_POLL_FREQUENCY.isNegative() || SEND_COMMAND_RESUBMIT_FREQUENCY.isNegative()
                || SEND_COMMAND_MAX_WAIT_TIME.isNegative()) {
            throw new IllegalArgumentException("The frequency cannot be negative");
        }
        // TODO: paramcheck (duration > 0)

        // logger.warn("STARTING UPDATES!!! Refresh forced: {}", forceRefresh);
        // TODO

        // Supplier<Boolean> fn = () -> {
        Runnable fn = () -> {
            // logger.info("I will be doing stuff in just a sec");
            try {
                // do a better debounce?
                Thread.sleep(100); // naive debounce (not to overflow the device if multiple commands are sent at
                                   // once)
            } catch (InterruptedException e) {
                // logger.warn("Got interrupted");
                return;// false;
            }
            var valuesToUpdate = this.deviceApi.get().getItemsWithPendingUpdates();
            // logger.info("Will UPDATE the following items: {}", valuesToUpdate);

            // int attempt = 0;

            // Instant lastCommandSendTime = Instant.MIN;
            // // Instant lastStatusRefreshTime = Instant.MIN;

            var triesEndTime = Instant.now().plus(SEND_COMMAND_MAX_WAIT_TIME);

            var nextCommandSendTime = Instant.MIN; // 1st send is instant
            var nextStateUpdateTime = Instant.now().plus(SEND_COMMAND_STATUS_POLL_FREQUENCY); // 1st poll is delayed

            Optional<Exception> lastException = Optional.empty();
            while (true) { // Handles both polling as well as retries
                // attempt++;
                try {
                    if (Instant.now().isAfter(nextCommandSendTime)) {
                        nextCommandSendTime = Instant.now().plus(SEND_COMMAND_RESUBMIT_FREQUENCY);
                        if (!this.deviceApi.get().hasPendingCommands()) {
                            if (forceRefresh) {
                                updateStateFromDevice(false);
                                // var newState = this.deviceApi.get().queryDeviceForUpdatedState(); // no updates but
                                // // refresh was forced
                                // // TODO - shouldn't this do updateChannelsFromDevice(newState);
                                // updateChannelsFromDevice(newState);
                                // updateThingProperties(this.deviceApi.get().getCurrentDeviceProperties());
                            } else {
                                logger.debug("Nothing to update... skipping"); // update might have occured async
                            }
                            return; // false; // no update made
                        }

                        this.deviceApi.get().sendCommandsToDevice();

                        if (!this.deviceApi.get().hasPendingCommands()) {
                            logger.info("ALL UPDATED ON 1st try!!");
                            return; // true;
                        }
                    }

                    if (!SEND_COMMAND_AWAIT_CONFIRMATION) {
                        return; // Nothing to do
                    }

                    if (Instant.now().isAfter(nextStateUpdateTime)) {
                        nextStateUpdateTime = Instant.now().plus(SEND_COMMAND_STATUS_POLL_FREQUENCY);// Note: the device
                                                                                                     // takes
                        // long to process
                        // commands. Giving it a few sec. before
                        // re-confirming
                        updateStateFromDevice(false);
                        // this.deviceApi.get().queryDeviceForUpdatedState();
                        if (this.deviceApi.get().hasPendingCommands()) {
                            throw new ArgoLocalApiCommunicationException("Update not confirmed. Value was not set");
                        }
                        return; // true; ALL A-OK
                    }
                    // empty loop cycle (no command, no update)...
                } catch (Exception ex) {
                    lastException = Optional.of(ex);
                }

                if (Instant.now().isBefore(triesEndTime)) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return; // Cancelled
                    }
                    logger.debug("Failed to update. Will retry...");
                    continue;
                }

                // Max time exceeded and update failed to send or not confirmed. Giving up.

                valuesToUpdate.stream().forEach(x -> x.abortPendingCommand());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Could not control device at IP address x.x.x.x");
                // throw new ArgoLocalApiCommunicationException("Failed to send commands to device: " +
                // e.getMessage(), e);
                // throw new RuntimeException("Device command failed", ex); // TODO: this has no effect -> log
                // it as warn!
                logger.warn("[{}] Device command failed: {}", this.getThing().getUID().toString(),
                        lastException.map(ex -> ex.getMessage()).orElse("No error details"));
                break;
            }
        };

        // Supplier<Boolean> fn2 = () -> {
        // logger.info("Before sleep");
        // try {
        // Thread.sleep(4000);
        // } catch (InterruptedException e) {
        // logger.info("Cancelled");
        // return false;
        // }
        // logger.info("****************************************************************** DID NASTY STUFF ***");
        // return true;
        // };
        // Runnable fn3 = () -> {
        // logger.info("Before sleep");
        // try {
        // Thread.sleep(4000);
        // } catch (InterruptedException e) {
        // logger.info("Cancelled");
        // return;
        // }
        // logger.info("****************************************************************** DID NASTY STUFF ***");
        // };

        synchronized (this) {
            cancelPendingSettingsUpdateJob(); // does it even work?!
            // settingsUpdateFuture = Optional.of(scheduler.submit(() -> CompletableFuture.supplyAsync(fn)));
            // settingsUpdateFuture = Optional.of(CompletableFuture.supplyAsync(fn, scheduler));
            // settingsUpdateFuture = Optional.of(CompletableFuture.supplyAsync(fn2, scheduler));
            settingsUpdateFuture = Optional.of(scheduler.submit(fn));

            // settingsUpdateFuture = Optional.of(scheduler.submit(this::removeme));
        }
    }
    //
    // private void removeme() {
    // logger.info("Before sleep");
    // try {
    // Thread.sleep(4000);
    // } catch (InterruptedException e) {
    // logger.info("Cancelled");
    // return;
    // }
    // logger.info("****************************************************************** DID NASTY STUFF ***");
    // };

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
