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
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.Argument;
import ibis.cashmere.constellation.deviceAPI.CommandStream;
import ibis.cashmere.constellation.deviceAPI.Device;
import ibis.cashmere.constellation.deviceAPI.DeviceEvent;
import ibis.cashmere.constellation.deviceAPI.Platform;
import ibis.cashmere.constellation.deviceAPI.Pointer;
import ibis.cashmere.constellation.deviceImpl.jocl.OpenCLKernelLaunch;

/**
 * The abstract base class for {@link OpenCLKernelLaunch} and {@link LibFuncLaunch} that contains shared code.
 */
public abstract class Launch {

    protected static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Launch");

    protected String name;
    protected String threadName;
    protected Device device;

    protected int nrArgs;
    protected ArrayList<Argument> argsToClean;

    protected CommandStream writeQueue;
    protected CommandStream executeQueue;
    protected CommandStream readQueue;

    protected ArrayList<DeviceEvent> writeBufferEvents;
    protected ArrayList<DeviceEvent> executeEvents;
    protected ArrayList<DeviceEvent> readBufferEvents;

    protected boolean launched;
    protected boolean finished;

    private static final int NR_LAUNCHES_TO_RETAIN = 2;

    private Set<float[]> noCopyFloats;
    private Set<double[]> noCopyDoubles;
    private Set<int[]> noCopyInts;
    private Set<byte[]> noCopyBytes;
    private Set<Buffer> noCopyBuffers;
    private Set<Pointer> noCopyPointers;

    private static ThreadLocal<Deque<Launch>> launches = ThreadLocal.<Deque<Launch>> withInitial(() -> new LinkedList<Launch>());

    // A Launch can only be created from a subclass
    protected Launch(String name, String threadName, Device device) {
        this.name = name;
        this.threadName = threadName;
        this.device = device;

        this.nrArgs = 0;
        this.argsToClean = new ArrayList<Argument>();

        this.writeQueue = device.getWriteQueue();
        this.executeQueue = device.getExecuteQueue();
        this.readQueue = device.getReadQueue();

        this.writeBufferEvents = new ArrayList<DeviceEvent>();
        this.executeEvents = new ArrayList<DeviceEvent>();
        this.readBufferEvents = new ArrayList<DeviceEvent>();

        this.launched = false;
        this.finished = false;

        this.noCopyFloats = new HashSet<float[]>();
        this.noCopyDoubles = new HashSet<double[]>();
        this.noCopyInts = new HashSet<int[]>();
        this.noCopyBytes = new HashSet<byte[]>();
        this.noCopyBuffers = new HashSet<Buffer>();
        this.noCopyPointers = Collections.newSetFromMap(new IdentityHashMap<Pointer, Boolean>());
    }

    /*
     * Public code, generic to Kernel and LibFunc launches
     */
    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param i
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(int i, Argument.Direction d) {
        IntArgument arg = new IntArgument(i, d);
        setArgument(Platform.INT_SIZE, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param f
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(float f, Argument.Direction d) {
        FloatArgument arg = new FloatArgument(f, d);
        setArgument(Platform.FLOAT_SIZE, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param f
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(double f, Argument.Direction d) {
        DoubleArgument arg = new DoubleArgument(f, d);
        setArgument(Platform.DOUBLE_SIZE, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(float[][] a, Argument.Direction d) {
        FloatArray2DArgument arg = new FloatArray2DArgument(device, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Platform.MEM_SIZE, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(float[] a, Argument.Direction d) {
        FloatArrayArgument arg = new FloatArrayArgument(device, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Platform.MEM_SIZE, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(double[] a, Argument.Direction d) {
        DoubleArrayArgument arg = new DoubleArrayArgument(device, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Platform.MEM_SIZE, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param buffer
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(Buffer buffer, Argument.Direction d) {
        BufferArgument arg = new BufferArgument(device, writeQueue, readQueue, writeBufferEvents, buffer, d);
        setArgument(Platform.MEM_SIZE, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. This method will throw an UnsupportedOperationException because <code>Pointer</code>
     * <code>p</code> is a pointer that points to device memory. Hence, it cannot be copied. The "noCopy" variant should be used.
     * This variant is available in the Cashmere library because the MCL compiler may generate this variant.
     *
     * @param p
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(Pointer p, Argument.Direction d) {
        throw new UnsupportedOperationException("Cannot set pointer argument and expect to copy");
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(int[] a, Argument.Direction d) {
        IntArrayArgument arg = new IntArrayArgument(device, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Platform.MEM_SIZE, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The value will be copied to the device before the kernel launches.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgument(byte[] a, Argument.Direction d) {
        ByteArrayArgument arg = new ByteArrayArgument(device, writeQueue, readQueue, writeBufferEvents, a, d);
        setArgument(Platform.MEM_SIZE, arg);
        argsToClean.add(arg);
    }

    /**
     * Set an argument for this launch. The user is responsible for copying the data to the device using
     * {@link Device#copy(float[],Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(float[] a, Argument.Direction d) {
        FloatArrayArgument arg = device.getArgument(a);
        setArgument(Platform.MEM_SIZE, arg);

        noCopyFloats.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is responsible for copying the data to the device using
     * {@link Device#copy(double[],Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(double[] a, Argument.Direction d) {
        DoubleArrayArgument arg = device.getArgument(a);
        setArgument(Platform.MEM_SIZE, arg);

        noCopyDoubles.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is responsible for copying the data to the device using
     * {@link Device#copy(Buffer,Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(Buffer a, Argument.Direction d) {
        BufferArgument arg = device.getArgument(a);
        setArgument(Platform.MEM_SIZE, arg);

        noCopyBuffers.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is responsible for copying the data to the device using
     * {@link Device#copy(Buffer,Pointer)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(Pointer a, Argument.Direction d) {
        PointerArgument arg = device.getArgument(a);
        setArgument(Platform.MEM_SIZE, arg);

        noCopyPointers.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is responsible for copying the data to the device using
     * {@link Device#copy(int[],Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(int[] a, Argument.Direction d) {
        IntArrayArgument arg = device.getArgument(a);
        setArgument(Platform.MEM_SIZE, arg);

        noCopyInts.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Set an argument for this launch. The user is responsible for copying the data to the device using
     * {@link Device#copy(byte[],Argument.Direction)}.
     *
     * @param a
     *            the argument to be set for this kernel
     * @param d
     *            indicates whether the value is only read, only written, or both
     */
    public void setArgumentNoCopy(byte[] a, Argument.Direction d) {
        ByteArrayArgument arg = device.getArgument(a);
        setArgument(Platform.MEM_SIZE, arg);

        noCopyBytes.add(a);
        // make the execute dependent on this copy.
        addWriteEvent(device.getWriteEvent(a));
    }

    /**
     * Get the name of the device.
     *
     * @return the name of the device.
     */
    public String getDeviceName() {
        return device.getName();
    }

    /*
     * Methods for the rest of the package
     */

    String getThread() {
        return threadName;
    }

    /*
     * Methods for subclasses
     */

    protected void registerExecuteEventToDevice(DeviceEvent event) {
        for (float[] fs : noCopyFloats) {
            device.addExecuteEvent(fs, event);
        }
        for (double[] ds : noCopyDoubles) {
            device.addExecuteEvent(ds, event);
        }
        for (int[] is : noCopyInts) {
            device.addExecuteEvent(is, event);
        }
        for (byte[] bs : noCopyBytes) {
            device.addExecuteEvent(bs, event);
        }
        for (Buffer bs : noCopyBuffers) {
            device.addExecuteEvent(bs, event);
        }
        for (Pointer p : noCopyPointers) {
            device.addExecuteEvent(p, event);
        }
    }

    protected void registerWithThread() {
        // register this launch with the thread. The thread will
        // clean all launch up to NR_LAUNCHES_TO_RETAIN to make sure all
        // the execute events are gone.

        cleanLaunches(NR_LAUNCHES_TO_RETAIN);
        launches.get().offerLast(this);
    }

    protected void clean() {
        removeExecuteEventsFromDevice(executeEvents);
        clean("execute", executeEvents);
        device.cleanWriteEvents(writeBufferEvents);
        clean("writeBuffer", writeBufferEvents);
        clean("readBuffer", readBufferEvents);
        clearNoCopies();
    }

    protected void finish() {
        if (!finished) {
            if (executeEvents.size() != 0) {
                if (device.asynchReads()) {
                    scheduleReadsDirectBuffers();
                }

                waitForExecEvents();

                if (device.asynchReads()) {
                    cleanAsynchronousArguments();
                } else {
                    cleanArguments();
                }

                finished = true;
                device.setNotBusy();
            } else {
                throw new Error("launch not called yet");
            }
        }
    }

    protected abstract void setArgument(int size, Argument arg);

    /*
     * Methods with private access
     */

    private static void cleanLaunches(int nrLaunchesToRetain) {
        int nrLaunchesToClean = Math.max(launches.get().size() - nrLaunchesToRetain, 0);
        for (int i = 0; i < nrLaunchesToClean; i++) {
            Launch l = launches.get().pollFirst();
            l.clean();
        }
    }

    private void clearNoCopies() {
        noCopyFloats.clear();
        noCopyDoubles.clear();
        noCopyInts.clear();
        noCopyBytes.clear();
        noCopyBuffers.clear();
        noCopyPointers.clear();
    }

    private void removeExecuteEventsFromDevice(ArrayList<DeviceEvent> executeEvents2) {
        for (DeviceEvent event : executeEvents2) {
            removeExecuteEventFromDevice(event);
        }
    }

    private void removeExecuteEventFromDevice(DeviceEvent event) {
        for (float[] fs : noCopyFloats) {
            device.removeExecuteEvent(fs, event);
        }
        for (double[] ds : noCopyDoubles) {
            device.removeExecuteEvent(ds, event);
        }
        for (int[] is : noCopyInts) {
            device.removeExecuteEvent(is, event);
        }
        for (byte[] bs : noCopyBytes) {
            device.removeExecuteEvent(bs, event);
        }
        for (Buffer bs : noCopyBuffers) {
            device.removeExecuteEvent(bs, event);
        }
        for (Pointer p : noCopyPointers) {
            device.removeExecuteEvent(p, event);
        }
    }

    private void clean(String type, ArrayList<DeviceEvent> events) {
        for (DeviceEvent event : events) {
            event.clean();
        }
        events.clear();
    }

    private void scheduleReadsDirectBuffers() {
        // Schedule reads
        // Problem is: apparently, as soon as reads are scheduled,
        // further writes are apparently delayed until after this
        // read is done. Ugly hack: just sleep a bit before
        // scheduling the read.
        //
        // Is this still a problem? Removed it.
        //
        // try {
        // Thread.sleep(50);
        // } catch(Throwable e) {
        // // ignore
        // }
        for (Argument a : argsToClean) {
            if (a instanceof BufferArgument) {
                BufferArgument b = (BufferArgument) a;
                if (b.isDirect()) {
                    a.scheduleReads(executeEvents, readBufferEvents, true);
                }
            }
        }
    }

    private DeviceEvent[] waitForExecEvents() {
        DeviceEvent[] exevnts = executeEvents.toArray(new DeviceEvent[executeEvents.size()]);
        if (logger.isDebugEnabled()) {
            logger.debug("finish: events to wait for: " + Arrays.toString(exevnts));
        }
        device.waitEvents(exevnts);
        if (logger.isDebugEnabled()) {
            logger.debug("finish: waiting for events done");
        }
        return exevnts;
    }

    private void addExecuteEventToTimer(DeviceEvent event) {
        long start = event.getTime(DeviceEvent.TimeType.TIME_START);
        long end = event.getTime(DeviceEvent.TimeType.TIME_END);
        if (start != 0 && end > start) {
            // Sometimes, end == 0 or start == 0. Don't know why.
            double time = (end - start) / 1e9;
            Cashmere.addTimeForKernel(name, device, time);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("finish: timer stuff done");
        }
    }

    private void cleanAsynchronousArguments() {
        for (Argument a : argsToClean) {
            if (!a.readScheduled()) {
                a.scheduleReads(null, readBufferEvents, true);
            }
        }
        DeviceEvent[] readBufferEventsArray = readBufferEvents.toArray(new DeviceEvent[readBufferEvents.size()]);
        if (readBufferEventsArray.length > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("finish: read buffer events to wait for: " + Arrays.toString(readBufferEventsArray));
            }
            device.waitEvents(readBufferEventsArray);
        }
        for (Argument a : argsToClean) {
            a.clean();
        }
    }

    private void cleanArguments() {
        for (Argument a : argsToClean) {
            if (!a.readScheduled()) {
                a.scheduleReads(null, readBufferEvents, false);
            }
            a.clean();
        }
    }

    private void addWriteEvent(DeviceEvent event) {
        if (event != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Storing {} in Launch.writeBufferEvents", event);
            }
            writeBufferEvents.add(event);
        }
    }
}
