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

import static ibis.constellation.util.MemorySizes.GB;
import static ibis.constellation.util.MemorySizes.MB;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_KERNEL_FUNCTION_NAME;
import static org.jocl.CL.CL_PLATFORM_EXTENSIONS;
import static org.jocl.CL.CL_PLATFORM_NAME;
import static org.jocl.CL.CL_PLATFORM_PROFILE;
import static org.jocl.CL.CL_PLATFORM_VENDOR;
import static org.jocl.CL.CL_PLATFORM_VERSION;

import java.util.HashMap;
import java.util.Map;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_platform_id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.DeviceInfo;

class OpenCLInfo {

    private static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Cashmere/OpenCL");

    private static final Map<String, DeviceInfo> OPENCL_TO_MCL_DEVICE_INFO;
    static {
        OPENCL_TO_MCL_DEVICE_INFO = new HashMap<String, DeviceInfo>();
        // DAS-4
        // OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX 480", new DeviceInfo("fermi", 20, "gtx480", 256 * 5 * MB));
        // OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX 680", new DeviceInfo("fermi", 40, "gtx680", 256 * 5 * MB));
        OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX 980", new DeviceInfo("fermi", 50, "gtx980", 3 * GB));
        OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX TITAN", new DeviceInfo("fermi", 60, "titan", 5 * GB));
        // OPENCL_TO_MCL_DEVICE_INFO.put("Tahiti", new DeviceInfo("hd7970", 60, "hd7970", 256 * 5 * MB));
        // OPENCL_TO_MCL_DEVICE_INFO.put("Tesla C2050", new DeviceInfo("fermi", 10, "c2050", 256 * 5 * MB));

        // GPUs
        OPENCL_TO_MCL_DEVICE_INFO.put("GeForce GTX TITAN X", new DeviceInfo("fermi", 60, "titanx", 11 * GB));
        OPENCL_TO_MCL_DEVICE_INFO.put("TITAN X (Pascal)", new DeviceInfo("fermi", 60, "titanx-pascal", 11 * GB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Tesla K20m", new DeviceInfo("fermi", 40, "k20", 5 * GB));
        OPENCL_TO_MCL_DEVICE_INFO.put("Tesla K40c", new DeviceInfo("fermi", 60, "k40", 11 * GB));

        // CPUs
        OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Xeon(R) CPU E5-2630 v3 @ 2.40GHz",
                new DeviceInfo("xeon_e5620", 1, "xeon_e5620", 256 * 5 * MB));

        // old ones
        // OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Xeon(R) CPU           E5620  @ 2.40GHz",
        //         new DeviceInfo("xeon_e5620", 1, "xeon_e5620", 256 * 5 * MB));
        // OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Xeon(R) CPU E5-2630 0 @ 2.30GHz",
        //         new DeviceInfo("xeon_e5620", 1, "xeon_e5620", 256 * 5 * MB));
        // OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Xeon(R) CPU           X5650  @ 2.67GHz",
        //         new DeviceInfo("xeon_e5620", 1, "xeon_e5620", 256 * 5 * MB));

        // OPENCL_TO_MCL_DEVICE_INFO.put("Intel(R) Many Integrated Core Acceleration Card",
        //         new DeviceInfo("xeon_phi", 10, "xeon_phi", 7 * GB));
    };

    static DeviceInfo getDeviceInfo(cl_device_id device) {
        String openCLDeviceName = OpenCLInfo.getName(device);
        if (OPENCL_TO_MCL_DEVICE_INFO.containsKey(openCLDeviceName)) {
            DeviceInfo deviceInfo = OPENCL_TO_MCL_DEVICE_INFO.get(openCLDeviceName);
            logger.info("Found MCL device: " + deviceInfo.getName() + " (" + openCLDeviceName + ")");
            return deviceInfo;
        } else {
            logger.warn("Found OpenCL device: " + openCLDeviceName);
            logger.warn("This is an unkown MCL device, please add it to MCL");
            return new DeviceInfo("unknown", 1, "Unknown", 1 * GB);
        }
    }

    static String getNameExtended(cl_platform_id platform) {
        return String.format("Vendor: %s, Platform: %s, Version: %s, Profile: %s, Extensions: %s",
                getPlatformString(platform, CL_PLATFORM_VENDOR), getPlatformString(platform, CL_PLATFORM_NAME),
                getPlatformString(platform, CL_PLATFORM_VERSION), getPlatformString(platform, CL_PLATFORM_PROFILE),
                getPlatformString(platform, CL_PLATFORM_EXTENSIONS));

    }

    static String getName(cl_platform_id platform) {
        return String.format("Vendor: %s, Platform: %s", getPlatformString(platform, CL_PLATFORM_VENDOR),
                getPlatformString(platform, CL_PLATFORM_NAME));
    }

    @FunctionalInterface
    interface GetInfoFunction<T> {
        int getInfo(T t, int paramName, long paramSize, Pointer paramValue, long[] paramValueSizeRet);
    }

    static <T> String getInfo(T t, int paramName, GetInfoFunction<T> func) {
        long size[] = new long[1];
        func.getInfo(t, paramName, 0, null, size);
        byte buffer[] = new byte[(int) size[0]];
        func.getInfo(t, paramName, buffer.length, Pointer.to(buffer), null);
        return new String(buffer, 0, buffer.length - 1).trim();
    }

    static String getPlatformString(cl_platform_id platform, int paramName) {
        return getInfo(platform, paramName, CL::clGetPlatformInfo);
    }

    static String getName(cl_device_id device) {
        return getInfo(device, CL_DEVICE_NAME, CL::clGetDeviceInfo);
    }

    static String getName(cl_kernel kernel) {
        return getInfo(kernel, CL_KERNEL_FUNCTION_NAME, CL::clGetKernelInfo);
    }
}
