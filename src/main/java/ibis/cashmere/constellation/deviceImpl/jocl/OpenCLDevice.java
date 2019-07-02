package ibis.cashmere.constellation.deviceImpl.jocl;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_FALSE;
import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_PROFILING_COMMAND_QUEUED;
import static org.jocl.CL.CL_PROGRAM_BUILD_LOG;
import static org.jocl.CL.CL_QUEUE_PROFILING_ENABLE;
import static org.jocl.CL.CL_QUEUE_PROPERTIES;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernelsInProgram;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;
import static org.jocl.CL.clGetEventProfilingInfo;
import static org.jocl.CL.clGetProgramBuildInfo;
import static org.jocl.CL.clReleaseMemObject;
import static org.jocl.CL.clWaitForEvents;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jocl.CL;
import org.jocl.CLException;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_event;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

import ibis.cashmere.constellation.Argument;
import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.CommandStream;
import ibis.cashmere.constellation.Device;
import ibis.cashmere.constellation.DeviceEvent;
import ibis.cashmere.constellation.KernelLaunch;
import ibis.cashmere.constellation.Pointer;
import ibis.util.ThreadPool;

public class OpenCLDevice extends Device {

    // To compare against ...
    private static cl_event null_event = new cl_event();

    private final cl_device_id deviceID;
    private final cl_context context;

    // the programs compiled for this Device
    private Map<String, cl_program> kernels = new HashMap<String, cl_program>();

    public OpenCLDevice(cl_device_id device, cl_platform_id platform, Cashmere cashmere) {
        super(cashmere, OpenCLInfo.getDeviceInfo(device));
        this.deviceID = device;

        // initialize the context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        cl_queue_properties queueProperties = new cl_queue_properties();
        queueProperties.addProperty(CL_QUEUE_PROPERTIES, CL_QUEUE_PROFILING_ENABLE);

        // create a context for the device
        context = clCreateContext(contextProperties, 1, new cl_device_id[] { device }, null, null, null);
        setWriteQueue(new OpenCLCommandStream(clCreateCommandQueueWithProperties(context, device, queueProperties, null)));
        setExecuteQueue(new OpenCLCommandStream(clCreateCommandQueueWithProperties(context, device, queueProperties, null)));
        setReadQueue(new OpenCLCommandStream(clCreateCommandQueueWithProperties(context, device, queueProperties, null)));
        measureTimeOffset();
    }

    @Override
    public void addKernel(String kernelSource, String filename) {
        cl_program program = clCreateProgramWithSource(context, 1, new String[] { kernelSource },
                new long[] { kernelSource.length() }, null);

        clBuildProgram(program, 0, null, null, null, null);
        // clBuildProgram(program, 0, null,
        // "-cl-nv-verbose -cl-nv-maxrregcount=20", null, null);
        // assuming there is only one kernel for now.

        long size[] = new long[1];
        clGetProgramBuildInfo(program, deviceID, CL_PROGRAM_BUILD_LOG, 0, null, size);
        byte buffer[] = new byte[(int) size[0]];
        clGetProgramBuildInfo(program, deviceID, CL_PROGRAM_BUILD_LOG, buffer.length, org.jocl.Pointer.to(buffer), null);
        String log = new String(buffer, 0, buffer.length - 1).trim();

        if (log.length() > 0) {
            System.out.println(log);
        }

        cl_kernel[] kernelArray = new cl_kernel[1];
        clCreateKernelsInProgram(program, 1, kernelArray, null);
        cl_kernel kernel = kernelArray[0];

        String nameKernel = OpenCLInfo.getName(kernel);
        this.kernels.put(nameKernel, program);

        // writeBinary(program, nameKernel, info.name);

        logger.info("Registered kernel " + nameKernel + " on device " + info.getNickName());
    }

    public cl_context getContext() {
        return context;
    }

    /*
     * Memory allocation and arguments
     */

    @Override
    public <T> T withAllocationError(Supplier<T> s) {
        try {
            return s.get();
        } catch (CLException e) {
            if (e.getStatus() == CL.CL_MEM_OBJECT_ALLOCATION_FAILURE) {
                throw new Error("Got memory allocation failure, you should reserve memory", e);
            } else {
                throw new Error("Got exception", e);
            }
        }
    }

    @Override
    public Pointer createBuffer(Argument.Direction d, long size) {

        // long flags = direction == Direction.IN ? CL_MEM_READ_ONLY
        // : direction == Direction.INOUT ? CL_MEM_READ_WRITE
        // : CL_MEM_WRITE_ONLY;
        // TODO: change the API: There is a mismatch between our APIs.
        // Argument.Direction.IN/OUT in our API is about copying before/after,
        // while READ/WRITE is about whether the buffers are read/written,
        // which are two separate things. Quick fix for now: make everything
        // READ_WRITE.
        long flags = CL_MEM_READ_WRITE;// | CL_MEM_HOST_NO_ACCESS;

        cl_mem clmem = withAllocationError(() -> clCreateBuffer(context, flags, size, null, null));

        if (clmem == null) {
            throw new Error("Could not allocate device memory");
        }
        return new OpenCLPointer(clmem);
    }

    @Override
    public DeviceEvent writeNoCreateBuffer(CommandStream stream, DeviceEvent[] waitEvents, boolean async, long size, Pointer hostPtr,
            Pointer devicePtr) {
        final int nEvents = waitEvents != null ? waitEvents.length : 0;
        final cl_event[] wEvents = nEvents == 0 ? null : new cl_event[nEvents];
        if (nEvents > 0) {
            for (int i = 0; i < nEvents; i++) {
                wEvents[i] = ((OpenCLEvent) waitEvents[i]).getCLEvent();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("WriteBuffer: events to wait for: " + Arrays.toString(waitEvents));
            }
            if (logger.isTraceEnabled()) {
                ThreadPool.createNew(new Thread() {
                    @Override
                    public void run() {
                        clWaitForEvents(waitEvents.length, wEvents);
                        logger.trace("Test wait successful: " + Arrays.toString(wEvents));
                    }
                }, "test event waiter");
            }
            DeviceEvent.retainEvents(waitEvents);
        }

        cl_command_queue q = ((OpenCLCommandStream) stream).getQueue();
        cl_mem memObject = ((OpenCLPointer) devicePtr).getCLMem();

        cl_event event = async ? new cl_event() : null;
        withAllocationError(() -> clEnqueueWriteBuffer(q, memObject, async ? CL_FALSE : CL_TRUE, 0, size, ((OpenCLPointer) hostPtr).getPointer(),
                nEvents, (nEvents == 0) ? null : wEvents, event));

        if (event == null) {
            return null;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("performing a writeBuffer with new event: {}, depends on {} (retained)", event, waitEvents);
        }

        if (logger.isInfoEnabled()) {
            logger.info("Done enqueue write event " + event);
        }

        return new OpenCLEvent(event);
    }

    @Override
    public DeviceEvent enqueueReadBuffer(CommandStream stream, boolean asynch, DeviceEvent[] waitEvents, long size,
            Pointer hostPtr, Pointer devicePtr) {
        cl_event event = new cl_event();
        final int nEvents = waitEvents != null ? waitEvents.length : 0;
        final cl_event[] wEvents = new cl_event[nEvents];
        for (int i = 0; i < nEvents; i++) {
            wEvents[i] = ((OpenCLEvent) waitEvents[i]).getCLEvent();
        }
        cl_command_queue q = ((OpenCLCommandStream) stream).getQueue();
        cl_mem memObject = ((OpenCLPointer) devicePtr).getCLMem();
        withAllocationError(() -> clEnqueueReadBuffer(q, memObject, asynch ? CL_FALSE : CL_TRUE, 0, size,
                ((OpenCLPointer) hostPtr).getPointer(), nEvents, (nEvents == 0) ? null : wEvents, event));
        if (logger.isDebugEnabled()) {
            logger.debug("performing a readBuffer with new event: {}, depends on {} (retained)", event, wEvents);
        }
        if (event.equals(null_event)) {
            // No initialized event returned.
            return null;
        }
        // Event.showEvents(waitEvents);
        // Event.showEvent(event);
        return new OpenCLEvent(event);
    }

    @Override
    public void waitEvents(DeviceEvent[] waitEvents) {
        final int nEvents = waitEvents != null ? waitEvents.length : 0;
        final cl_event[] wEvents = new cl_event[nEvents];
        for (int i = 0; i < nEvents; i++) {
            wEvents[i] = ((OpenCLEvent) waitEvents[i]).getCLEvent();
        }
        clWaitForEvents(wEvents.length, wEvents);
    }

    @Override
    public KernelLaunch createLaunch(String name, String threadname) {
        return new OpenCLKernelLaunch(name, threadname, this);
    }

    void measureTimeOffset() {
        float f[] = { 0.0f };
        org.jocl.Pointer fPointer = org.jocl.Pointer.to(f);
        cl_mem memObject = clCreateBuffer(context, CL_MEM_READ_WRITE, Sizeof.cl_float, null, null);
        cl_event event = new cl_event();
        long startHost = System.nanoTime();
        clEnqueueWriteBuffer(((OpenCLCommandStream) getWriteQueue()).getQueue(), memObject, CL_TRUE, 0, Sizeof.cl_float, fPointer,
                0, null, event);
        clReleaseMemObject(memObject);
        long[] value = new long[1];
        clGetEventProfilingInfo(event, CL_PROFILING_COMMAND_QUEUED, Sizeof.cl_ulong, org.jocl.Pointer.to(value), null);
        setOffsetHostDevice(startHost - value[0]);
    }

    cl_kernel getKernel() {
        Collection<cl_program> programCollection = kernels.values();
        cl_program[] programs = new cl_program[programCollection.size()];
        programs = programCollection.toArray(programs);
        cl_program program = programs[0];
        return getKernelProgram(program);
    }

    cl_kernel getKernel(String name) {
        if (name == null) {
            return getKernel();
        }
        cl_program program = kernels.get(name);
        return getKernelProgram(program);
    }

    private cl_kernel getKernelProgram(cl_program program) {
        cl_kernel[] kernelArray = new cl_kernel[1];
        clCreateKernelsInProgram(program, 1, kernelArray, null);
        cl_kernel kernel = kernelArray[0];
        return kernel;
    }

    @Override
    public boolean registeredKernel(String name) {
        if (name == null) {
            return kernels.size() == 1;
        } else {
            return kernels.containsKey(name);
        }
    }

    protected void writeBinary(cl_program program, String nameKernel, String nameDevice) {
        // int nrDevices = 1;
        // long[] sizes = new long[nrDevices];
        // clGetProgramInfo(program, CL_PROGRAM_BINARY_SIZES, nrDevices * Sizeof.size_t, Pointer.to(sizes), null);
        // byte[][] buffers = new byte[nrDevices][];
        // Pointer[] pointers = new Pointer[nrDevices];
        // for (int i = 0; i < nrDevices; i++) {
        //     buffers[i] = new byte[(int) sizes[i] + 1];
        //     pointers[i] = Pointer.to(buffers[i]);
        // }
        // Pointer p = Pointer.to(pointers);
        // clGetProgramInfo(program, CL_PROGRAM_BINARIES, nrDevices * Sizeof.POINTER, p, null);
        // String binary = new String(buffers[0], 0, buffers[0].length - 1).trim();
        // try {
        //     PrintStream out = new PrintStream(new File(nameDevice + "_" + nameKernel + ".ptx"));
        //     out.println(binary);
        //     out.close();
        // } catch (IOException e) {
        //     System.err.println(e.getMessage());
        // }
    }
}
