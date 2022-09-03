package org.openhab.binding.argoclima.internal.device_api.elements;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

@NonNullByDefault
public class TimeParam extends ArgoApiElementBase {

    private Optional<LocalTime> currentValue = Optional.empty();

    private int minValue;
    private int maxValue;

    public TimeParam() {
        this.minValue = fromHhMm(0, 0);
        this.maxValue = fromHhMm(23, 59);
    }

    public TimeParam(int minValue, int maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public static int fromHhMm(int hour, int minute) {
        // TODO assertions
        return hour * 60 + minute;
    }

    @Override
    protected void updateFromApiResponseInternal(String responseValue) {
        // TODO Auto-generated method stub
        int raw = toInt(responseValue);
        int hh = Math.floorDiv(raw, 60);
        int mm = raw - hh;

        this.currentValue = Optional.of(LocalTime.of(hh, mm));
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
        // todo this is sketchy
        return new DateTimeType(ZonedDateTime.of(LocalDate.now(), currentValue.get(), ZoneId.systemDefault()));
    }

    @Override
    protected @Nullable String handleCommandInternal(Command command) {
        return null; // TODO
    }

}
