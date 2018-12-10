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

import ibis.cashmere.constellation.deviceAPI.CommandStream;
import ibis.cashmere.constellation.deviceAPI.Device;

/**
 * Represents a method to initialize a library.
 */
@FunctionalInterface
public interface InitLibraryFunction {

    /**
     * Initializes a library.
     *
     * @param device
     *            the <code>device</code> to be used for the library
     * @param queue
     *            the <code>CommandStream</code> to be used for the library
     */
    public void initialize(Device device, CommandStream queue);
}
