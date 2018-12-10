package ibis.cashmere.constellation.deviceAPI;

import java.nio.Buffer;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.deviceImpl.jcuda.CudaPlatform;
import ibis.cashmere.constellation.deviceImpl.jocl.OpenCLPlatform;

public interface Platform {

    // TODO: add API for sizes?
    public static final int INT_SIZE = 4;
    public static final int FLOAT_SIZE = 4;
    public static final int DOUBLE_SIZE = 8;
    public static final int MEM_SIZE = 8;

    public void initializePlatform(Map<String, List<Device>> devices, Cashmere cashmere);

    public static Platform initializePlatform(Properties p, Map<String, List<Device>> devices, Cashmere cashmere) {
        String platform = p.getProperty("cashmere.platform", "OpenCL");
        Platform platformImpl = null;

        if (platform.equals("OpenCL")) {
            // We may have to do this part with introspection in the future ...
            platformImpl = new OpenCLPlatform();
        }
        if (platform.equals("Cuda")) {
            platformImpl = new CudaPlatform();
        }

        if (platformImpl != null) {
            platformImpl.initializePlatform(devices, cashmere);
            return platformImpl;
        }
        return null;
    }

    public String getSuffix();

    public Pointer toPointer(byte[] a);

    public Pointer toPointer(Buffer b);

    public Pointer toPointer(int[] a);

    public Pointer toPointer(float[] a);

    public Pointer toPointer(double[] a);
}
