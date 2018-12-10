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
import ibis.cashmere.constellation.deviceAPI.Platform;
import ibis.cashmere.constellation.deviceAPI.Pointer;

public class FloatArrayArgument extends ArrayArgument {

    protected float[] fs;

    public FloatArrayArgument(Device device, CommandStream writeQueue, CommandStream readQueue,
            ArrayList<DeviceEvent> writeBufferEvents, float[] fs, Direction d) {
        super(device, d, readQueue);

        this.fs = fs;
        Pointer fsPointer = Cashmere.cashmere.getPlatform().toPointer(fs);

        if (d == Direction.IN || d == Direction.INOUT) {
            DeviceEvent event = writeBuffer(device, writeQueue, fs.length * Platform.FLOAT_SIZE, fsPointer);
            writeBufferEvents.add(event);
        } else {
            createBuffer(device, fs.length * Platform.FLOAT_SIZE, fsPointer);
        }
    }

    @Override
    public void scheduleReads(ArrayList<DeviceEvent> waitListEvents, ArrayList<DeviceEvent> readBufferEvents, boolean async) {

        if (direction == Direction.OUT || direction == Direction.INOUT) {
            DeviceEvent event = readBuffer(device, readQueue, waitListEvents, fs.length * Platform.FLOAT_SIZE,
                    Cashmere.cashmere.getPlatform().toPointer(fs), async);
            if (event != null) {
                readBufferEvents.add(event);
            }
        }
    }

    @Override
    void clean() {
        super.clean();
        fs = null;
    }
}
