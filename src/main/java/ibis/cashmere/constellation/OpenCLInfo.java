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

package ibis.cashmere.constellation;

import static org.jocl.CL.CL_PLATFORM_NAME;
import static org.jocl.CL.CL_PLATFORM_VENDOR;
import static org.jocl.CL.CL_PLATFORM_VERSION;
import static org.jocl.CL.CL_PLATFORM_PROFILE;
import static org.jocl.CL.CL_PLATFORM_EXTENSIONS;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_KERNEL_FUNCTION_NAME;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetKernelInfo;
import static org.jocl.CL.clGetPlatformInfo;

import org.jocl.CL;
import org.jocl.cl_platform_id;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.Pointer;

class OpenCLInfo {

    static String getNameExtended(cl_platform_id platform) {
	return String.format("Vendor: %s, Platform: %s, Version: %s, Profile: %s, Extensions: %s", 
		getPlatformString(platform, CL_PLATFORM_VENDOR),
		getPlatformString(platform, CL_PLATFORM_NAME),
		getPlatformString(platform, CL_PLATFORM_VERSION),
		getPlatformString(platform, CL_PLATFORM_PROFILE),
		getPlatformString(platform, CL_PLATFORM_EXTENSIONS));
		
    }

    static String getName(cl_platform_id platform) {
	return String.format("Vendor: %s, Platform: %s", 
		getPlatformString(platform, CL_PLATFORM_VENDOR),
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
