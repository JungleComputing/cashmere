package ibis.cashmere.constellation.deviceAPI;

public interface Pointer {
    // Returns true on de-allocation from device.
    public boolean clean();
}
