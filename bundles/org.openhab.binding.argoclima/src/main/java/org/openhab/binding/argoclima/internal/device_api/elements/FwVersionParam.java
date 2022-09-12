package org.openhab.binding.argoclima.internal.device_api.elements;

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

@NonNullByDefault
public class FwVersionParam extends ArgoApiElementBase {
    private Optional<String> currentValue = Optional.empty();

    private static State valueToState(Optional<String> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }
        return new StringType("0" + value.get());
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        this.currentValue = Optional.of(responseValue);
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return "0" + currentValue.get();
        // return currentValue.toString();
    }

    @Override
    protected State getAsState() {
        return valueToState(currentValue);
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        return new HandleCommandResult(false);
    }
}
