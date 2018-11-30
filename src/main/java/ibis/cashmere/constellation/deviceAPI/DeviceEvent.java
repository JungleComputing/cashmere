package ibis.cashmere.constellation.deviceAPI;

public interface DeviceEvent {
    public static enum TimeType {
        TIME_QUEUED, TIME_START, TIME_END
    };

    public long getTime(TimeType tp);

}
