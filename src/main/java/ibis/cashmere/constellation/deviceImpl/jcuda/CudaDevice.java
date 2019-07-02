package ibis.cashmere.constellation.deviceImpl.jcuda;

import static jcuda.driver.JCudaDriver.cuCtxCreate;
import static jcuda.driver.JCudaDriver.cuCtxSetCurrent;
import static jcuda.driver.JCudaDriver.cuEventCreate;
import static jcuda.driver.JCudaDriver.cuEventRecord;
import static jcuda.driver.JCudaDriver.cuEventSynchronize;
import static jcuda.driver.JCudaDriver.cuMemAlloc;
import static jcuda.driver.JCudaDriver.cuMemcpyDtoH;
import static jcuda.driver.JCudaDriver.cuMemcpyDtoHAsync;
import static jcuda.driver.JCudaDriver.cuMemcpyHtoD;
import static jcuda.driver.JCudaDriver.cuMemcpyHtoDAsync;
import static jcuda.driver.JCudaDriver.cuModuleGetFunction;
import static jcuda.driver.JCudaDriver.cuModuleLoadData;
import static jcuda.driver.JCudaDriver.cuStreamCreate;
import static jcuda.driver.JCudaDriver.cuStreamSynchronize;
import static jcuda.driver.JCudaDriver.cuStreamWaitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import ibis.cashmere.constellation.Argument.Direction;
import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.CommandStream;
import ibis.cashmere.constellation.Device;
import ibis.cashmere.constellation.DeviceEvent;
import ibis.cashmere.constellation.KernelLaunch;
import ibis.cashmere.constellation.Pointer;
import jcuda.CudaException;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUdeviceptr;
import jcuda.driver.CUevent;
import jcuda.driver.CUfunction;
import jcuda.driver.CUmodule;
import jcuda.driver.CUstream;
import jcuda.driver.JCudaDriver;

public class CudaDevice extends Device {

    private final CUcontext ctxt;

    private Map<String, CUfunction> kernels = new HashMap<String, CUfunction>();

    private String architecture;

    private String capability;

    private ThreadLocal<Integer> threadLocal = ThreadLocal.withInitial(() -> 0);

    public CudaDevice(CUdevice device, Cashmere cashmere) {
        super(cashmere, CudaInfo.getDeviceInfo(device));

        ctxt = new CUcontext();
        cuCtxCreate(ctxt, 0, device);

        CUstream stream;

        stream = new CUstream();
        cuStreamCreate(stream, jcuda.driver.CUstream_flags.CU_STREAM_DEFAULT);
        setWriteQueue(new CudaCommandStream(stream));
        stream = new CUstream();
        cuStreamCreate(stream, jcuda.driver.CUstream_flags.CU_STREAM_DEFAULT);
        setExecuteQueue(new CudaCommandStream(stream));
        stream = new CUstream();
        cuStreamCreate(stream, jcuda.driver.CUstream_flags.CU_STREAM_DEFAULT);
        setReadQueue(new CudaCommandStream(stream));

        int cc[] = getMajorMinor(device);

        this.architecture = "compute_" + cc[0] + "" + cc[1];
        this.capability = "sm_" + cc[0] + "" + cc[1];
        measureTimeOffset();

    }

    private int[] getMajorMinor(CUdevice device) {
        final int[] major = new int[1];
        final int[] minor = new int[1];
        JCudaDriver.cuDeviceGetAttribute(major, jcuda.driver.CUdevice_attribute.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR,
                device);
        JCudaDriver.cuDeviceGetAttribute(minor, jcuda.driver.CUdevice_attribute.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR,
                device);
        final int[] result = new int[2];
        result[0] = major[0];
        result[1] = minor[0];

        return result;
    }

    private void checkContext() {
        Integer result = threadLocal.get();
        logger.debug("checkContext: result = " + result);
        if (result == 0) {
            logger.debug("Setting context");
            cuCtxSetCurrent(ctxt);
            threadLocal.set(new Integer(1));
        }
    }

    private CUdeviceptr alloc(long size) {
        checkContext();
        CUdeviceptr ptr = new CUdeviceptr();
        cuMemAlloc(ptr, size);
        return ptr;
    }

    @Override
    public Pointer createBuffer(Direction d, long size) {
        CUdeviceptr ptr = withAllocationError(() -> alloc(size));
        return new CudaPointer(ptr);
    }

    @Override
    public void addKernel(String kernelSource, String name) {
        CUmodule module = new CUmodule();
        logger.debug("Adding a kernel for Cuda, name = " + name);
        try {
            byte[] cubin = compileCuSourceToCubin(kernelSource, "-lineinfo", "-gencode=arch=" + architecture + ",code=" + capability);
            cuModuleLoadData(module, cubin);
            String kernelName = name.substring(0, name.lastIndexOf(".cu"));
            CUfunction f = new CUfunction();
            cuModuleGetFunction(f, module, kernelName);
            kernels.put(kernelName, f);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private byte[] compileCuSourceToCubin(final String source, final String... options) throws IOException {
        final File cuFile = File.createTempFile("jcuda", ".cu");
        final File cubinFile = File.createTempFile("jcuda", ".cubin");

        try {
            FileUtils.write(cuFile, source);

            final List<String> arguments = new ArrayList<String>();
            arguments.add("nvcc");
            arguments.addAll(Arrays.asList(options));
            arguments.add("-cubin");
            arguments.add(cuFile.getAbsolutePath());
            arguments.add("-o");
            arguments.add(cubinFile.getAbsolutePath());

            final String output = runExternalCommand(arguments.toArray(new String[0]));
            if (output.length() > 2) {
                System.out.println(output);
            }

            //final String disassembly = runExternalCommand("cuobjdump", "-sass", cubinFile.getAbsolutePath());
            //System.out.println(disassembly);

            return FileUtils.readFileToByteArray(cubinFile);
        } finally {
            cuFile.delete();
            cubinFile.delete();
        }
    }

    private String runExternalCommand(final String... arguments) throws IOException {
        final Process process = new ProcessBuilder().command(arguments).redirectErrorStream(true).start();
        final String processOutput = new String(IOUtils.toByteArray(process.getInputStream()));
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Could not generate output file: " + processOutput);
            }
        } catch (final InterruptedException e) {
            throw new IOException("Interrupted while waiting for external process", e);
        }

        return processOutput;
    }

    void measureTimeOffset() {
        checkContext();
        CUstream cuStream = ((CudaCommandStream) getExecuteQueue()).getQueue();
        CUevent execEvent = new CUevent();
        cuEventCreate(execEvent, jcuda.driver.CUevent_flags.CU_EVENT_BLOCKING_SYNC);
        cuEventRecord(execEvent, cuStream);
        cuEventSynchronize(execEvent);
        // TODO: not possible in CUDA? We can only get the time difference between two events?
    }

    @Override
    public DeviceEvent writeNoCreateBuffer(CommandStream q, DeviceEvent[] waitEvents, boolean async, long size, Pointer hostPtr,
            Pointer devicePtr) {
        checkContext();
        CUstream cuStream = ((CudaCommandStream) q).getQueue();
        // insert waits for the wait events
        if (waitEvents != null) {
            for (DeviceEvent evnt : waitEvents) {
                cuStreamWaitEvent(cuStream, ((CudaEvent) evnt).getEvent(), 0);
            }
        }


        // Asynchronous writes require page-pinned memory, which we don't have. So, instead, we
        // synchronize on the stream, and copy synchronously.
        // TODO: investigate!

        if (! async) {
            cuStreamSynchronize(cuStream);
            logger.debug("Deviceptr = " + ((CudaPointer) devicePtr).getPtr() + ", host ptr = " + ((CudaPointer) hostPtr).getPointer());
            cuMemcpyHtoD(((CudaPointer) devicePtr).getPtr(), ((CudaPointer) hostPtr).getPointer(), size);
            return null;
        }
        cuMemcpyHtoDAsync(((CudaPointer) devicePtr).getPtr(), ((CudaPointer) hostPtr).getPointer(), size, cuStream);
        // Insert event in the queue and return it, so that it can be waited for.
        CUevent e = new CUevent();
        cuEventCreate(e, jcuda.driver.CUevent_flags.CU_EVENT_BLOCKING_SYNC);
        cuEventRecord(e, cuStream);
        return new CudaEvent(e);
    }

    @Override
    public DeviceEvent enqueueReadBuffer(CommandStream q, boolean async, DeviceEvent[] waitEvents, long size, Pointer hostPtr,
            Pointer devicePtr) {
        checkContext();
        CUstream cuStream = ((CudaCommandStream) q).getQueue();
        // insert waits for the wait events
        if (waitEvents != null) {
            for (DeviceEvent evnt : waitEvents) {
                cuStreamWaitEvent(cuStream, ((CudaEvent) evnt).getEvent(), 0);
            }
        }

        // Asynchronous writes require page-pinned memory, which we don't have. So, instead, we
        // synchronize on the stream, and copy synchronously.
        // TODO: investigate!

        if (! async) {
            cuStreamSynchronize(cuStream);
            cuMemcpyDtoH(((CudaPointer) hostPtr).getPointer(), ((CudaPointer) devicePtr).getPtr(), size);
            return null;
        }

        cuMemcpyDtoHAsync(((CudaPointer) hostPtr).getPointer(), ((CudaPointer) devicePtr).getPtr(), size, cuStream);
        // Insert event in the queue and return it, so that it can be waited for.
        CUevent e = new CUevent();
        cuEventCreate(e, jcuda.driver.CUevent_flags.CU_EVENT_BLOCKING_SYNC);
        cuEventRecord(e, cuStream);
        return new CudaEvent(e);
    }

    @Override
    public <T> T withAllocationError(Supplier<T> s) {
        try {
            return s.get();
        } catch (CudaException e) {
            throw new Error("Got exception", e);
        }
    }

    @Override
    public void waitEvents(DeviceEvent[] waitEvents) {
        if (waitEvents != null) {
            checkContext();
            for (DeviceEvent evnt : waitEvents) {
                CUevent e = ((CudaEvent) evnt).getEvent();
                if (e != null) {
                    cuEventSynchronize(e);
                }
            }
        }
    }

    @Override
    public KernelLaunch createLaunch(String name, String threadname) {
        checkContext();
        return new CudaKernelLaunch(name, threadname, this);
    }

    @Override
    public boolean registeredKernel(String name) {
        if (name == null) {
            return kernels.size() == 1;
        }
        return kernels.containsKey(name);
    }

    CUfunction getKernel(String kernelName) {
        if (kernelName == null) {
            if (kernels.size() == 1) {
                return kernels.values().toArray(new CUfunction[1])[0];
            }
            return null;
        }
        return kernels.get(kernelName);
    }
}
