package ibis.cashmere.constellation;

import java.nio.Buffer;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.deviceImpl.jcuda.CudaPlatform;
import ibis.cashmere.constellation.deviceImpl.jocl.OpenCLPlatform;

public interface Platform {

    /*
     * loggers
     */
    public static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Platform");


    // TODO: add API for sizes?
    public static final int INT_SIZE = 4;
    public static final int FLOAT_SIZE = 4;
    public static final int DOUBLE_SIZE = 8;
    public static final int MEM_SIZE = 8;

    /**
     * Initializes the platform.
     * 
     * @param devices
     *            all devices are added here.
     * @param cashmere
     *            the cashmere instance.
     */
    public void initializePlatform(Map<String, List<Device>> devices, Cashmere cashmere);

    public static Platform initializePlatform(Properties p, Map<String, List<Device>> devices, Cashmere cashmere) {
        String platform = p.getProperty("cashmere.platform", "opencl");
        Platform platformImpl = null;

        if (platform.equals("opencl")) {
            // We may have to do this part with introspection in the future ...
            platformImpl = new OpenCLPlatform();
        }
        if (platform.equals("cuda")) {
            platformImpl = new CudaPlatform();
        }

        if (platformImpl != null) {
            platformImpl.initializePlatform(devices, cashmere);
            return platformImpl;
        }
        return null;
    }

    /**
     * Returns the suffix of the platform at hand, i.e. ".cl" for OpenCL, ".cu" for Cuda.
     * 
     * @return the suffix
     */
    public String getSuffix();

    /**
     * Creates a new Pointer to the given values.
     * 
     * @param a
     *            the values.
     * @return the pointer.
     */
    public Pointer toPointer(byte[] a);

    /**
     * Creates a new Pointer to the given buffer.
     * 
     * @param b
     *            the buffer.
     * @return the pointer.
     */
    public Pointer toPointer(Buffer b);

    /**
     * Creates a new Pointer to the given values.
     * 
     * @param a
     *            the values.
     * @return the pointer.
     */
    public Pointer toPointer(int[] a);

    /**
     * Creates a new Pointer to the given values.
     * 
     * @param a
     *            the values.
     * @return the pointer.
     */
    public Pointer toPointer(float[] a);

    /**
     * Creates a new Pointer to the given values.
     * 
     * @param a
     *            the values.
     * @return the pointer.
     */
    public Pointer toPointer(double[] a);
}
