package ibis.cashmere.constellation.deviceImpl.jcuda;

import ibis.cashmere.constellation.deviceAPI.CommandStream;
import jcuda.driver.CUstream;

public class CudaCommandStream implements CommandStream {
    private final CUstream stream;

    CUstream getQueue() {
        return stream;
    }

    CudaCommandStream(CUstream q) {
        this.stream = q;
    }
}
