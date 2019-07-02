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

/**
 * Represents one specific launch of a {@link LibFunc}. While {@link #launch} methods can only be called once, it is possible to
 * create multiple launches from a {@link LibFunc}.
 */
public class LibFuncLaunch extends Launch {

    // A LibFuncLaunch can only be created from within the package.
    LibFuncLaunch(String kernelName, String threadName, Device device) {
        super(kernelName, threadName, device);
    }

    /**
     * Launch the library function with the supplied {@link LaunchFunction}. The launch will be synchronous.
     *
     * @param launchFunction
     *            represents the functionality to launch the library function.
     */
    public void launch(LaunchFunction launchFunction) {
        launch(true, launchFunction);
    }

    /**
     * Launch the library function with the supplied {@link LaunchFunction}.
     *
     * @param synchronous
     *            indicates whether the launch will be synchronous or asynchronous.
     * @param launchFunction
     *            represents the functionality to launch the library function.
     */
    public void launch(boolean synchronous, LaunchFunction launchFunction) {
        device.launched();
        final DeviceEvent[] wbeArray = writeBufferEvents.toArray(new DeviceEvent[writeBufferEvents.size()]);

        DeviceEvent.retainEvents(wbeArray);

        DeviceEvent event = device.withAllocationError(() -> {
            return launchFunction.launch(executeQueue, wbeArray.length == 0 ? null : wbeArray);
        });

        executeEvents.add(event);

        registerExecuteEventToDevice(event);

        launched = true;
        if (synchronous) {
            finish();
        }
        registerWithThread();
    }

    @Override
    protected void setArgument(int size, Argument arg) {
        // do nothing, has no meaning here
    }
}
