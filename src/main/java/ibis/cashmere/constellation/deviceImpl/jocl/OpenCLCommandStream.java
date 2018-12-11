package ibis.cashmere.constellation.deviceImpl.jocl;

import org.jocl.cl_command_queue;

import ibis.cashmere.constellation.deviceAPI.CommandStream;

public class OpenCLCommandStream implements CommandStream {
    private final cl_command_queue queue;

    cl_command_queue getQueue() {
        return queue;
    }

    OpenCLCommandStream(cl_command_queue q) {
        this.queue = q;
    }
}