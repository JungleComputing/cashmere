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

public class DoubleArrayArgument extends ArrayArgument {

    private double[] ds;

    public DoubleArrayArgument(Device device, CommandStream writeQueue, CommandStream readQueue,
            ArrayList<DeviceEvent> writeBufferEvents, double[] ds, Direction d) {
        super(device, d, readQueue);

        this.ds = ds;
        Pointer dsPointer = Cashmere.cashmere.getPlatform().toPointer(ds);

        if (d == Direction.IN || d == Direction.INOUT) {
            DeviceEvent event = writeBuffer(device, writeQueue, ds.length * Platform.DOUBLE_SIZE, dsPointer);
            if (event != null) {
                writeBufferEvents.add(event);
            }
        } else {
            createBuffer(device, ds.length * Platform.DOUBLE_SIZE, dsPointer);
        }
    }

    @Override
    public void scheduleReads(ArrayList<DeviceEvent> waitListEvents, ArrayList<DeviceEvent> readBufferEvents, boolean async) {
        if (direction == Direction.OUT || direction == Direction.INOUT) {
            DeviceEvent event = readBuffer(device, readQueue, waitListEvents, ds.length * Platform.DOUBLE_SIZE,
                    Cashmere.cashmere.getPlatform().toPointer(ds), async);
            if (event != null) {
                readBufferEvents.add(event);
            }
        }
    }

    @Override
    void clean() {
        super.clean();
        ds = null;
    }
}
