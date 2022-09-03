package org.openhab.binding.argoclima.internal.device_api.elements;

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

@NonNullByDefault
public class FwVersionParam extends ArgoApiElementBase {
    private Optional<String> currentValue = Optional.empty();

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
        if (currentValue.isEmpty()) {
            return UnDefType.UNDEF;
        }
        return new StringType(this.toString());
    }

    @Override
    protected @Nullable String handleCommandInternal(Command command) {
        return null; // TODO
    }
}
