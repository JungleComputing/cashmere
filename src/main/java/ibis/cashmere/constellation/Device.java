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
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that represents a many-core <code>Device</code>.
 */
public abstract class Device implements Comparable<Device> {

    /*
     * loggers
     */
    protected static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Device");
    private static final Logger memlogger = LoggerFactory.getLogger("ibis.cashmere.constellation.Device/memory");
    private static final Logger eventlogger = LoggerFactory.getLogger("ibis.cashmere.constellation.Device/event");

    private Cashmere cashmere;

    private CommandStream writeQueue;
    private CommandStream executeQueue;
    private CommandStream readQueue;
    private int nrKernelLaunches;

    // the information for this device
    protected final DeviceInfo info;

    /*
     * Keeping track of the state of the Device
     */
    // the offset of events timings to the host
    private long offsetHostDevice;

    protected void setOffsetHostDevice(long offsetHostDevice) {
        this.offsetHostDevice = offsetHostDevice;
    }

    // keeping track of the number of kernels launched
    private int launched;

    // keeping track of the amount of memory that is reserved
    private long memoryReserved;

    /*
     * Arguments and their relation to events
     */

    // mappings from arguments to a kernel to Argument and DeviceEvents objects, etc
    private Map<float[], FloatArrayArgument> floatArrayArguments;
    private Map<double[], DoubleArrayArgument> doubleArrayArguments;
    private Map<int[], IntArrayArgument> intArrayArguments;
    private Map<byte[], ByteArrayArgument> byteArrayArguments;
    private Map<Buffer, BufferArgument> bufferArguments;
    private Map<Pointer, PointerArgument> pointerArguments;

    private Map<float[], DeviceEvent> writeEventsFloats;
    private Map<double[], DeviceEvent> writeEventsDoubles;
    private Map<int[], DeviceEvent> writeEventsInts;
    private Map<byte[], DeviceEvent> writeEventsBytes;
    private Map<Buffer, DeviceEvent> writeEventsBuffers;
    private Map<Pointer, DeviceEvent> writeEventsPointers;

    private Map<DeviceEvent, float[]> writeEventsFloatsInversed;
    private Map<DeviceEvent, double[]> writeEventsDoublesInversed;
    private Map<DeviceEvent, int[]> writeEventsIntsInversed;
    private Map<DeviceEvent, byte[]> writeEventsBytesInversed;
    private Map<DeviceEvent, Buffer> writeEventsBuffersInversed;
    private Map<DeviceEvent, Pointer> writeEventsPointersInversed;

    private Map<Buffer, ArrayList<DeviceEvent>> executeEventsBuffers;
    private Map<Pointer, ArrayList<DeviceEvent>> executeEventsPointers;
    private Map<float[], ArrayList<DeviceEvent>> executeEventsFloats;
    private Map<double[], ArrayList<DeviceEvent>> executeEventsDoubles;
    private Map<int[], ArrayList<DeviceEvent>> executeEventsInts;
    private Map<byte[], ArrayList<DeviceEvent>> executeEventsBytes;

    private Map<String, ArrayList<DeviceEvent>> readBufferEventsMap;

    /*
     * Variables for debugging/logging
     */
    private long nrBytesAllocated = 0;

    /*
     * The constructor
     */
    public Device(Cashmere cashmere, DeviceInfo info) {
        this.cashmere = cashmere;

        this.info = info;

        readBufferEventsMap = new HashMap<String, ArrayList<DeviceEvent>>();

        this.bufferArguments = new IdentityHashMap<Buffer, BufferArgument>();
        this.pointerArguments = new IdentityHashMap<Pointer, PointerArgument>();
        this.floatArrayArguments = new IdentityHashMap<float[], FloatArrayArgument>();
        this.doubleArrayArguments = new IdentityHashMap<double[], DoubleArrayArgument>();
        this.intArrayArguments = new IdentityHashMap<int[], IntArrayArgument>();
        this.byteArrayArguments = new IdentityHashMap<byte[], ByteArrayArgument>();

        this.writeEventsBuffers = new IdentityHashMap<Buffer, DeviceEvent>();
        this.writeEventsPointers = new IdentityHashMap<Pointer, DeviceEvent>();
        this.writeEventsFloats = new IdentityHashMap<float[], DeviceEvent>();
        this.writeEventsDoubles = new IdentityHashMap<double[], DeviceEvent>();
        this.writeEventsInts = new IdentityHashMap<int[], DeviceEvent>();
        this.writeEventsBytes = new IdentityHashMap<byte[], DeviceEvent>();

        this.writeEventsBuffersInversed = new IdentityHashMap<DeviceEvent, Buffer>();
        this.writeEventsPointersInversed = new IdentityHashMap<DeviceEvent, Pointer>();
        this.writeEventsFloatsInversed = new IdentityHashMap<DeviceEvent, float[]>();
        this.writeEventsDoublesInversed = new IdentityHashMap<DeviceEvent, double[]>();
        this.writeEventsIntsInversed = new IdentityHashMap<DeviceEvent, int[]>();
        this.writeEventsBytesInversed = new IdentityHashMap<DeviceEvent, byte[]>();

        this.executeEventsBuffers = new IdentityHashMap<Buffer, ArrayList<DeviceEvent>>();
        this.executeEventsPointers = new IdentityHashMap<Pointer, ArrayList<DeviceEvent>>();
        this.executeEventsFloats = new IdentityHashMap<float[], ArrayList<DeviceEvent>>();
        this.executeEventsDoubles = new IdentityHashMap<double[], ArrayList<DeviceEvent>>();
        this.executeEventsInts = new IdentityHashMap<int[], ArrayList<DeviceEvent>>();
        this.executeEventsBytes = new IdentityHashMap<byte[], ArrayList<DeviceEvent>>();

        this.memoryReserved = 0;
    }

    public abstract DeviceEvent writeNoCreateBuffer(CommandStream q, DeviceEvent[] waitEvents, boolean async, long size, Pointer hostPtr,
            Pointer devicePtr);

    public abstract DeviceEvent enqueueReadBuffer(CommandStream q, boolean asynch, DeviceEvent[] waitEvents, long size,
            Pointer hostPtr, Pointer devicePtr);

    public abstract <T> T withAllocationError(Supplier<T> s);

    public abstract void waitEvents(DeviceEvent[] waitEvents);

    public abstract KernelLaunch createLaunch(String name, String threadname);

    public abstract boolean registeredKernel(String name);

    public abstract Pointer createBuffer(Argument.Direction d, long size);

    public abstract void addKernel(String kernelSource, String fileName);

    /*
     * General device management
     */

    /**
     * Get the memory capacity of this device.
     *
     * @return the capacity of this device in bytes.
     */
    public long getMemoryCapacity() {
        return info.getMemSize();
    }

    /**
     * Get the type name of this device (i.e., fermi).
     *
     * @return the class name.
     */
    public String getName() {
        return info.getName();
    }

    /**
     * Get the nickname of this device (i.e. gtx980).
     *
     * @return the nickname.
     */
    public String getNickName() {
        return info.getNickName();
    }

    /**
     * Compares this device with <code>Device</code> device in terms of when a device can launch kernels.
     * <p>
     * This method compares the number of kernel launches on a device to find out when a device is going to terminate.
     *
     * @param device
     *            the device to compare against
     * @return &lt; 0 if this device is expected to terminate its kernels sooner than device, 0 if this device is expected to
     *         terminate its kernels at the same time as the other device, &gt; 0 if this device is expected to terminate its
     *         kernels later than the other device.
     */
    @Override
    public int compareTo(Device device) {
        double factor;
        double expectedTermination;
        double expectedTerminationDevice;
        synchronized (this) {
            factor = 1.0 / info.getSpeed();
            expectedTermination = getNrKernelLaunches() + getLaunched() + 1;
        }
        synchronized (device) {
            factor = factor * device.info.getSpeed();
            expectedTerminationDevice = device.getNrKernelLaunches() + device.getLaunched() + 1;
        }

        expectedTermination *= factor;

        if (logger.isInfoEnabled()) {
            logger.info("compareTo: " + this + ": expectedTermination = " + expectedTermination + ", " + device
                    + ": expectedTermination = " + expectedTerminationDevice);
        }

        return expectedTermination < expectedTerminationDevice ? -1 : expectedTermination == expectedTerminationDevice ? 0 : 1;
    }

    @Override
    public String toString() {
        return info.toString() + "(" + System.identityHashCode(this) + ")";
    }

    /*
     * Managing memory on the device
     */

    /**
     * Allocates <code>size</code> bytes of memory on the device.
     *
     * @param size
     *            the number of bytes to allocate
     * @return a <code>Pointer</code> to the memory on the device
     */
    public Pointer allocate(long size) {
        PointerArgument a = new PointerArgument(this, getReadQueue());
        a.createBuffer(this, size, null);

        Pointer pointer = a.getPointer();
        synchronized (pointerArguments) {
            pointerArguments.put(pointer, a);
        }
        return pointer;
    }

    /*
     * The copy methods call performCopy that will make a new BufferArgument. The
     * constructor of this BufferArgument calls Argument.writeBuffer that will do
     * an clEnqueueWriteBuffer without any events to wait on. This method will
     * return an event that is registered with registerEvent() and then added to
     * writeBufferEvents. This is an ArrayList of events that is will only hold
     * one element. This element, a DeviceEvent is mapped to the key Buffer a in
     * the writeEventsBuffers map.
     *
     * Synchronization comments.
     *
     * PerformCopy keeps track of which Buffer belongs to which BufferArgument.
     * If another copy is being done of the same Buffer, we increment
     * ArrayArgument.referenceCount.
     *
     */
    /**
     * Copy a buffer to the device. After completion, the data has a representation on the host and on the device. This means that
     * after a kernel execution updates the data, the host representation can be updated with a {@link #get(Buffer)}.
     *
     * @param buffer
     *            a <code>Buffer</code> to be copied to the device
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(Buffer buffer, Argument.Direction d) {
        performCopy(bufferArguments, writeEventsBuffers, writeEventsBuffersInversed, buffer,
                (writeBufferEvents) -> new BufferArgument(this, getWriteQueue(), getReadQueue(), writeBufferEvents, buffer, d),
                () -> buffer.capacity());
    }

    /**
     * Copy an array of floats to the device. After completion, the data has a representation on the host and on the device. This
     * means that after a kernel execution updates the data, the host representation can be updated with a {@link #get(float[])}.
     *
     * @param a
     *            a <code>float</code> array to be copied to the devie
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(float[] a, Argument.Direction d) {
        performCopy(floatArrayArguments, writeEventsFloats, writeEventsFloatsInversed, a,
                (x) -> new FloatArrayArgument(this, getWriteQueue(), getReadQueue(), x, a, d), () -> a.length * 4);
    }

    /**
     * Copy an array of doubles to the device. After completion, the data has a representation on the host and on the device. This
     * means that after a kernel execution updates the data, the host representation can be updated with a {@link #get(double[])}.
     *
     * @param a
     *            a <code>double</code> array to be copied to the devie
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(double[] a, Argument.Direction d) {
        performCopy(doubleArrayArguments, writeEventsDoubles, writeEventsDoublesInversed, a,
                (x) -> new DoubleArrayArgument(this, getWriteQueue(), getReadQueue(), x, a, d), () -> a.length * 8);
    }

    /**
     * Copy an array of ints to the device. After completion, the data has a representation on the host and on the device. This
     * means that after a kernel execution updates the data, the host representation can be updated with a {@link #get(int[])}.
     *
     * @param a
     *            a <code>int</code> array to be copied to the devie
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(int[] a, Argument.Direction d) {
        performCopy(intArrayArguments, writeEventsInts, writeEventsIntsInversed, a,
                (x) -> new IntArrayArgument(this, getWriteQueue(), getReadQueue(), x, a, d), () -> a.length * 4);
    }

    /**
     * Copy an array of bytes to the device. After completion, the data has a representation on the host and on the device. This
     * means that after a kernel execution updates the data, the host representation can be updated with a {@link #get(byte[])}.
     *
     * @param a
     *            a <code>byte</code> array to be copied to the devie
     * @param d
     *            indicates the direction of the copied value (only for reading, only for writing, or for both)
     */
    public void copy(byte[] a, Argument.Direction d) {
        performCopy(byteArrayArguments, writeEventsBytes, writeEventsBytesInversed, a,
                (x) -> new ByteArrayArgument(this, getWriteQueue(), getReadQueue(), x, a, d), () -> a.length);
    }

    /**
     * Copy a buffer to memory on the device. Compared to {@link #copy(Buffer,Argument.Direction)}, this version is not coupled
     * with <code>from</code>.
     *
     * @param from
     *            a <code>Buffer</code> to be copied to the device
     * @param to
     *            a <code>Pointer</code> representing the address of the memory to which is copied
     */
    public void copy(Buffer from, Pointer to) {
        PointerArgument a;
        DeviceEvent writePointerEvent = null;
        synchronized (pointerArguments) {
            a = pointerArguments.get(to);
            if (a != null) {
                writePointerEvent = a.writeBufferNoCreateBuffer(this, getWriteQueue(), null, from.capacity(),
                        cashmere.getPlatform().toPointer(from.getByteBuffer()));
            } else {
                throw new Error("Unknown pointer: device = " + this + ", ptr = "  + to);
            }
        }

        synchronized (writeEventsPointers) {
            Logger logger = Device.logger.isDebugEnabled() ? Device.logger : Device.eventlogger;
            if (logger.isDebugEnabled()) {
                logger.debug("Copy Buffer to Pointer: event = " + writePointerEvent);
                logger.debug("storing last event in Device.writeEvents<type>");
                logger.debug("storing last event in Device.writeEventsInversed<type>");
            }
            DeviceEvent old_event = writeEventsPointers.remove(to);
            if (old_event != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("old {} associated with {}, about to clean old event", old_event, to);
                }
                old_event.clean();
                Pointer p = writeEventsPointersInversed.remove(old_event); // Added
                if (p == null) {
                    throw new Error("Inconsistency in writeEventsPointers");
                }
            }
            if (writePointerEvent != null) {
                writeEventsPointers.put(to, writePointerEvent);
                writeEventsPointersInversed.put(writePointerEvent, to);
            }
        }
    }

    /**
     * Whether the buffer is available on the device.
     *
     * @param buffer
     *            the <code>Buffer</code> of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(Buffer buffer) {
        return performAvailable(buffer, bufferArguments);
    }

    /**
     * Whether the array of floats is available on the device.
     *
     * @param a
     *            the array of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(float[] a) {
        return performAvailable(a, floatArrayArguments);
    }

    /**
     * Whether the array of bytes is available on the device.
     *
     * @param a
     *            the array of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(byte[] a) {
        return performAvailable(a, byteArrayArguments);
    }

    /**
     * Whether the array of ints is available on the device.
     *
     * @param a
     *            the array of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(int[] a) {
        return performAvailable(a, intArrayArguments);
    }

    /**
     * Whether the array of doubles is available on the device.
     *
     * @param a
     *            the array of which is checked to be available on the device
     * @return true if and only if the buffer is on the device
     */
    public boolean available(double[] a) {
        return performAvailable(a, doubleArrayArguments);
    }

    /**
     * Get the <code>Buffer</code> from the device. The <code>Buffer</code> is not removed from the device.
     *
     * @param buffer
     *            a <code>Buffer</code> in which the data
     */
    public void get(Buffer buffer) {
        performGet(buffer, bufferArguments, executeEventsBuffers);
    }

    /**
     * Get the array of floats from the device. The representation of the array on the device is not removed from the device.
     *
     * @param a
     *            the <code>float</code> array to which the contents of the device representation is copied
     */
    public void get(float[] a) {
        performGet(a, floatArrayArguments, executeEventsFloats);
    }

    /**
     * Get the array of doubles from the device. The representation of the array on the device is not removed from the device.
     *
     * @param a
     *            the <code>double</code> array to which the contents of the device representation is copied
     */
    public void get(double[] a) {
        performGet(a, doubleArrayArguments, executeEventsDoubles);
    }

    /**
     * Get the array of ints from the device. The representation of the array on the device is not removed from the device.
     *
     * @param a
     *            the <code>int</code> array to which the contents of the device representation is copied
     */
    public void get(int[] a) {
        performGet(a, intArrayArguments, executeEventsInts);
    }

    /**
     * Get the array of bytes from the device. The representation of the array on the device is not removed from the device.
     *
     * @param a
     *            the <code>byte</code> array to which the contents of the device representation is copied
     */
    public void get(byte[] a) {
        performGet(a, byteArrayArguments, executeEventsBytes);
    }

    /**
     * Get the contents of the memory on the device represented by <code>Pointer</code> <code>from</code> into <code>to</code>.
     *
     * @param to
     *            the <code>Buffer</code> to copy to
     * @param from
     *            the memory from which is copied
     */
    public void get(Buffer to, Pointer from) {
        performPointerGet(from, cashmere.getPlatform().toPointer(to.getByteBuffer()), to.capacity());
    }

    /**
     * Get the contents of the memory on the device represented by <code>Pointer</code> <code>from</code> into <code>to</code>.
     *
     * @param to
     *            the array of doubles to copy to
     * @param from
     *            the memory from which is copied
     */
    public void get(double[] to, Pointer from) {
        performPointerGet(from, cashmere.getPlatform().toPointer(to), to.length * Platform.DOUBLE_SIZE);
    }

    /**
     * Get the contents of the memory on the device represented by <code>Pointer</code> <code>from</code> into <code>to</code>.
     *
     * @param to
     *            the array of floats to copy to
     * @param from
     *            the memory from which is copied
     */
    public void get(float[] to, Pointer from) {
        performPointerGet(from, cashmere.getPlatform().toPointer(to), to.length * Platform.FLOAT_SIZE);
    }

    /*
     * Synchronization comments
     *
     * Protects: bufferArguments, referenceCount, executeEventsBuffers,
     * writeEventsBuffers
     */
    /**
     * Clean <code>Buffer</code> from the device.
     *
     * @param buffer
     *            the <code>Buffer</code> to be cleaned
     * @return the reference count of the <code>Buffer</code> value
     */
    public int clean(Buffer buffer) {
        if (buffer != null) {
            return performClean(buffer, bufferArguments, buffer.capacity(), () -> {
                return removeEvent(buffer, executeEventsBuffers, writeEventsBuffers, writeEventsBuffersInversed);
            });
        }
        return -1;
    }

    /**
     * Clean the float array from the device.
     *
     * @param a
     *            the array to be cleaned
     * @return the reference count of the array
     */
    public int clean(float[] a) {
        if (a != null) {
            return performClean(a, floatArrayArguments, a.length * 4, () -> {
                return removeEvent(a, executeEventsFloats, writeEventsFloats, writeEventsFloatsInversed);
            });
        }
        return -1;
    }

    /**
     * Clean the byte array from the device.
     *
     * @param a
     *            the array to be cleaned
     * @return the reference count of the array
     */
    public int clean(byte[] a) {
        if (a != null) {
            return performClean(a, byteArrayArguments, a.length, () -> {
                return removeEvent(a, executeEventsBytes, writeEventsBytes, writeEventsBytesInversed);
            });
        }
        return -1;
    }

    /**
     * Clean the int array from the device.
     *
     * @param a
     *            the array to be cleaned
     * @return the reference count of the array
     */
    public int clean(int[] a) {
        if (a != null) {
            return performClean(a, intArrayArguments, a.length * 4, () -> {
                return removeEvent(a, executeEventsInts, writeEventsInts, writeEventsIntsInversed);
            });
        }
        return -1;
    }

    /**
     * Clean the double array from the device.
     *
     * @param a
     *            the array to be cleaned
     * @return the reference count of the array
     */
    public int clean(double[] a) {
        if (a != null) {
            return performClean(a, doubleArrayArguments, a.length * 8, () -> {
                return removeEvent(a, executeEventsDoubles, writeEventsDoubles, writeEventsDoublesInversed);
            });
        }
        return -1;
    }

    /*
     * Package methods
     */

    /*
     * Initialization of the device
     */

    public void initializeLibrary(InitLibraryFunction func) {
        func.initialize(this, getExecuteQueue());
    }

    public void deinitializeLibrary(DeInitLibraryFunction func) {
        func.deinitialize();
    }

    public FloatArrayArgument getArgument(float[] a) {
        return getArgumentGeneric(a, floatArrayArguments);
    }

    public DoubleArrayArgument getArgument(double[] a) {
        return getArgumentGeneric(a, doubleArrayArguments);
    }

    public BufferArgument getArgument(Buffer a) {
        return getArgumentGeneric(a, bufferArguments);
    }

    public PointerArgument getArgument(Pointer a) {
        return getArgumentGeneric(a, pointerArguments);
    }

    public IntArrayArgument getArgument(int[] a) {
        return getArgumentGeneric(a, intArrayArguments);
    }

    public ByteArrayArgument getArgument(byte[] a) {
        return getArgumentGeneric(a, byteArrayArguments);
    }

    /*
     * Setting/querying the state of the device
     */

    public synchronized void setBusy() {
        setNrKernelLaunches(getNrKernelLaunches() + 1);
    }

    public synchronized void launched() {
        setLaunched(getLaunched() + 1);
    }

    public synchronized void setNotBusy() {
        if (getNrKernelLaunches() > 0) {
            // Note: not reliable since setBusy is only called for getKernel() and getLibFunc(),
            // while setNotBusy is called when a launch is actually finished.
            setNrKernelLaunches(getNrKernelLaunches() - 1);
        }
        setLaunched(getLaunched() - 1);
    }

    public boolean asynchReads() {
        return cashmere.isAsynchReads();
    }

    /*
     * Handling events
     */

    public DeviceEvent getWriteEvent(float[] a) {
        return getWriteEventGeneric(a, writeEventsFloats);
    }

    public DeviceEvent getWriteEvent(double[] a) {
        return getWriteEventGeneric(a, writeEventsDoubles);
    }

    public DeviceEvent getWriteEvent(int[] a) {
        return getWriteEventGeneric(a, writeEventsInts);
    }

    public DeviceEvent getWriteEvent(byte[] a) {
        return getWriteEventGeneric(a, writeEventsBytes);
    }

    public DeviceEvent getWriteEvent(Buffer a) {
        return getWriteEventGeneric(a, writeEventsBuffers);
    }

    public DeviceEvent getWriteEvent(Pointer a) {
        return getWriteEventGeneric(a, writeEventsPointers);
    }

    public void addExecuteEvent(float[] a, DeviceEvent event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsFloats));
    }

    public void addExecuteEvent(double[] a, DeviceEvent event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsDoubles));
    }

    public void addExecuteEvent(int[] a, DeviceEvent event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsInts));
    }

    public void addExecuteEvent(byte[] a, DeviceEvent event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsBytes));
    }

    public void addExecuteEvent(Buffer a, DeviceEvent event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsBuffers));
    }

    public void addExecuteEvent(Pointer a, DeviceEvent event) {
        processExecuteEvent(event, getExecuteEvents(a, executeEventsPointers));
    }

    public void removeExecuteEvent(float[] a, DeviceEvent event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsFloats));
    }

    public void removeExecuteEvent(double[] a, DeviceEvent event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsDoubles));
    }

    public void removeExecuteEvent(int[] a, DeviceEvent event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsInts));
    }

    public void removeExecuteEvent(byte[] a, DeviceEvent event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsBytes));
    }

    public void removeExecuteEvent(Buffer a, DeviceEvent event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsBuffers));
    }

    public void removeExecuteEvent(Pointer a, DeviceEvent event) {
        removeExecuteEvent(event, getExecuteEvents(a, executeEventsPointers));
    }

    public void cleanWriteEvents(ArrayList<DeviceEvent> events) {
        for (DeviceEvent event : events) {
            cleanWriteEvent(event, writeEventsFloats, writeEventsFloatsInversed);
            cleanWriteEvent(event, writeEventsDoubles, writeEventsDoublesInversed);
            cleanWriteEvent(event, writeEventsInts, writeEventsIntsInversed);
            cleanWriteEvent(event, writeEventsBytes, writeEventsBytesInversed);
            cleanWriteEvent(event, writeEventsBuffers, writeEventsBuffersInversed);
            cleanWriteEvent(event, writeEventsPointers, writeEventsPointersInversed);
        }
    }

    public int getNrKernelLaunches() {
        return nrKernelLaunches;
    }

    public void setNrKernelLaunches(int nrKernelLaunches) {
        this.nrKernelLaunches = nrKernelLaunches;
    }

    public int getLaunched() {
        return launched;
    }

    public void setLaunched(int launched) {
        this.launched = launched;
    }

    public CommandStream getWriteQueue() {
        return writeQueue;
    }

    public void setWriteQueue(CommandStream writeQueue) {
        this.writeQueue = writeQueue;
    }

    public CommandStream getExecuteQueue() {
        return executeQueue;
    }

    public void setExecuteQueue(CommandStream executeQueue) {
        this.executeQueue = executeQueue;
    }

    public CommandStream getReadQueue() {
        return readQueue;
    }

    public void setReadQueue(CommandStream readQueue) {
        this.readQueue = readQueue;
    }

    /*
     * Debugging
     */

    void showExecuteEvents() {
        if (eventlogger.isDebugEnabled()) {
            showEvents(executeEventsBuffers, "Buffer");
            showEvents(executeEventsPointers, "Pointer");
            showEvents(executeEventsFloats, "Floats");
            showEvents(executeEventsDoubles, "Doubles");
            showEvents(executeEventsInts, "Ints");
            showEvents(executeEventsBytes, "Bytes");
        }
    }

    // Private methods

    /*
     * Managing arguments/memory on the device
     */

    private <K, V extends ArrayArgument> void performCopy(Map<K, V> map, Map<K, DeviceEvent> writeEvents,
            Map<DeviceEvent, K> writeEventsInversed, K k, Function<ArrayList<DeviceEvent>, V> makeNewArgument,
            Supplier<Integer> size) {

        boolean madeNewArgument;
        ArrayList<DeviceEvent> writeBufferEvents = null;
        synchronized (map) {
            if (!map.containsKey(k)) {
                writeBufferEvents = new ArrayList<DeviceEvent>();
                map.put(k, makeNewArgument.apply(writeBufferEvents));
                madeNewArgument = true;
            } else {
                V v = map.get(k);
                v.incRefCount();
                madeNewArgument = false;
            }
        }

        if (madeNewArgument) {
            if (memlogger.isDebugEnabled()) {
                synchronized (this) {
                    nrBytesAllocated += size.get();
                    memlogger.debug(String.format("Allocated: %6s, total: %s: %s", toStringBytes(size.get()),
                            toStringBytes(nrBytesAllocated), map.get(k)));
                }

            }
            synchronized (writeEvents) {
                if (writeBufferEvents.size() == 1) {
                    if (eventlogger.isDebugEnabled()) {
                        eventlogger.debug("Copy Buffer: event = " + writeBufferEvents.get(0));
                        eventlogger.debug("storing last event in Device.writeEvents<type>");
                        eventlogger.debug("storing last event in Device.writeEventsInversed<type>");
                    }
                    DeviceEvent event = writeBufferEvents.get(0);
                    writeEvents.put(k, event);
                    writeEventsInversed.put(event, k);
                } else {
                    throw new Error("Should not happen");
                }
            }
        }
    }

    private <K, V extends ArrayArgument> boolean performAvailable(K k, Map<K, V> map) {
        synchronized (map) {
            return map.get(k) != null;
        }
    }

    private <K, V extends ArrayArgument> void performGet(K k, Map<K, V> map, Map<K, ArrayList<DeviceEvent>> executeEvents) {
        V v;
        synchronized (map) {
            v = map.get(k);
        }
        ArrayList<DeviceEvent> execEvents = getExecuteEvents(k, executeEvents);
        ArrayList<DeviceEvent> readBufferEvents = getReadBufferEvents();
        v.scheduleReads(execEvents, readBufferEvents, false);
        releaseEvents(readBufferEvents);
        releaseEvents(execEvents);
        // we just remove it immediately
        synchronized (executeEvents) {
            executeEvents.remove(k);
        }
    }

    private void performPointerGet(Pointer from, Pointer to, long size) {
        PointerArgument a;
        synchronized (pointerArguments) {
            a = pointerArguments.get(from);
        }
        ArrayList<DeviceEvent> execEvents = getExecuteEvents(from, executeEventsPointers);
        ArrayList<DeviceEvent> readBufferEvents = getReadBufferEvents();
        a.scheduleReads(to, size, execEvents, readBufferEvents, false);
        synchronized (executeEventsPointers) {
            executeEventsPointers.remove(from);
        }
        releaseEvents(readBufferEvents);
        releaseEvents(execEvents);
    }

    private <K, V extends ArrayArgument> int performClean(K k, Map<K, V> map, int size, Supplier<DeviceEvent> cleanEvents) {
        V v;
        synchronized (map) {
            v = map.get(k);
        }
        if (v == null) {
            return -1;
        }

        int refCount = v.decrementAndGetRefCount();

        if (refCount == 0) {
            synchronized (map) {
                map.remove(k);
            }
            cleanEvents.get();

            if (memlogger.isDebugEnabled()) {
                synchronized (this) {
                    nrBytesAllocated -= size;
                    memlogger.debug(String.format("Deallocated: %6s, total: %s %s", toStringBytes(size),
                            toStringBytes(nrBytesAllocated), v));
                }
            }
        }

        return refCount;
    }

    private <K, V extends ArrayArgument> V getArgumentGeneric(K k, Map<K, V> map) {
        synchronized (map) {
            return map.get(k);
        }
    }

    /*
     * Handling events
     */

    private void releaseEvents(ArrayList<DeviceEvent> events) {
        for (DeviceEvent event : events) {
            event.clean();
        }
        events.clear();
    }

    private <K> DeviceEvent removeEvent(K k, Map<K, ArrayList<DeviceEvent>> executeEvents, Map<K, DeviceEvent> writeEvents,
            Map<DeviceEvent, K> writeEventsInversed) {

        synchronized (executeEvents) {
            executeEvents.remove(k);
        }
        synchronized (writeEvents) {
            DeviceEvent event = writeEvents.remove(k);
            if (event != null) {
                K kStored = writeEventsInversed.get(event);
                if (eventlogger.isDebugEnabled()) {
                    eventlogger.debug("removing {} from Device.writeEvents<type>", event);
                }
                if (kStored == k) {
                    writeEventsInversed.remove(event);
                    eventlogger.debug("removing {} from Device.writeEventsInversed<type>", event);
                    eventlogger.debug("about to clean {}", event);
                    event.clean();
                }
            }
            return event;
        }
    }

    private <K> ArrayList<DeviceEvent> getExecuteEvents(K k, Map<K, ArrayList<DeviceEvent>> map) {
        ArrayList<DeviceEvent> events;
        synchronized (map) {
            events = map.get(k);
            if (events == null) {
                events = new ArrayList<DeviceEvent>();
                map.put(k, events);
            }
        }
        return events;
    }

    private <K> DeviceEvent getWriteEventGeneric(K k, Map<K, DeviceEvent> writeEvents) {
        synchronized (writeEvents) {
            return writeEvents.get(k);
        }
    }

    private synchronized ArrayList<DeviceEvent> getReadBufferEvents() {
        String thread = Thread.currentThread().getName();
        ArrayList<DeviceEvent> readBufferEvents = readBufferEventsMap.get(thread);
        if (readBufferEvents == null) {
            readBufferEvents = new ArrayList<DeviceEvent>();
            readBufferEventsMap.put(thread, readBufferEvents);
        }
        return readBufferEvents;
    }

    private void processExecuteEvent(DeviceEvent event, ArrayList<DeviceEvent> events) {
        synchronized (events) {
            events.add(event);
        }
        if (eventlogger.isDebugEnabled()) {
            eventlogger.debug("storing {} in Device.executeEvents<type> {}", event, events);
        }
    }

    private void removeExecuteEvent(DeviceEvent event, ArrayList<DeviceEvent> events) {
        synchronized (events) {
            events.remove(event);
        }
        if (eventlogger.isDebugEnabled()) {
            eventlogger.debug("removing {} from Device.executeEvents<type>", event);
        }
    }

    private <T> void cleanWriteEvent(DeviceEvent event, Map<T, DeviceEvent> writeEvents,
            Map<DeviceEvent, T> writeEventsInversed) {
        synchronized (writeEvents) {
            T t = writeEventsInversed.get(event);
            if (t != null) {
                DeviceEvent storedEvent = writeEvents.get(t);
                if (storedEvent == event) {
                    writeEvents.remove(t);
                    writeEventsInversed.remove(event);
                    if (eventlogger.isDebugEnabled()) {
                        eventlogger.debug("removing {} from Device.writeEvents<type>", event);
                        eventlogger.debug("removing {} from Device.writeEventsInversed<type>", event);
                        eventlogger.debug("about to clean {}", event);
                    }
                    event.clean();
                }
            }
        }
    }

    private <K> void showEvents(Map<K, ArrayList<DeviceEvent>> executeEvents, String type) {
        int size = executeEvents.size();
        if (size > 0) {
            eventlogger.debug("executeEvents{} has {} elements", type, size);
            Collection<ArrayList<DeviceEvent>> values = executeEvents.values();
            for (ArrayList<DeviceEvent> events : values) {
                eventlogger.debug("  key 1:");
                for (DeviceEvent event : events) {
                    eventlogger.debug("    {}", event);
                    // Event.showEvent("execute", event);
                    // can segfault if the even has been released
                }
            }
        }
    }

}
