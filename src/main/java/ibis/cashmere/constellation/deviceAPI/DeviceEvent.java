package ibis.cashmere.constellation.deviceAPI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface DeviceEvent {

    public static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.deviceAPI.DeviceEvent");

    public static enum TimeType {
        TIME_START, TIME_END
    };

    public long getTime(TimeType tp);

    public void retain();

}
