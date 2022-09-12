package org.openhab.binding.argoclima.internal.device_api.elements;

import java.util.Optional;

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

@NonNullByDefault
public class TemperatureParam extends ArgoApiElementBase {

    private double minValue;
    private double maxValue;
    private Optional<Double> currentValue = Optional.empty();
    private double step;

    public TemperatureParam(double min, double max, double step) {
        this.minValue = min;
        this.maxValue = max;
        this.step = step;
    }

    public TemperatureParam() {
        this.minValue = Double.NEGATIVE_INFINITY;
        this.maxValue = Double.POSITIVE_INFINITY;
        this.step = 0.01;
    }

    private static State valueToState(Optional<Double> value) {
        if (value.isEmpty()) {
            return UnDefType.UNDEF;
        }
        return new QuantityType<Temperature>(value.get(), SIUnits.CELSIUS);
    }

    // @Override
    // public String toApiSetting() {
    // // TODO Auto-generated method stub
    // return null;
    // }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        int rawValue = 0;
        try {
            rawValue = Integer.parseInt(responseValue);
        } catch (NumberFormatException e) {
            throw new RuntimeException(String.format("The value %s is not a valid integer", responseValue), e);
        }
        // TODO: check range
        this.currentValue = Optional.of(rawValue / 10.0);
    }

    @Override
    public String toString() {
        if (currentValue.isEmpty()) {
            return "???";
        }
        return currentValue.get().toString() + " °C";
    }

    @Override
    protected State getAsState() {
        return valueToState(currentValue);
    }

    @Override
    protected HandleCommandResult handleCommandInternalEx(Command command) {
        if (command instanceof QuantityType<?>) {
            double newValue = ((QuantityType<?>) command).doubleValue();
            if (this.currentValue.isEmpty() || this.currentValue.get().doubleValue() != newValue) {
                var targetValue = Optional.<Double>of(newValue);
                this.currentValue = targetValue;
                return new HandleCommandResult(Integer.toUnsignedString((int) (targetValue.get() * 10.0)),
                        valueToState(targetValue));
            }
            // return Integer.toUnsignedString((int) (this.currentValue.get() * 10.0));
        }
        return new HandleCommandResult(false);
    }
}
