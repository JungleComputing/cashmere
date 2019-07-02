package ibis.cashmere.constellation;

/*
 * Administration of different kinds of devices
 */
public class DeviceInfo {
    private final String name;
    private final int speed;
    private final String nickName;
    private final long memSize;

    public DeviceInfo(String name, int speed, String nickName, long memSize) {
        this.name = name;
        this.speed = speed;
        this.nickName = nickName;
        this.memSize = memSize;
    }

    @Override
    public String toString() {
        return getNickName();
    }

    public int getSpeed() {
        return speed;
    }

    public String getNickName() {
        return nickName;
    }

    public long getMemSize() {
        return memSize;
    }

    public String getName() {
        return name;
    }
}