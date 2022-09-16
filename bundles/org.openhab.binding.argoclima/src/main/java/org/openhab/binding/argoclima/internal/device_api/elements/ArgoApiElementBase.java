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
package org.openhab.binding.argoclima.internal.device_api.elements;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Mateusz Bronk - Initial contribution
 */
@NonNullByDefault
public abstract class ArgoApiElementBase implements IArgoElement {
    protected static final String NO_VALUE = "N";
    private final Logger logger = LoggerFactory.getLogger(ArgoApiElementBase.class);

    private @Nullable String lastRawValueFromDevice;
    private Optional<State> lastStateConfirmedByDevice = Optional.empty();
    // private @Nullable String targetRawValue;
    private Optional<HandleCommandResult> lastCommandResult = Optional.empty();

    @Override
    public State updateFromApiResponse(String responseValue) {
        if (this.isUpdatePending()) {
            if (responseValue.equals(this.lastCommandResult.get().deviceCommandToSend.get())) { // todo: compare by
                                                                                                // stete
                logger.info("Update confirmed!");
                this.lastCommandResult = Optional.empty();
                // this.targetRawValue = null;
            } else if (this.lastCommandResult.get().isMinimumRefreshTimeExceeded()) {
                logger.warn("Long-pending update found. Cancelling...");
                this.lastCommandResult = Optional.empty();
            } else {
                logger.warn("Update made, but values mismatch... {} != {}", responseValue,
                        this.lastCommandResult.get().deviceCommandToSend.get());
            }
        }
        // TODO Auto-generated method stub

        this.lastRawValueFromDevice = responseValue;
        this.updateFromApiResponseInternal(responseValue);
        this.lastStateConfirmedByDevice = Optional.of(this.getAsState());
        return this.getAsState();
    }

    @Override
    public void abortPendingCommand() {
        // this.targetRawValue = null;
        this.lastCommandResult = Optional.empty();
    }

    protected abstract void updateFromApiResponseInternal(String responseValue);

    protected abstract State getAsState();

    @Override
    public State toState() { // TODO: unnecessary alias
        return this.getAsState();
    }

    // protected void updateState(String channelID, State state) {
    // ChannelUID channelUID = new ChannelUID(this.getThing().getUID(), channelID);
    // updateState(channelUID, state);
    // }

    // @Override
    // public String toApiSetting() {
    // return NO_VALUE; // to be overridden in derived class
    // }

    protected static int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new RuntimeException(String.format("The value %s is not a valid integer", value), e);
        }
    }

    @Override
    public String toString() {
        return String.format("RAW[%s]", lastRawValueFromDevice);
    }

    @Override
    public State getLastStateFromDevice() {
        return this.lastStateConfirmedByDevice.orElse(UnDefType.UNDEF); // TODO: yagni?
    }

    // @Override
    // public State getCurentState() {
    // return this.getAsState();
    // }

    @Override
    public boolean isUpdatePending() {
        // logger.info("Is update pending: CURRENT_STATE=[{}], LAST_STATE_FROM_DEVICE=[{}], PLANNED_STATE=[{}]",
        // toState(),
        // getLastStateFromDevice(), this.lastCommandResult);

        return this.lastCommandResult.isPresent() && this.lastCommandResult.get().handled
                && this.lastCommandResult.get().deviceCommandToSend.get() != lastRawValueFromDevice;
    }

    @Override
    public String getDeviceApiValue() {
        if (!isUpdatePending()) {
            return NO_VALUE;
        }
        return this.lastCommandResult.get().deviceCommandToSend.get();
    }

    public class HandleCommandResult {
        public final boolean handled;
        public final Optional<String> deviceCommandToSend;
        public final Optional<State> plannedState;
        private final long updateRequestedTime;
        private final long UPDATE_EXPIRE_TIME_MS = 120000;

        public boolean isMinimumRefreshTimeExceeded() {
            return (Instant.now().toEpochMilli() - updateRequestedTime) > UPDATE_EXPIRE_TIME_MS;
        }

        public HandleCommandResult(boolean handled) {
            if (handled) {
                throw new RuntimeException("This c-tor should be used for rejected only");
            }
            this.updateRequestedTime = Instant.now().toEpochMilli();
            this.handled = false;
            this.deviceCommandToSend = Optional.empty();
            this.plannedState = Optional.empty();
        }

        public HandleCommandResult(String deviceCommandToSend, State plannedState) {
            this.updateRequestedTime = Instant.now().toEpochMilli();
            this.handled = true;
            this.deviceCommandToSend = Optional.of(deviceCommandToSend);
            this.plannedState = Optional.of(plannedState);
        }

        @Override
        public String toString() {
            return String.format("HandleCommandResult(wasHandled=%s,deviceCommand=%s,plannedState=%s,isObsolete=%s)",
                    handled, deviceCommandToSend, plannedState, isMinimumRefreshTimeExceeded());
        }
    }

    @Override
    public boolean handleCommand(Command command) {
        var result = this.handleCommandInternalEx(command);
        if (result.handled) {
            this.lastCommandResult = Optional.of(result);
            // this.targetRawValue = result.deviceCommandToSend.get();
            return true;
        }
        return false;
        //
        // String newRawValue = this.handleCommandInternal(command);
        // if (newRawValue != null) {
        // this.targetRawValue = newRawValue; // do not touch otherwise
        // return true;
        // }
        // // this.updatePending = false;
        // return false; // TODO
    }

    /**
     *
     * @param command
     * @return Raw value to be sent to the device or null if no update needed
     */
    // protected abstract @Nullable String handleCommandInternal(Command command);

    protected abstract HandleCommandResult handleCommandInternalEx(Command command);
}
