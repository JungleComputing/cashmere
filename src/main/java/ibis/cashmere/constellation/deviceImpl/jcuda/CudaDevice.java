package ibis.cashmere.constellation.deviceImpl.jcuda;

import static jcuda.driver.JCudaDriver.cuStreamCreate;

import java.util.function.Supplier;

import ibis.cashmere.constellation.Argument.Direction;
import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.deviceAPI.CommandStream;
import ibis.cashmere.constellation.deviceAPI.Device;
import ibis.cashmere.constellation.deviceAPI.DeviceEvent;
import ibis.cashmere.constellation.deviceAPI.KernelLaunch;
import ibis.cashmere.constellation.deviceAPI.Pointer;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUfunction;
import jcuda.driver.CUstream;

public class CudaDevice extends Device {

    private final CUcontext ctxt;

    public CudaDevice(CUdevice device, Cashmere cashmere) {
        super(cashmere, CudaInfo.getDeviceInfo(device));
        ctxt = new CUcontext();
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
    }

    @Override
    public Pointer createBuffer(Direction d, long size) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void addKernel(String kernelSource) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void measureTimeOffset() {
        // TODO Auto-generated method stub

    }

    @Override
    public DeviceEvent writeNoCreateBuffer(CommandStream q, DeviceEvent[] waitEvents, long size, Pointer hostPtr,
            Pointer devicePtr) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DeviceEvent enqueueReadBuffer(CommandStream q, boolean asynch, DeviceEvent[] waitEvents, long size, Pointer hostPtr,
            Pointer devicePtr) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> T withAllocationError(Supplier<T> s) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void waitEvents(DeviceEvent[] waitEvents) {
        // TODO Auto-generated method stub

    }

    @Override
    public KernelLaunch createLaunch(String name, String threadname) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean registeredKernel(String name) {
        // TODO Auto-generated method stub
        return false;
    }

    CUfunction getKernel(String kernelName) {
        // TODO Auto-generated method stub
        return null;
    }
}
