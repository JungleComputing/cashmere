package ibis.cashmere.constellation.deviceImpl.jcuda;

import ibis.cashmere.constellation.deviceAPI.Pointer;

public class CUPointer implements Pointer {

    final jcuda.Pointer cuPointer;

    public CUPointer(byte[] a) {
        cuPointer = jcuda.Pointer.to(a);
    }

    public CUPointer(int[] a) {
        cuPointer = jcuda.Pointer.to(a);
    }

    public CUPointer(float[] a) {
        cuPointer = jcuda.Pointer.to(a);
    }

    public CUPointer(double[] a) {
        cuPointer = jcuda.Pointer.to(a);
    }
}
