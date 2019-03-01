package ibis.cashmere.constellation.deviceImpl.jocl;

import static org.jocl.CL.clReleaseMemObject;

import java.nio.Buffer;

import org.jocl.cl_mem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.deviceAPI.Pointer;

public class OpenCLPointer implements Pointer {

    private final org.jocl.Pointer clPointer;
    private org.jocl.cl_mem clmem = null;
    private static Logger logger = LoggerFactory.getLogger(OpenCLPointer.class);

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

    OpenCLPointer(org.jocl.cl_mem mem) {
        clmem = mem;
        clPointer = org.jocl.Pointer.to(clmem);
    }

    public boolean clean() {
        if (clmem != null) {
            if (logger.isInfoEnabled()) {
                logger.info("Releasing " + clmem);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("about to release");
            }
            clReleaseMemObject(clmem);
            if (logger.isDebugEnabled()) {
                logger.debug("released");
            }
            clmem = null;
            return true;
        }
        return false;
    }

    cl_mem getCLMem() {
        return clmem;
    }

    public org.jocl.Pointer getPointer() {
        return clPointer;
    }
}
