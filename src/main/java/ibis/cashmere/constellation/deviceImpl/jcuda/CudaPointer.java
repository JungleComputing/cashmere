package ibis.cashmere.constellation.deviceImpl.jcuda;

import static jcuda.driver.JCudaDriver.cuMemFree;

import ibis.cashmere.constellation.Pointer;
import jcuda.driver.CUdeviceptr;

public class CudaPointer implements Pointer {

    final jcuda.Pointer cuPointer;
    private CUdeviceptr ptr;

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

    CudaPointer(CUdeviceptr ptr) {
        this.ptr = ptr;
        cuPointer = jcuda.Pointer.to(ptr);
    }

    CUdeviceptr getPtr() {
        return ptr;
    }

    jcuda.Pointer getPointer() {
        return cuPointer;
    }

    @Override
    public boolean clean() {
        if (ptr != null) {
            cuMemFree(ptr);
            ptr = null;
            return true;
        }
        return false;
    }
}
