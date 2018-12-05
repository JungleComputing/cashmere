package ibis.cashmere.constellation.deviceImpl.jcuda;

import ibis.cashmere.constellation.deviceAPI.Pointer;

public class CudaPointer implements Pointer {

    final jcuda.Pointer cuPointer;

    public CudaPointer(byte[] a) {
        cuPointer = jcuda.Pointer.to(a);
    }

    public CudaPointer(int[] a) {
        cuPointer = jcuda.Pointer.to(a);
    }

    public CudaPointer(float[] a) {
        cuPointer = jcuda.Pointer.to(a);
    }

    public CudaPointer(double[] a) {
        cuPointer = jcuda.Pointer.to(a);
    }

    public CudaPointer(java.nio.Buffer b) {
        cuPointer = jcuda.Pointer.to(b);
    }

    // TODO: encapsulate CUdeviceptr
}
