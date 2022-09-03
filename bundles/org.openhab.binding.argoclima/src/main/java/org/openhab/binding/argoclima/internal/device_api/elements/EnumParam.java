package org.openhab.binding.argoclima.internal.device_api.elements;

import java.util.EnumSet;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.argoclima.internal.device_api.types.IArgoApiEnum;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

@NonNullByDefault
public class EnumParam<E extends Enum<E> & IArgoApiEnum> extends ArgoApiElementBase {
    private Optional<E> currentValue = Optional.empty();
    private Class<E> cls;

    public EnumParam(Class<E> cls) {
        this.cls = cls;
        this.currentValue = Optional.empty();
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        // TODO Auto-generated method stub
        int rawValue = 0;
        try {
            rawValue = Integer.parseInt(responseValue);
        } catch (NumberFormatException e) {
            throw new RuntimeException(String.format("The value %s is not a valid integer", responseValue), e);
        }

        this.currentValue = this.fromInt(rawValue);

    }

    @SuppressWarnings("null") // TODO
    private Optional<E> fromInt(int value) {
        return EnumSet.allOf(this.cls).stream().filter(p -> p.getIntValue() == value).findFirst();
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get().toString();
        // return currentValue.toString();
    }

    @Override
    protected State getAsState() {
        if (currentValue.isEmpty()) {
            return UnDefType.UNDEF;
        }
        return new StringType(currentValue.get().toString());
    }

    @Override
    protected @Nullable String handleCommandInternal(Command command) {
        if (command instanceof StringType) {
            String newValue = ((StringType) command).toFullString();
            E val = Enum.valueOf(this.cls, newValue);
            if (this.currentValue.isEmpty() || this.currentValue.get().compareTo(val) != 0) {
                this.currentValue = Optional.of(val);
                return Integer.toString(this.currentValue.get().getIntValue());
            }
        }

        return null; // TODO
    }

    // protected @Nullable String handleCommandInternal(Command command) {
    // if (command instanceof QuantityType<?>) {
    // int newValue = ((QuantityType<?>) command).intValue();
    // if (this.currentValue.isEmpty() || this.currentValue.get().intValue() != newValue) {
    // this.currentValue = Optional.of(newValue);
    // }
    // return Integer.toString(this.currentValue.get().intValue());
    // }
    // return null; // TODO
    // }
}
