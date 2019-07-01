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

import ibis.cashmere.constellation.deviceAPI.CommandStream;
import ibis.cashmere.constellation.deviceAPI.Device;
import ibis.cashmere.constellation.deviceAPI.DeviceEvent;
import ibis.cashmere.constellation.deviceAPI.Pointer;

public class ByteArrayArgument extends ArrayArgument {

    private byte[] bs;

    public ByteArrayArgument(Device device, CommandStream writeQueue, CommandStream readQueue,
            ArrayList<DeviceEvent> writeBufferEvents, byte[] bs, Direction d) {
        super(device, d, readQueue);

        this.bs = bs;
        transform();
        Pointer bsPointer = Cashmere.cashmere.getPlatform().toPointer(bs);

        if (d == Direction.IN || d == Direction.INOUT) {
            DeviceEvent event = writeBuffer(device, writeQueue, bs.length, bsPointer);
            assert(event == null);
        } else {
            createBuffer(device, bs.length, bsPointer);
        }
    }

    @Override
    public void scheduleReads(ArrayList<DeviceEvent> waitListEvents, ArrayList<DeviceEvent> readBufferEvents, boolean async) {

        if (direction == Direction.OUT || direction == Direction.INOUT) {
            DeviceEvent event = readBuffer(device, readQueue, waitListEvents, bs.length,
                    Cashmere.cashmere.getPlatform().toPointer(bs), async);
            assert(event == null);
        }
    }

    @Override
    void clean() {
        super.clean();
        bs = null;
    }
}
