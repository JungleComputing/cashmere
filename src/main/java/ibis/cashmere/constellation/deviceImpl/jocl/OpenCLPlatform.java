package ibis.cashmere.constellation.deviceImpl.jocl;

import static org.jocl.CL.CL_DEVICE_TYPE_ALL;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetPlatformIDs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jocl.CL;
import org.jocl.cl_device_id;
import org.jocl.cl_platform_id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.deviceAPI.Device;
import ibis.cashmere.constellation.deviceAPI.Platform;
import ibis.cashmere.constellation.deviceAPI.Pointer;

public class OpenCLPlatform implements Platform {

    private static final Logger loggerOpenCL = LoggerFactory.getLogger("ibis.cashmere.constellation.Cashmere/OpenCL");

    public void initializePlatform(Map<String, List<Device>> devices, Cashmere cashmere) {
        try {
            CL.setExceptionsEnabled(true);

            cl_platform_id[] platforms = getPlatforms();

            if (loggerOpenCL.isInfoEnabled()) {
                loggerOpenCL.info("Found {} platform(s):", platforms.length);
                for (int i = 0; i < platforms.length; i++) {
                    loggerOpenCL.info(OpenCLInfo.getName(platforms[i]));
                }
            }

            getDevices(platforms, devices, cashmere);
        } catch (Throwable e) {
            loggerOpenCL.warn("Could not initialize OpenCL", e);
        }
    }

    @Override
    public Pointer toPointer(byte[] a) {
        return new CLPointer(a);
    }

    @Override
    public Pointer toPointer(int[] a) {
        return new CLPointer(a);
    }

    @Override
    public Pointer toPointer(float[] a) {
        return new CLPointer(a);
    }

    @Override
    public Pointer toPointer(double[] a) {
        return new CLPointer(a);
    }

    public String getSuffix() {
        return ".cl";
    }

    private cl_platform_id[] getPlatforms() {
        // In some cases, Intel publishes two platforms with the same device
        // The only difference in the platform information is the version: OpenCL 1.2 or OpenCL 1.2 LINUX.
        // This method filters one of the platforms

        ArrayList<cl_device_id> devicesSeen = new ArrayList<cl_device_id>();
        ArrayList<cl_platform_id> platformsExcluded = new ArrayList<cl_platform_id>();
        ArrayList<cl_platform_id> platforms = new ArrayList<cl_platform_id>();

        platforms.addAll(Arrays.asList(getPlatformIDs()));

        for (cl_platform_id platform : platforms) {
            cl_device_id[] devices = getDeviceIDs(platform);

            for (cl_device_id device : devices) {
                if (devicesSeen.contains(device)) {
                    platformsExcluded.add(platform);
                    if (loggerOpenCL.isInfoEnabled()) {
                        loggerOpenCL.info("Excluding platform {}", OpenCLInfo.getName(platform));
                    }
                }
                devicesSeen.add(device);
            }
        }

        platforms.removeAll(platformsExcluded);
        cl_platform_id[] array = new cl_platform_id[platforms.size()];

        return platforms.toArray(array);
    }

    private cl_platform_id[] getPlatformIDs() {
        int numPlatformsArray[] = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);

        return platforms;
    }

    private cl_device_id[] getDeviceIDs(cl_platform_id platform) {
        int numDevicesArray[] = new int[1];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        cl_device_id[] device_ids = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, numDevices, device_ids, null);

        return device_ids;
    }

    private void getDevices(cl_platform_id[] platforms, Map<String, List<Device>> devices, Cashmere cashmere) {
        for (cl_platform_id platform : platforms) {
            List<Device> list = getDevicesPlatform(platform, cashmere);
            for (Device device : list) {
                List<Device> l = devices.get(device.getName());
                if (l == null) {
                    l = new ArrayList<Device>();
                    devices.put(device.getName(), l);
                }
                l.add(device);
            }
        }
    }

    private ArrayList<Device> getDevicesPlatform(cl_platform_id platform, Cashmere cashmere) {

        ArrayList<Device> devices = new ArrayList<Device>();

        cl_device_id[] device_ids = getDeviceIDs(platform);

        for (cl_device_id device : device_ids) {
            Device d = new Device(device, platform, cashmere);
            if (!d.getName().equals("unknown")) {
                devices.add(d);
            }
        }

        return devices;
    }

}
