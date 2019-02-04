package ibis.cashmere.constellation.deviceAPI;

/**
 * Abstraction for a device or host pointer.
 */
public interface Pointer {

    /**
     * Deallocation of device pointer.
     * 
     * @return <code>true</code> when in fact de-allocated.
     */
    public boolean clean();
}
