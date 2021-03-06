package ibis.cashmere.constellation.deviceImpl.jcuda;

import ibis.cashmere.constellation.CommandStream;
import jcuda.driver.CUstream;

public class CudaCommandStream implements CommandStream {
    private final CUstream stream;

    public CUstream getQueue() {
        return stream;
    }

    CudaCommandStream(CUstream q) {
        this.stream = q;
    }
}
