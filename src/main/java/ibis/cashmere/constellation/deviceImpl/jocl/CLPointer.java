package ibis.cashmere.constellation.deviceImpl.jocl;

import ibis.cashmere.constellation.deviceAPI.Pointer;

public class CLPointer implements Pointer {
    final org.jocl.Pointer clPointer;

    public CLPointer(byte[] a) {
        clPointer = org.jocl.Pointer.to(a);
    }

    public CLPointer(int[] a) {
        clPointer = org.jocl.Pointer.to(a);
    }

    public CLPointer(float[] a) {
        clPointer = org.jocl.Pointer.to(a);
    }

    public CLPointer(double[] a) {
        clPointer = org.jocl.Pointer.to(a);
    }
}
