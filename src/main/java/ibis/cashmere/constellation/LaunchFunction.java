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
 * Represents a method to launch a library function {@link LibFunc}. The supplied parameters can be used to enqueue the library
 * function.
 */
@FunctionalInterface
public interface LaunchFunction {

    /**
     * Launches a library function. This means that the <code>queue</code> should be used to enqueue the library function. The
     * parameter <code>events_in_wait_list</code> will contain the events that should finish before the library function can be
     * executed. It should return the event indicating the end of the library function execution.
     *
     * @param queue
     *            the {@link CommandStream} with which library function executions can be enqueued
     * @param events_in_wait_list
     *            contains events that should finish before the library function is invoked.
     * @return event indicating the end of the library function execution.
     */
    public DeviceEvent launch(CommandStream queue, DeviceEvent[] events_in_wait_list);

}
