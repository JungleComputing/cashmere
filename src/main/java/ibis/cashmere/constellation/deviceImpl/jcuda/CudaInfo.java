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

package ibis.cashmere.constellation.deviceImpl.jcuda;

import static ibis.constellation.util.MemorySizes.GB;
import static jcuda.driver.CUresult.CUDA_SUCCESS;
import static jcuda.driver.JCudaDriver.cuDeviceGet;
import static jcuda.driver.JCudaDriver.cuDeviceGetCount;
import static jcuda.driver.JCudaDriver.cuDeviceGetName;
import static jcuda.driver.JCudaDriver.cuInit;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.deviceAPI.DeviceInfo;
import jcuda.driver.CUdevice;

class CudaInfo {

    private static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Cashmere/CUDA");

    private static final Map<String, DeviceInfo> CUDA_TO_MCL_DEVICE_INFO;

    private static boolean initialized = false;

    static {
        CUDA_TO_MCL_DEVICE_INFO = new HashMap<String, DeviceInfo>();
        // DAS-4
        // CUDA_TO_MCL_DEVICE_INFO.put("GeForce GTX 480", new DeviceInfo("fermi", 20, "gtx480", 256 * 5 * MB));
        // CUDA_TO_MCL_DEVICE_INFO.put("GeForce GTX 680", new DeviceInfo("fermi", 40, "gtx680", 256 * 5 * MB));
        CUDA_TO_MCL_DEVICE_INFO.put("GeForce GTX 980", new DeviceInfo("fermi", 50, "gtx980", 3 * GB));
        CUDA_TO_MCL_DEVICE_INFO.put("GeForce GTX TITAN", new DeviceInfo("fermi", 60, "titan", 5 * GB));
        // CUDA_TO_MCL_DEVICE_INFO.put("Tesla C2050", new DeviceInfo("fermi", 10, "c2050", 256 * 5 * MB));

        // GPUs
        CUDA_TO_MCL_DEVICE_INFO.put("GeForce GTX TITAN X", new DeviceInfo("fermi", 60, "titanx", 11 * GB));
        CUDA_TO_MCL_DEVICE_INFO.put("TITAN X (Pascal)", new DeviceInfo("fermi", 60, "titanx-pascal", 11 * GB));
        CUDA_TO_MCL_DEVICE_INFO.put("Tesla K20m", new DeviceInfo("fermi", 40, "k20", 5 * GB));
        CUDA_TO_MCL_DEVICE_INFO.put("Tesla K40c", new DeviceInfo("fermi", 60, "k40", 11 * GB));
    };

    static DeviceInfo getDeviceInfo(CUdevice device) {
        String openCLDeviceName = CudaInfo.getName(device);
        if (CUDA_TO_MCL_DEVICE_INFO.containsKey(openCLDeviceName)) {
            DeviceInfo deviceInfo = CUDA_TO_MCL_DEVICE_INFO.get(openCLDeviceName);
            logger.info("Found MCL device: " + deviceInfo.getName() + " (" + openCLDeviceName + ")");
            return deviceInfo;
        } else {
            logger.warn("Found OpenCL device: " + openCLDeviceName);
            logger.warn("This is an unkown MCL device, please add it to MCL");
            return new DeviceInfo("unknown", 1, "Unknown", 1 * GB);
        }
    }

    private static synchronized void initialize() {
        if (!initialized) {
            initialized = true;
            cuInit(0);
            final int[] count = new int[1];
            cuDeviceGetCount(count);

            final CUdevice[] devices = new CUdevice[count[0]];
            for (int i = 0; i < devices.length; i++) {
                devices[i] = new CUdevice();
                cuDeviceGet(devices[i], i);
            }
        }
    }

    private static String getName(CUdevice device) {
        final byte[] name = new byte[4096];
        if (cuDeviceGetName(name, name.length, device) == CUDA_SUCCESS) {
            for (int i = 0; i < 4096; i++) {
                if (name[i] == 0) {
                    return new String(name, 0, i);
                }
            }
        }
        return "<unnamed>";
    }
}
