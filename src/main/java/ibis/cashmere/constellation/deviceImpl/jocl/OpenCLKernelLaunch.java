/*
 * Copyright 2018 Vrije Universiteit Amsterdam, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ibis.cashmere.constellation.deviceImpl.jocl;

import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clReleaseKernel;
import static org.jocl.CL.clRetainEvent;
import static org.jocl.CL.clSetKernelArg;

import java.util.Arrays;

import org.jocl.cl_event;
import org.jocl.cl_kernel;

import ibis.cashmere.constellation.Argument;
import ibis.cashmere.constellation.Device;
import ibis.cashmere.constellation.DeviceEvent;
import ibis.cashmere.constellation.Kernel;
import ibis.cashmere.constellation.KernelLaunch;

/**
 * Represents one specific launch of a <code>Kernel</code> . While {@link #launch launch} methods can only be called once, it is
 * possible to create multiple launches from a {@link Kernel}.
 */
public class OpenCLKernelLaunch extends KernelLaunch {

    private cl_kernel kernel;

    // A OpenCLKernelLaunch can only be created from within the package
    OpenCLKernelLaunch(String kernelName, String threadName, Device device) {
        super(kernelName, threadName, device);
        this.kernel = ((OpenCLDevice) device).getKernel(kernelName);
    }

    /*
     * Public methods
     */

    /**
     * Launch the <code>Kernel</code> with the specified parameters.
     *
     * @param gridX
     *            the size of the grid in the X direction
     * @param gridY
     *            the size of the grid in the Y direction
     * @param gridZ
     *            the size of the grid in the Z direction
     * @param blockX
     *            the size of the block in the X direction
     * @param blockY
     *            the size of the block in the Y direction
     * @param blockZ
     *            the size of the block in the Z direction
     * @param synchronous
     *            indicates whether the launch should be synchronous or asynchronous
     */
    @Override
    public void launch(int gridX, int gridY, int gridZ, int blockX, int blockY, int blockZ, boolean synchronous) {
        long global_work_size[] = new long[] { gridX, gridY, gridZ };
        long local_work_size[] = new long[] { blockX, blockY, blockZ };

        device.launched();

        final cl_event[] wbeArray = new cl_event[writeBufferEvents.size()];
        for (int i = 0; i < writeBufferEvents.size(); i++) {
            wbeArray[i] = ((OpenCLEvent) writeBufferEvents.get(i)).getCLEvent();
            clRetainEvent(wbeArray[i]);
        }

        cl_event event = new cl_event();
        device.withAllocationError(() -> clEnqueueNDRangeKernel(((OpenCLCommandStream) executeQueue).getQueue(), kernel, 3, null,
                global_work_size, local_work_size, wbeArray.length, wbeArray.length == 0 ? null : wbeArray, event));
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Launched " + name + ": " + event + " (new event) depends on : " + Arrays.toString(wbeArray) + "(retained)");
            logger.debug("Storing {} in Launch.executeEvents", event);
        }

        DeviceEvent evnt = new OpenCLEvent(event);
        executeEvents.add(evnt);
        registerExecuteEventToDevice(evnt);

        launched = true;
        if (synchronous) {
            finish();
        }
        registerWithThread();
    }

    @Override
    protected void clean() {
        clReleaseKernel(kernel);
        kernel = null;
        super.clean();
    }

    @Override
    protected void setArgument(int size, Argument arg) {
        if (logger.isDebugEnabled()) {
            logger.debug("setArgument: size = " + size + ", getPointer(): " + arg.getPointer());
        }
        clSetKernelArg(kernel, nrArgs, size, ((OpenCLPointer) arg.getPointer()).getPointer());
        nrArgs++;
    }
}
