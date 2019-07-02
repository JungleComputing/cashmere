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

import java.util.ArrayList;

public class PointerArgument extends ArrayArgument {

    public PointerArgument(Device device, CommandStream readQueue) {
        super(device, Argument.Direction.INOUT, readQueue);
    }

    // This is not an override, but an alternative.
    public void scheduleReads(Pointer to, long size, ArrayList<DeviceEvent> waitListEvents,
            ArrayList<DeviceEvent> readBufferEvents, boolean async) {
        DeviceEvent event = readBuffer(device, readQueue, waitListEvents, size, to, async);
        if (event != null) {
            readBufferEvents.add(event);
        }
    }
}
