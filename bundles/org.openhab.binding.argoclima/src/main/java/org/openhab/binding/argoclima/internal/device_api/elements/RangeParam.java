package org.openhab.binding.argoclima.internal.device_api.elements;

import java.util.Optional;

import javax.measure.quantity.Dimensionless;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

//@NonNullByDefault
public class RangeParam<T extends Number & Comparable<Integer>> extends ArgoApiElementBase {

    private Optional<@NonNull Number> currentValue = Optional.empty();

    private T minValue;
    private T maxValue;

    public RangeParam(T min, T max) {
        this.minValue = min;
        this.maxValue = max;
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        // TODO: if double then ?
        currentValue = Optional.of(toInt(responseValue));
        // if (this.minValue.compareTo(minValue) > 1) {
        //
        // }
        // TODO Auto-generated method stub

    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get().toString();
    }

    @Override
    protected State getAsState() {
        if (currentValue.isEmpty()) {
            return UnDefType.UNDEF;
        }
        return new QuantityType<Dimensionless>(currentValue.get(), Units.PERCENT);
    }

    @Override
    protected @Nullable String handleCommandInternal(Command command) {
        if (command instanceof QuantityType<?>) {
            int newValue = ((QuantityType<?>) command).intValue();
            if (this.currentValue.isEmpty() || this.currentValue.get().intValue() != newValue) {
                this.currentValue = Optional.of(newValue);
            }
            return Integer.toString(this.currentValue.get().intValue());
        }
        return null; // TODO
    }
}
