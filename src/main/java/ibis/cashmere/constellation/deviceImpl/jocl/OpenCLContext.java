package ibis.cashmere.constellation.deviceImpl.jocl;

import org.jocl.cl_context;

import ibis.cashmere.constellation.deviceAPI.Context;

public class OpenCLContext implements Context {

    private final cl_context clContext;

    public OpenCLContext(cl_context ctxt) {
        this.clContext = ctxt;
    }

    public cl_context getClContext() {
        return clContext;
    }
}
