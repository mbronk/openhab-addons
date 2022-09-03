package org.openhab.binding.argoclima.internal.device_api.elements;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;

@NonNullByDefault
public interface IArgoElement {

    public State getLastStateFromDevice();

    public State getCurentState();

    public boolean isUpdatePending();

    public String getDeviceApiValue();

    public State updateFromApiResponse(String responseValue);

    public String toApiSetting();

    public State toState();

    // returns value state
    public boolean handleCommand(Command command);

    @Override
    public String toString();
}
