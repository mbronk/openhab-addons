package org.openhab.binding.argoclima.internal.device_api.elements;

import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public abstract class ArgoApiElementBase implements IArgoElement {
    protected static final String NO_VALUE = "N";
    private final Logger logger = LoggerFactory.getLogger(ArgoApiElementBase.class);

    private @Nullable String currentRawValue;
    private @Nullable String targetRawValue;
    private boolean updatePending = false;

    @Override
    public State updateFromApiResponse(String responseValue) {
        if (this.isUpdatePending()) {
            if (responseValue.equals(this.targetRawValue)) {
                // TODO logger
                this.updatePending = false;
                this.targetRawValue = null;
            } else {
                logger.warn("Update made, but values mismatch... {} != {}", responseValue, this.targetRawValue);
            }
        }
        // TODO Auto-generated method stub

        this.currentRawValue = responseValue;
        this.updateFromApiResponseInternal(responseValue);
        return this.getAsState();
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

    @Override
    public String toApiSetting() {
        return NO_VALUE; // to be overridden in derived class
    }

    protected static int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new RuntimeException(String.format("The value %s is not a valid integer", value), e);
        }
    }

    @Override
    public String toString() {
        return String.format("RAW[%s]", currentRawValue);
    }

    @Override
    public State getLastStateFromDevice() {
        return this.getAsState(); // TODO: yagni?
    }

    @Override
    public State getCurentState() {
        return this.getAsState();
    }

    @Override
    public boolean isUpdatePending() {
        // return this.updatePending; // TODO
        return this.targetRawValue != null;
    }

    @Override
    public String getDeviceApiValue() {
        if (!isUpdatePending() || this.targetRawValue == null) {
            return NO_VALUE;
        }
        return ObjectUtils.defaultIfNull(this.targetRawValue, ""); // TODO: null
        // return NO_VALUE;// TODO
    }

    @Override
    public boolean handleCommand(Command command) {
        String newRawValue = this.handleCommandInternal(command);
        if (newRawValue != null) {
            this.targetRawValue = newRawValue; // do not touch otherwise
            this.updatePending = true;
            return true;
        }
        // this.updatePending = false;
        return false; // TODO
    }

    /**
     *
     * @param command
     * @return Raw value to be sent to the device or null if no update needed
     */
    protected abstract @Nullable String handleCommandInternal(Command command);
}
