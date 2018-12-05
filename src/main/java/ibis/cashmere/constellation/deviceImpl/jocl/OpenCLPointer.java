package ibis.cashmere.constellation.deviceImpl.jocl;

import java.nio.Buffer;

import ibis.cashmere.constellation.deviceAPI.Pointer;

public class OpenCLPointer implements Pointer {

    private final org.jocl.Pointer clPointer;
    private org.jocl.cl_mem clmem = null;

    public OpenCLPointer(byte[] a) {
        clPointer = org.jocl.Pointer.to(a);
    }

    public OpenCLPointer(int[] a) {
        clPointer = org.jocl.Pointer.to(a);
    }

    public OpenCLPointer(float[] a) {
        clPointer = org.jocl.Pointer.to(a);
    }

    public OpenCLPointer(double[] a) {
        clPointer = org.jocl.Pointer.to(a);
    }

    public OpenCLPointer(Buffer b) {
        clPointer = org.jocl.Pointer.to(b);
    }

}
