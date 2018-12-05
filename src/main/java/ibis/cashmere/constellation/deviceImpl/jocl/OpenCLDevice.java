package ibis.cashmere.constellation.deviceImpl.jocl;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_PROGRAM_BUILD_LOG;
import static org.jocl.CL.CL_QUEUE_PROFILING_ENABLE;
import static org.jocl.CL.CL_QUEUE_PROPERTIES;
import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateCommandQueueWithProperties;
import static org.jocl.CL.clCreateContext;
import static org.jocl.CL.clCreateKernelsInProgram;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clGetProgramBuildInfo;

import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.deviceAPI.Device;

public class OpenCLDevice extends Device {

    private cl_device_id deviceID;
    private cl_context context;

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
        this.writeQueue = clCreateCommandQueueWithProperties(context, device, queueProperties, null);
        this.executeQueue = clCreateCommandQueueWithProperties(context, device, queueProperties, null);
        this.readQueue = clCreateCommandQueueWithProperties(context, device, queueProperties, null);
    }

    @Override
    public void addKernel(String kernelSource) {
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

}
