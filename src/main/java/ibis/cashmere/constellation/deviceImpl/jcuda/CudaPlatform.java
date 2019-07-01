package ibis.cashmere.constellation.deviceImpl.jcuda;

import static jcuda.driver.JCudaDriver.cuDeviceGet;
import static jcuda.driver.JCudaDriver.cuDeviceGetCount;
import static jcuda.driver.JCudaDriver.cuInit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.deviceAPI.Device;
import ibis.cashmere.constellation.deviceAPI.Platform;
import ibis.cashmere.constellation.deviceAPI.Pointer;
import jcuda.driver.CUdevice;
import jcuda.driver.JCudaDriver;

public class CudaPlatform implements Platform {

    @Override
    public void initializePlatform(Map<String, List<Device>> devices, Cashmere cashmere) {
        JCudaDriver.setExceptionsEnabled(true);
        cuInit(0);
        final int[] count = new int[1];
        cuDeviceGetCount(count);
        for (int i = 0; i < count[0]; i++) {
            CUdevice dev = new CUdevice();
            cuDeviceGet(dev, i);
            Device d = new CudaDevice(dev, cashmere);
            String deviceName = d.getName();
            logger.debug("Device name = " + deviceName);
            if (!deviceName.equals("unknown")) {
                List<Device> l = devices.get(deviceName);
                if (l == null) {
                    l = new ArrayList<Device>();
                    devices.put(deviceName, l);
                }
                l.add(d);
            }
        }
    }

    @Override
    public String getSuffix() {
        return ".cu";
    }

    @Override
    public Pointer toPointer(byte[] a) {
        return new CudaPointer(a);
    }

    @Override
    public Pointer toPointer(int[] a) {
        return new CudaPointer(a);
    }

    @Override
    public Pointer toPointer(float[] a) {
        return new CudaPointer(a);
    }

    @Override
    public Pointer toPointer(double[] a) {
        return new CudaPointer(a);
    }

    @Override
    public Pointer toPointer(java.nio.Buffer b) {
        return new CudaPointer(b);
    }

}
