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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.ConstellationConfiguration;
import ibis.constellation.ConstellationCreationException;
import ibis.constellation.ConstellationFactory;
import ibis.constellation.NoSuitableExecutorException;
import ibis.constellation.Timer;
import ibis.util.IPUtils;
import ibis.util.TypedProperties;

/**
 * The entry point to the Cashmere library.
 * <p>
 * The <code>Cashmere</code> class is the main entry point for the library. Cashmere's responsibility is to schedule MCL kernels
 * within one node. A typical setup for a Cashmere program is {@link #initialize initialize} Cashmere and request whether this
 * instance {@link #isMaster}. Non-master instances of Cashmere typically execute {@link #done done} immediately, while the master
 * will launch {@link Activity} instances of {@link Constellation} with {@link #submit(Activity)}. These activities should be set
 * up such that they can be stolen by other nodes in the cluster.
 * <p>
 * Activities may want to launch MCL kernels to the many-core device if there is one available. The MCL compiler will generate an
 * <code>MCL.java</code> file from that will have to be included in the source directory of the Cashmere application. The Cashmere
 * will system will compile those kernels automatically and they will be available with the {@link #getKernel()} methods. To
 * launch a kernel, we can create a {@link KernelLaunch} with {@link Kernel#createLaunch()}. The kernel can then be launched with
 * {@link KernelLaunch#launch(int,int,int,int,int,int) launch}.
 */
public class Cashmere {

    private static final Logger logger = LoggerFactory.getLogger("ibis.cashmere.constellation.Cashmere");

    static Cashmere cashmere = null;

    /*
     * Members for constellation
     */
    private Constellation constellation;

    private final String localBase;

    /*
     * Members for Cashmere
     */

    // Maps a kernel/device combination to a discovered speed.
    private final Map<KernelDevice, Double> kernelSpeeds = new HashMap<KernelDevice, Double>();

    private final boolean asynchReads;

    // Maps an MCL device name to a Device
    private final Map<String, List<Device>> devices = new HashMap<String, List<Device>>();

    // A list of Kernels and Libfuncs
    private final List<ManyCoreUnit> manyCoreUnits = Collections.synchronizedList(new ArrayList<ManyCoreUnit>());

    private final Map<String, Timer> timers = new HashMap<String, Timer>();

    // Keeping track of Library (de)initialization
    private final Map<String, InitLibraryFunction> initLibraryFuncs = new HashMap<String, InitLibraryFunction>();

    private final Map<String, DeInitLibraryFunction> deInitLibraryFuncs = new HashMap<String, DeInitLibraryFunction>();

    private Platform platform;

    /*
     * The public interface to Cashmere
     */

    /**
     * Initializes the <code>Cashmere</code> library. For the parameters not used in this <code>initialize</code> method, default
     * values are used.
     *
     * @param config
     *            the configuration for Constellation
     * @exception ConstellationCreationException
     *                if a {@link Constellation} instance could not be created.
     * @see #initialize(ConstellationConfiguration[], Properties, Map, int, int) initialize
     */
    public static void initialize(ConstellationConfiguration[] config) throws ConstellationCreationException {
        initialize(config, System.getProperties(), new HashMap<String, List<String>>(), 0, 0);
    }

    /**
     * Initializes the <code>Cashmere</code> library. For the parameters not used in this <code>initialize</code> method, default
     * values are used.
     *
     * @param config
     *            the configuration for Constellation
     * @param props
     *            the <code>Properties</code> for Cashmere
     * @exception ConstellationCreationException
     *                if a {@link Constellation} instance could not be created.
     * @see #initialize(ConstellationConfiguration[], Properties, Map, int, int) initialize
     */
    public static void initialize(ConstellationConfiguration[] config, Properties props) throws ConstellationCreationException {

        initialize(config, props, new HashMap<String, List<String>>());
    }

    /**
     * Initializes the <code>Cashmere</code> library. For the parameters not used in this <code>initialize</code> method, default
     * values are used.
     *
     * @param config
     *            the configuration for Constellation
     * @param props
     *            the <code>Properties</code> for Cashmere
     * @param kernelDefines
     *            the define statements for the MCL kernels
     * @exception ConstellationCreationException
     *                if a {@link Constellation} instance could not be created.
     * @see #initialize(ConstellationConfiguration[], Properties, Map, int, int) initialize
     */
    public static void initialize(ConstellationConfiguration[] config, Properties props,
            Map<String, List<String>> kernelDefines) throws ConstellationCreationException {

        initialize(config, props, kernelDefines, 0, 0);
    }

    /**
     * Initializes the <code>Cashmere</code> library.
     *
     * @param config
     *            the configuration for Constellation
     * @param props
     *            the <code>Properties</code> for Cashmere
     * @param kernelDefines
     *            the define statements for the MCL kernels
     * @param nrBuffers
     *            the number of {@link Buffer} instances to allocate
     * @param sizeBuffer
     *            the size of the {@link Buffer} instances
     * @exception ConstellationCreationException
     *                if a {@link Constellation} instance could not be created.
     */
    public static void initialize(ConstellationConfiguration[] config, Properties props, Map<String, List<String>> kernelDefines,
            int nrBuffers, int sizeBuffer) throws ConstellationCreationException {

        if (cashmere == null) {
            cashmere = new Cashmere(config, props, kernelDefines, nrBuffers, sizeBuffer);
        }
    }

    /**
     * Setup a library. The <code>initLibraryFunc</code> and <code>deInitLibraryFunc</code> functions need to implement
     * functionality to set up the library with Cashmere's data structures. To actually
     * register/unregister the library with Cashmere, it necessary to call {@link #initializeLibraries()} and
     * {@link #deinitializeLibraries()} respectively.
     *
     * @param name
     *            a user-defined name for the library
     * @param initLibraryFunc
     *            a function that initializes the library
     * @param deInitLibraryFunc
     *            a function that deinitializes the library
     */
    public static synchronized void setupLibrary(String name, InitLibraryFunction initLibraryFunc,
            DeInitLibraryFunction deInitLibraryFunc) {
        cashmere.initLibraryFuncs.put(name, initLibraryFunc);
        cashmere.deInitLibraryFuncs.put(name, deInitLibraryFunc);
    }

    /**
     * Initialize the libraries that have been set up. The libraries should have been set up with {@link #setupLibrary}.
     *
     */
    public static void initializeLibraries() {
        cashmere.initLibraries();
    }

    /**
     * Deinitialize the libraries that have been set up. The libraries should have been set up with {@link #setupLibrary}.
     *
     */
    public static void deinitializeLibraries() {
        cashmere.deinitLibraries();
    }

    /**
     * Requests whether this node is the {@link Constellation} master.
     *
     * @return true if this node is the {@link Constellation} master.
     */
    public static boolean isMaster() {
        return getConstellation().isMaster();
    }

    /**
     * Submit an {@link Activity}.
     *
     * @param a
     *            the <code>Activity</code> to submit.
     * @return an {@link ActivityIdentifier} that identifies the {@link Activity}
     * @exception NoSuitableExecutorException
     *                if an error occurs
     */
    public static ActivityIdentifier submit(Activity a) throws NoSuitableExecutorException {
        return getConstellation().submit(a);
    }

    /**
     * Retrieve the registered MCL kernel. This method only works if there is only one MCL kernel registered.
     *
     * @return the registered <code>Kernel</code>
     * @exception CashmereNotAvailable
     *                if Cashmere could not initialize because it could not find suitable many-core devices.
     */
    public static synchronized Kernel getKernel() throws CashmereNotAvailable {
        return cashmere.getKernel(null, null, null);
    }

    /**
     * Retrieve the MCL kernel with name <code>name</code>. The fastest available <code>Device</code> is selected.
     *
     * @param name
     *            the name of the kernel
     * @return the <code>Kernel</code> with name <code>name</code>
     * @exception CashmereNotAvailable
     *                if Cashmere could not initialize because it could not find suitable many-core devices.
     */
    public static synchronized Kernel getKernel(String name) throws CashmereNotAvailable {
        return cashmere.getKernel(null, name, null);
    }

    /**
     * Retrieve the MCL kernel with name <code>name</code> for a specific <code>Device</code>.
     *
     * @param name
     *            the name of the kernel
     * @param device
     *            the <code>Device</code> on which the kernel should be registered
     * @return the <code>Kernel</code> with name <code>name</code>
     * @exception CashmereNotAvailable
     *                if Cashmere could not initialize because it could not find suitable many-core devices.
     */
    public static synchronized Kernel getKernel(String name, Device device) throws CashmereNotAvailable {
        return cashmere.getKernel(null, name, device);
    }

    /**
     * Retrieve the library function with library name <code>libraryName</code>.
     *
     * @param libraryName
     *            the name of the library
     * @return the <code>LibFunc</code> associated with library <code>libraryName</code>
     * @exception CashmereNotAvailable
     *                if Cashmere could not initialize because it could not find suitable many-core devices.
     * @exception LibFuncNotAvailable
     *                if the library function has not been set up or initialized
     * @see #setupLibrary
     * @see #initializeLibraries
     */
    public static synchronized LibFunc getLibFunc(String libraryName) throws CashmereNotAvailable, LibFuncNotAvailable {
        return getLibFunc(libraryName, null);
    }

    /**
     * Retrieve the library function with library name <code>libraryName</code>.
     *
     * @param libraryName
     *            the name of the library
     * @param device
     *            the <code>Device</code> on which the library function should be called
     * @return the <code>LibFunc</code> associated with library <code>libraryName</code>
     * @exception CashmereNotAvailable
     *                if Cashmere could not initialize because it could not find suitable many-core devices.
     * @exception LibFuncNotAvailable
     *                if the library function has not been set up or initialized
     * @see #setupLibrary
     * @see #initializeLibraries
     */
    public static synchronized LibFunc getLibFunc(String libraryName, Device device)
            throws CashmereNotAvailable, LibFuncNotAvailable {
        return cashmere.getLibraryFunc(null, libraryName, device);
    }

    /**
     * Get the fastest <code>Device</code> for a specific kernel.
     *
     * @param nameKernel
     *            the name of the kernel
     * @return the fastest available <code>Device</code> associated with the kernel
     * @exception CashmereNotAvailable
     *                if Cashmere could not initialize because it could not find suitable many-core devices.
     */
    public static synchronized Device getDevice(String nameKernel) throws CashmereNotAvailable {
        return cashmere.pickDevice(nameKernel);
    }

    /**
     * Returns a list of all <code>Device</code>s for a specific kernel.
     *
     * @param nameKernel
     *            the name of the kernel
     * @return the list of <code>Device</code>s that can execute this kernel.
     */
    public static List<Device> getDevices(String nameKernel) {
        return cashmere.getDevicesForKernel(nameKernel);
    }

    /**
     * Get the overall timer for the application.
     *
     * @return the overall {@link Timer}
     */
    public static synchronized Timer getOverallTimer() {
        return getConstellation().getOverallTimer();
    }

    /**
     * Get a timer {@link Timer}.
     *
     * @param device
     *            the device name
     * @param thread
     *            the thread name
     * @param action
     *            the name of the action
     * @return the {@link Timer} for this device, thread and action.
     */
    public static synchronized Timer getTimer(String device, String thread, String action) {
        return cashmere.retrieveTimer(device, thread, action);
    }

    /**
     * Get the {@link Constellation} instance.
     *
     * @return the {@link Constellation} instance.
     */
    public static Constellation getConstellation() {
        if (cashmere == null) {
            throw new Error("Cashmere not initialized yet");
        }
        if (cashmere.constellation == null) {
            throw new Error("Constellation not initialized yet");
        }
        return cashmere.constellation;
    }

    /**
     * Signal to Cashmere that the application is <code>done</code>.
     *
     */
    public static void done() {
        getConstellation().done();
    }

    /*
     * Package section of Cashmere
     */

    public boolean isAsynchReads() {
        return asynchReads;
    }

    synchronized static void addTimeForKernel(String kernelName, Device device, double time) {
        cashmere.addTime(kernelName, device, time);
    }

    /*
     * Private section of Cashmere
     */

    // Private helper class representing a combination of Kernel on a Device to compare speeds of a device.
    private class KernelDevice implements Comparable<KernelDevice> {
        private String kernel;
        private Device device;

        public KernelDevice(String kernel, Device device) {
            if (kernel == null) {
                kernel = "__DEFAULT__";
            }
            this.kernel = kernel;
            this.device = device;
        }

        @Override
        public boolean equals(Object oo) {
            if (!(oo instanceof KernelDevice)) {
                return false;
            }
            KernelDevice o = (KernelDevice) oo;
            return kernel.equals(o.kernel) && device.equals(o.device);
        }

        @Override
        public int hashCode() {
            return kernel.hashCode() + device.hashCode();
        }

        @Override
        public int compareTo(KernelDevice kd) {
            double expectedTermination;
            double expectedTerminationDevice;
            synchronized (this.device) {
                expectedTermination = (this.device.getNrKernelLaunches() + this.device.getLaunched()) * kernelSpeeds.get(this);
                if (logger.isInfoEnabled()) {
                    logger.info("" + this + ": launches = " + (this.device.getNrKernelLaunches() + this.device.getLaunched())
                            + ", speed = " + kernelSpeeds.get(this));
                }
            }
            synchronized (kd.device) {
                expectedTerminationDevice = (kd.device.getNrKernelLaunches() + kd.device.getLaunched() + 1)
                        * kernelSpeeds.get(kd);
                if (logger.isInfoEnabled()) {
                    logger.info("" + kd + ": launches = " + (kd.device.getNrKernelLaunches() + kd.device.getLaunched())
                            + ", speed = " + kernelSpeeds.get(kd));
                }
            }
            if (logger.isInfoEnabled()) {
                logger.info("compareTo: " + this + ": expectedTermination = " + expectedTermination + ", " + kd
                        + ": expectedTermination = " + expectedTerminationDevice);
            }

            return expectedTermination < expectedTerminationDevice ? -1
                    : expectedTermination == expectedTerminationDevice ? 0 : 1;
        }

        @Override
        public String toString() {
            return device.toString();
        }
    }

    public static void createConstellation(ConstellationConfiguration[] e) throws ConstellationCreationException {
        if (cashmere == null) {
            throw new Error("Cashmere not initialized yet");
        }
        if (cashmere.constellation != null) {
            throw new Error("Constellation already created");
        }
        cashmere.constellation = ConstellationFactory.createConstellation(e);
    }

    /*
     * Initialization of Cashmere
     */

    // private constructors
    private Cashmere(ConstellationConfiguration[] e, Properties props, Map<String, List<String>> defines, int nrBuffers,
            int sizeBuffer) throws ConstellationCreationException {

        localBase = getLocalBase();
        asynchReads = new TypedProperties(props).getBooleanProperty("cashmere.asyncReads", false);
        if (e != null) {
            constellation = ConstellationFactory.createConstellation(e);
        }
        setPlatform(Platform.initializePlatform(props, devices, this));
        initializeBuffers(nrBuffers, sizeBuffer);
        initializeKernels(defines);
    }

    // ensuring a unique name of local executors
    private String getLocalBase() {
        String localBase;
        try {
            localBase = "LOCAL-" + IPUtils.getLocalHostAddress().getHostName() + "-";
        } catch (UnknownHostException e) {
            Random r = new Random();
            localBase = "LOCAL-" + r.nextInt() + "-";
        }
        return localBase;
    }

    private void initializeBuffers(int nrBuffers, int sizeBuffer) {
        if (nrBuffers > 0) {
            BufferCache.initializeBuffers(sizeBuffer, nrBuffers);
        }
    }

    private void initializeKernels(Map<String, List<String>> defines) {
        Map<String, String> kernelSources = getKernelSources();
        insertDefines(kernelSources, defines);
        storeKernels(kernelSources);
    }

    private Map<String, String> getKernelSources() {

        HashMap<String, String> kernelSources = new HashMap<String, String>();

        String classpath = System.getProperty("java.class.path");
        String[] classpathEntries = classpath.split(File.pathSeparator);

        try {
            // assuming that the application jar is the first
            URL jar = new URL("file:" + classpathEntries[0]);
            ZipInputStream zip = new ZipInputStream(jar.openStream());
            ZipEntry ze = null;
            while ((ze = zip.getNextEntry()) != null) {
                if (ze.getName().endsWith(getPlatform().getSuffix())) {
                    BufferedInputStream bis = new BufferedInputStream(zip);
                    byte[] bytes = new byte[(int) ze.getSize()];
                    int offset = 0;
                    int sz = bytes.length;
                    while (sz > 0) {
                        int nrRead = bis.read(bytes, offset, sz);
                        if (nrRead > 0) {
                            offset += nrRead;
                            sz -= nrRead;
                        } else {
                            throw new Error("Something wrong while reading " + "kernel " + ze.getName());
                        }
                    }
                    logger.debug("Found kernel " + ze.getName() + " with size " + bytes.length);
                    kernelSources.put(ze.getName(), new String(bytes));
                }
            }
        } catch (IOException e) {
            System.out.println(e);
        }
        return kernelSources;
    }

    private void insertDefines(Map<String, String> sources, Map<String, List<String>> defines) {
        if (defines == null) {
            defines = new HashMap<String, List<String>>();
        }
        for (String k : defines.keySet()) {
            String source = sources.get(k);
            if (source == null) {
                logger.warn("{} not a known sourcefile", k);
            } else {
                sources.put(k, insertDefines(source, defines.get(k)));
            }
        }
    }

    private String insertDefines(String source, List<String> defines) {
        String header = getHeaderSource(source);
        String body = getBodySource(source);

        StringBuilder sb = new StringBuilder();
        sb.append(header);
        for (String s : defines) {
            sb.append(s);
        }
        sb.append(body);
        return sb.toString();
    }

    private String getHeaderSource(String source) {
        StringBuilder sb = new StringBuilder();
        Scanner scanner = new Scanner(source);
        while (scanner.hasNext("//")) {
            sb.append(scanner.nextLine());
            sb.append("\n");
        }
        scanner.close();
        return sb.toString();
    }

    private String getBodySource(String source) {
        Scanner scanner = new Scanner(source);
        while (scanner.hasNext("//")) {
            scanner.nextLine();
        }

        StringBuilder sb = new StringBuilder();
        scanner.useDelimiter("\\z");
        while (scanner.hasNext()) {
            sb.append(scanner.next());
        }
        scanner.close();
        return sb.toString();
    }

    private void storeKernels(Map<String, String> sources) {
        for (String k : sources.keySet()) {
            String kernelSource = sources.get(k);
            String deviceName = getDeviceNameSource(kernelSource);
            if (devices.containsKey(deviceName)) {
                List<Device> list = devices.get(deviceName);
                if (list == null) {
                    logger.warn("{} not available on this machine", deviceName);
                } else {
                    for (Device device : list) {
                        device.addKernel(kernelSource, k);
                    }
                }
            }
        }
    }

    private String getDeviceNameSource(String source) {
        Scanner scanner = new Scanner(source);
        scanner.next(); // read away "//"
        String retval = scanner.next();
        scanner.close();
        return retval;
    }

    /*
     * Setting up libraries
     */

    private void initLibraries() {
        Collection<List<Device>> deviceCollection = devices.values();
        for (List<Device> l : deviceCollection) {
            for (Device device : l) {
                for (String name : initLibraryFuncs.keySet()) {
                    device.initializeLibrary(initLibraryFuncs.get(name));
                }
            }
        }
    }

    private void deinitLibraries() {
        Collection<List<Device>> deviceCollection = devices.values();
        for (List<Device> l : deviceCollection) {
            for (Device device : l) {
                for (String name : deInitLibraryFuncs.keySet()) {
                    device.deinitializeLibrary(deInitLibraryFuncs.get(name));
                }
            }
        }
    }

    /*
     * Retrieving kernels, library functions, and devices
     */

    private Kernel getKernel(Constellation executor, String name, Device device) throws CashmereNotAvailable {
        if (device == null) {
            device = pickDevice(name);
        }
        device.setBusy();
        Kernel kernel = new Kernel(name, (executor != null) ? executor.identifier().toString() : Thread.currentThread().getName(),
                device);
        return kernel;
    }

    // private static synchronized LibFunc getLibFunc(Constellation executor, String name, Device device)
    // 	throws CashmereNotAvailable, LibFuncNotAvailable {

    // }

    private LibFunc getLibraryFunc(Constellation executor, String name, Device device)
            throws CashmereNotAvailable, LibFuncNotAvailable {

        if (device == null) {
            device = pickFastestDevice();
        }
        device.setBusy();
        if (!initLibraryFuncs.containsKey(name)) {
            throw new LibFuncNotAvailable(name);
        }
        LibFunc libFunc = new LibFunc(name,
                executor != null ? executor.identifier().toString() : Thread.currentThread().getName(), device);
        return libFunc;
    }

    private List<Device> getDevicesForKernel(String name) {
        ArrayList<Device> al = new ArrayList<Device>();
        Collection<List<Device>> deviceCollection = devices.values();
        for (List<Device> l : deviceCollection) {
            for (Device device : l) {
                if (device.registeredKernel(name)) {
                    al.add(device);
                }
            }
        }
        return al;
    }

    private synchronized Device pickDevice(String name) throws CashmereNotAvailable {
        Collection<List<Device>> deviceCollection = devices.values();
        ArrayList<Device> al = new ArrayList<Device>();
        ArrayList<KernelDevice> kd = new ArrayList<KernelDevice>();
        boolean measuredSpeeds = true;

        for (List<Device> list : deviceCollection) {
            for (Device device : list) {
                logger.debug("Found device: " + device.getName());
                if (device.registeredKernel(name)) {
                    KernelDevice d = new KernelDevice(name, device);
                    if (kernelSpeeds.get(d) == null) {
                        measuredSpeeds = false;
                    } else {
                        kd.add(d);
                    }
                    al.add(device);
                }
            }
        }

        if (al.size() > 0) {
            if (measuredSpeeds) {
                Collections.sort(kd);
                return kd.get(0).device;
            }

            Collections.sort(al);
            // the first is the best.
            return al.get(0);
        }

        String kernelMessage = name == null ? "the kernel" : "kernel " + name;
        throw new CashmereNotAvailable("no devices found where " + kernelMessage + " is registered");
    }

    private Device pickFastestDevice() throws CashmereNotAvailable {
        ArrayList<Device> listDevices = new ArrayList<Device>();

        Collection<List<Device>> deviceCollection = devices.values();
        for (List<Device> l : deviceCollection) {
            listDevices.addAll(l);
        }

        if (listDevices.size() > 0) {
            Collections.sort(listDevices);
            return listDevices.get(0);
        }

        throw new CashmereNotAvailable("no devices found where for library");
    }

    // add time for a kernel/device combinationy
    private void addTime(String kernelName, Device device, double time) {
        KernelDevice d = new KernelDevice(kernelName, device);
        Double v = kernelSpeeds.get(d);
        if (v == null || v < time) {
            kernelSpeeds.put(d, time);
        }
    }

    /*
     * Timers
     */
    private Timer retrieveTimer(String standardDevice, String standardThread, String standardAction) {
        String s = standardDevice + "_" + standardThread + "_" + standardAction;
        Timer timer = timers.get(s);
        if (timer == null) {
            timer = createTimer(standardDevice, standardThread, standardAction);
            timers.put(s, timer);
        }
        return timer;
    }

    private Timer createTimer(String standardDevice, String standardThread, String standardAction) {
        return getConstellation().getTimer(standardDevice, standardThread, standardAction);
    }

    private Timer createTimer() {
        return getConstellation().getTimer();
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    /*
     * Currently not used, keeping it for Shane
     */

    /*
     * When setting up the executors, we create a number of local and global
     * executors. The executor for local is in stealpool 1 and steals from
     * stealpool 0, which means that it searches for activities local 0. The
     * local executors are in stealpool local 0, steal from global 0 and will
     * look for activities with the global 0 context. Then there is one last
     * executor that looks for activities with label global 1. It will not steal
     * from any pool and belongs to global pool 0.
     *
     * This means that in a Cashmere application, there should be one activity
     * that is started with context global 1. This activity should submit new
     * activities global 0. These activities should then create activities with
     * local 0.
     */

    // private void conclude() {
    //     // Event.logger.debug("in conclude");
    //     if (!manyCoreUnits.isEmpty()) {
    //         // Timer writeTimer = createTimer();
    //         // Timer executeTimer = createTimer();
    //         // Timer readTimer = createTimer();
    //         //
    //         for (ManyCoreUnit manyCoreUnit : manyCoreUnits) {
    //             for (Launch launch : manyCoreUnit.launches) {
    //                 // conclude(launch.getThread() + " write", "writeBuffer",
    //                 // manyCoreUnit.getDevice(), launch.writeBufferEvents,
    //                 // writeTimer);
    //                 // conclude(launch.getThread(), "execute",
    //                 // manyCoreUnit.getDevice(), launch.executeEvents,
    //                 // executeTimer);
    //                 // conclude(launch.getThread() + " read", "readBuffer",
    //                 // manyCoreUnit.getDevice(), launch.readBufferEvents,
    //                 // readTimer);
    //                 launch.clean();
    //             }
    //         }

    //         Collection<Device> deviceCollection = devices.values();
    //         for (Device device : deviceCollection) {
    //             for (String thread : device.readBufferEventsMap.keySet()) {
    //                 ArrayList<cl_event> readBufferEvents = device.readBufferEventsMap
    //                         .get(thread);
    //                 if (readBufferEvents != null
    //                         && readBufferEvents.size() != 0) {

    //                     // conclude(thread + " read", "readBuffer", device,
    //                     // readBufferEvents,
    //                     // readTimer);
    //                 }
    //             }
    //         }
    //     }

    //     // try {
    //     // Thread.currentThread().sleep(10000);
    //     // }
    //     // catch (InterruptedException e) {
    //     // }
    //     // for (cl_event event : Event.nonReleasedEvents) {
    //     // Event.logger.debug("left over: " + event);
    //     // }
    // }

    // public static void d(String s) {
    //     System.out.printf("%s: %s\n", Thread.currentThread(), s);
    // }

    // public static void d(String s, Object... args) {
    //     System.out.printf("%s: %s", Thread.currentThread(),
    //             String.format(s, args));
    // }

    // public void clean() {
    //     if (!manyCoreUnits.isEmpty()) {
    //         for (ManyCoreUnit manyCoreUnit : manyCoreUnits) {
    //             for (Launch launch : manyCoreUnit.launches) {
    //                 launch.clean();
    //             }
    //         }
    //     }

    //     Collection<Device> deviceCollection = devices.values();
    //     for (Device device : deviceCollection) {
    //         device.showExecuteEvents();
    //     }

    // }

    // public void release() {
    // 	Event.logger.debug("in releasey");
    // 	if (!manyCoreUnits.isEmpty()) {
    // 	    for (ManyCoreUnit manyCoreUnit : manyCoreUnits) {
    // 		for (Launch launch : manyCoreUnit.launches) {
    // 		    Event.logger.debug("cleaning launch");
    // 		    launch.clean();
    // 		}
    // 	    }
    // 	}
    // }

    // synchronized static void releaseEvent(cl_event event) {
    // 	long queued = getValue(event, CL_PROFILING_COMMAND_QUEUED);
    // 	long submit = getValue(event, CL_PROFILING_COMMAND_SUBMIT);
    // 	long start = getValue(event, CL_PROFILING_COMMAND_START);
    // 	long end = getValue(event, CL_PROFILING_COMMAND_END);
    // 	if (end == 0 || end < start) {
    // 	    // Sometimes seems to happen ...
    // 	    if (logger.isDebugEnabled()) {
    // 		logger.debug("start = " + start + ", end = " + end
    // 			+ ", event = " + event);
    // 	    }
    // 	    end = start + 1;
    // 	}
    // 	if (start > 0) {
    // 	    queuedEvents.put(event, queued);
    // 	    submitEvents.put(event, submit);
    // 	    startEvents.put(event, start);
    // 	    endEvents.put(event, end);
    // 	} else {
    // 	    // Sometimes happens on read events that result from synchronous
    // 	    // reads.
    // 	    // System.err.println("Release unstarted event?");
    // 	    // (new Throwable()).printStackTrace(System.err);
    // 	    return;
    // 	}
    // 	// if (event == null) { // never; debug.
    // 	clReleaseEvent(event);
    // 	logger.debug("releasing event {}", event);
    // 	// }
    // }

    // private void conclude(String thread, String action, Device device,
    // 	    ArrayList<cl_event> events, Timer timer) {
    // 	if (events == null)
    // 	    throw new Error("shit");

    // 	long offsetHostDevice = device.getOffsetHostDevice();
    // 	for (cl_event event : events) {
    // 	    long queued = 0;
    // 	    if (queuedEvents.get(event) == null) {
    // 		releaseEvent(event);
    // 	    } else {
    // 		queued = queuedEvents.get(event);
    // 	    }
    // 	    if (event == null)
    // 		System.out.println("event == null");
    // 	    if (submitEvents == null)
    // 		System.out.println("submitEvents == null");
    // 	    long submit = submitEvents.get(event);
    // 	    long start = startEvents.get(event);
    // 	    long end = endEvents.get(event);
    // 	    // System.out.println("start = " + start + ", end = " + end +
    // 	    // ", offsetHostDevice = " + offsetHostDevice);
    // 	    timer.add(device.getNickName(), thread, action,
    // 		    queued + offsetHostDevice, submit + offsetHostDevice,
    // 		    start + offsetHostDevice, end + offsetHostDevice);
    // 	}
    // }

    // private static int getSpeed() {
    //     int speed = 0;
    //     Collection<Device> deviceCollection = devices.values();
    //     for (Device device : deviceCollection) {
    //         int deviceSpeed = device.getSpeed();
    //         if (deviceSpeed > speed) {
    //             speed = deviceSpeed;
    //         }
    //     }
    //     return speed;
    // }

}
