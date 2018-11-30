package ibis.cashmere.constellation.deviceAPI;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.deviceImpl.jcuda.CudaPlatform;
import ibis.cashmere.constellation.deviceImpl.jocl.OpenCLPlatform;

public interface Platform {

    public void initializePlatform(Map<String, List<Device>> devices, Cashmere cashmere);

    public static Platform initializePlatform(Properties p, Map<String, List<Device>> devices, Cashmere cashmere) {
        String platform = p.getProperty("cashmere.platform", "OpenCL");
        if (platform.equals("OpenCL")) {
            // We may have to do this part with introspection in the future ...
            Platform platformImpl = new OpenCLPlatform();
            platformImpl.initializePlatform(devices, cashmere);
            return platformImpl;
        }
        if (platform.equals("Cuda")) {
            Platform platformImpl = new CudaPlatform();
            platformImpl.initializePlatform(devices, cashmere);
            return platformImpl;

        }
        return null;
    }

    public String getSuffix();

    public Pointer toPointer(byte[] a);

    public Pointer toPointer(int[] a);

    public Pointer toPointer(float[] a);

    public Pointer toPointer(double[] a);
}
