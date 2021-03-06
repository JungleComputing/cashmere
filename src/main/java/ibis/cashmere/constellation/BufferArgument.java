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

public class BufferArgument extends ArrayArgument {

    private Buffer buffer;

    public BufferArgument(Device device, CommandStream writeQueue, CommandStream readQueue,
            ArrayList<DeviceEvent> writeBufferEvents, Buffer b, Direction d) {
        super(device, d, readQueue);

        this.buffer = b;
        Pointer bufferPointer = Cashmere.cashmere.getPlatform().toPointer(buffer.byteBuffer);

        if (d == Direction.IN || d == Direction.INOUT) {
            DeviceEvent event = writeBuffer(device, writeQueue, buffer.capacity(), bufferPointer);
            if (event != null) {
                writeBufferEvents.add(event);
            }
        } else {
            createBuffer(device, buffer.capacity(), bufferPointer);
        }
    }

    boolean isDirect() {
        return buffer.isDirect();
    }

    @Override
    public void scheduleReads(ArrayList<DeviceEvent> waitListEvents, ArrayList<DeviceEvent> readBufferEvents, boolean async) {

        if (direction == Direction.OUT || direction == Direction.INOUT) {
            DeviceEvent event = readBuffer(device, readQueue, waitListEvents, buffer.capacity(),
                    Cashmere.cashmere.getPlatform().toPointer(buffer.byteBuffer), async);
            if (event != null) {
                readBufferEvents.add(event);
            }
        }
    }

    @Override
    void clean() {
        super.clean();
        buffer = null;
    }
}
