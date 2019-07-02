package ibis.cashmere.constellation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface DeviceEvent {

    public static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Device/event");

    public static enum TimeType {
        TIME_START, TIME_END
    };

    public static void retainEvents(DeviceEvent[] events) {
        if (events != null) {
            for (DeviceEvent e : events) {
                e.retain();
            }
        }
    }

    public long getTime(TimeType tp);

    public void retain();

    public void clean();

    public void show(String type);
}
