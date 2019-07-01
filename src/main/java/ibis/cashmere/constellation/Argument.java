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

import static ibis.constellation.util.MemorySizes.toStringBytes;

import java.util.ArrayList;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.deviceAPI.CommandStream;
import ibis.cashmere.constellation.deviceAPI.Device;
import ibis.cashmere.constellation.deviceAPI.DeviceEvent;
import ibis.cashmere.constellation.deviceAPI.Pointer;

/**
 * A class used for indicating directions of arguments to kernels.
 */
public class Argument {

    /*
     * Public members
     *
     * This is the only thing that is public.  All other methods and members are package or protected.
     */

    /**
     * The <code>Direction</code> enumeration contains constants for arguments.
     */
    public static enum Direction {
        /**
         * The argument is used for input and is only read by the kernel.
         */
        IN,
        /**
         * The argument is used for output and is only written by the kernel.
         */
        OUT,
        /**
         * The argument is used for input and for output, it read and written by the kernel.
         */
        INOUT,
    };

    /*
     * members for subclasses
     */
    private Pointer pointer;
    protected Direction direction;
    private boolean readScheduled;

    /*
     * private members
     */
    private static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation");
    private static final Logger memLogger = LoggerFactory.getLogger("ibis.cashmere.constellation.Argument/memory");
    private static final Logger eventLogger = LoggerFactory.getLogger("ibis.cashmere.constellation.Event");

    // keeping track of the amount allocated
    private static long allocatedBytes = 0;
    private long size;

    /*
     * Constructors
     */

    // The constructor should only be called from subclasses
    protected Argument(Direction d) {
        this.pointer = null;
        this.direction = d;
    }

    protected Argument(Pointer p, Direction d, boolean allocated) {
        this.pointer = p;
        this.direction = d;
    }

    /*
     * Package methods
     */

    public void scheduleReads(ArrayList<DeviceEvent> a, ArrayList<DeviceEvent> b, boolean async) {
    }

    void clean() {
        if (pointer != null) {
            if (pointer.clean()) {
                synchronized (Argument.class) {
                    allocatedBytes -= size;
                }
                memLogger.debug(String.format("deallocated: %4s, total: %s", toStringBytes(size), toStringBytes(allocatedBytes)));
            }
        }
    }

    public Pointer getPointer() {
        return pointer;
    }

    public Direction getDirection() {
        return direction;
    }

    public void createBuffer(Device device, long size, Pointer hostPtr) {

        pointer = device.createBuffer(direction, size);
        this.size = size;
        if (memLogger.isDebugEnabled()) {
            synchronized (Argument.class) {
                allocatedBytes += size;
            }
            memLogger.debug(String.format("allocated: %6s, total: %s", toStringBytes(size), toStringBytes(allocatedBytes)));
        }

        if (pointer == null) {
            throw new Error("Could not allocate device memory");
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Done allocating memory of size " + size + ", result = " + pointer);
        }
    }

    public DeviceEvent writeBufferNoCreateBuffer(Device device, CommandStream q, final DeviceEvent[] waitEvents, long size,
            Pointer hostPtr) {

        boolean async = this instanceof BufferArgument && ((BufferArgument) this).isDirect();
        return device.writeNoCreateBuffer(q, waitEvents, async, size, hostPtr, pointer);
    }

    boolean readScheduled() {
        return readScheduled;
    }

    /*
     * Methods for subclasses
     */

    protected DeviceEvent writeBuffer(Device device, CommandStream q, long size, Pointer hostPtr) {
        createBuffer(device, size, hostPtr);

        return writeBufferNoCreateBuffer(device, q, null, size, hostPtr);
    }

    protected DeviceEvent readBuffer(Device device, CommandStream q, ArrayList<DeviceEvent> waitEvents, long size,
            Pointer hostPtr, boolean asynch) {

        if (eventLogger.isDebugEnabled()) {
            eventLogger.debug("Doing a readbuffer");
        }

        readScheduled = true;
        final DeviceEvent[] events = waitEvents != null ? waitEvents.toArray(new DeviceEvent[waitEvents.size()]) : null;
        final int nEvents = waitEvents != null ? waitEvents.size() : 0;
        if (nEvents != 0) {
            for (DeviceEvent e : events) {
                e.retain();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("readBuffer: events to wait for: " + Arrays.toString(events));
            }
        }

        boolean async = asynch && this instanceof BufferArgument && ((BufferArgument) this).isDirect();
        return device.enqueueReadBuffer(q, async, events, size, hostPtr, pointer);
    }
}
