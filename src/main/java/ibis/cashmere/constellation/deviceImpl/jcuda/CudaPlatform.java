package ibis.cashmere.constellation.deviceImpl.jcuda;

import java.util.List;
import java.util.Map;

import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.deviceAPI.Device;
import ibis.cashmere.constellation.deviceAPI.Platform;
import ibis.cashmere.constellation.deviceAPI.Pointer;

public class CudaPlatform implements Platform {

    @Override
    public void initializePlatform(Map<String, List<Device>> devices, Cashmere cashmere) {
        // TODO Auto-generated method stub

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
