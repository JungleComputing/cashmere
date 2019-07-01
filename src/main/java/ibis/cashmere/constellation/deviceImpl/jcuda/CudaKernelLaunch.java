package ibis.cashmere.constellation.deviceImpl.jcuda;

import static jcuda.driver.JCudaDriver.cuEventCreate;
import static jcuda.driver.JCudaDriver.cuEventRecord;
import static jcuda.driver.JCudaDriver.cuLaunchKernel;
import static jcuda.driver.JCudaDriver.cuStreamWaitEvent;

import java.util.ArrayList;
import java.util.List;

import ibis.cashmere.constellation.Argument;
import ibis.cashmere.constellation.deviceAPI.Device;
import ibis.cashmere.constellation.deviceAPI.DeviceEvent;
import ibis.cashmere.constellation.deviceAPI.KernelLaunch;
import jcuda.Pointer;
import jcuda.driver.CUevent;
import jcuda.driver.CUfunction;
import jcuda.driver.CUstream;

public class CudaKernelLaunch extends KernelLaunch {

    private CUfunction kernel;
    private List<Pointer> args = new ArrayList<Pointer>();

    // A CudaKernelLaunch can only be created from within the package
    CudaKernelLaunch(String kernelName, String threadName, Device device) {
        super(kernelName, threadName, device);
        this.kernel = ((CudaDevice) device).getKernel(kernelName);
    }

    @Override
    public void launch(int gridX, int gridY, int gridZ, int blockX, int blockY, int blockZ, boolean synchronous) {

        device.launched();

        // Create argument array
        final Pointer[] params = args.toArray(new Pointer[args.size()]);

        CUstream cuStream = ((CudaCommandStream) executeQueue).getQueue();
        // insert waits for the write events
        for (int i = 0; i < writeBufferEvents.size(); i++) {
            cuStreamWaitEvent(cuStream, ((CudaEvent) writeBufferEvents.get(i)).getEvent(), 0);
        }

        cuLaunchKernel(kernel, gridX/blockX, gridY/blockY, gridZ/blockZ, blockX, blockY, blockZ, 0, cuStream, Pointer.to(params), null);

        // create an execute event.

        CUevent execEvent = new CUevent();
        cuEventCreate(execEvent, jcuda.driver.CUevent_flags.CU_EVENT_BLOCKING_SYNC);
        cuEventRecord(execEvent, cuStream);

        DeviceEvent evnt = new CudaEvent(execEvent);
        executeEvents.add(evnt);
        registerExecuteEventToDevice(evnt);

        launched = true;
        if (synchronous) {
            finish();
        }
        registerWithThread();
    }

    @Override
    protected void setArgument(int size, Argument arg) {
        logger.debug("args(" + args.size() + " = " + ((CudaPointer) arg.getPointer()).cuPointer);
        args.add(((CudaPointer) arg.getPointer()).cuPointer);
    }
}
