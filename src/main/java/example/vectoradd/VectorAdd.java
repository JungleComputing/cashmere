package example.vectoradd;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.Cashmere;
import ibis.constellation.ConstellationConfiguration;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.StealStrategy;
import ibis.constellation.StealPool;
import ibis.constellation.Timer;
import ibis.constellation.util.SingleEventCollector;

import example.util.Util;

class VectorAdd {

    static Logger logger = LoggerFactory.getLogger("VectorAdd");

    public static void writeFile(float[] array) {
        try {
            PrintStream out = new PrintStream("vectoradd.out");
            for (int i = 0; i < array.length; i++) {
                out.println(array[i]);
            }
            out.close();
        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
        }
    }

    public static ConstellationConfiguration[] getExecutors(Properties props) {

        String localProp = props.getProperty("cashmere.nLocalExecutors");
        String globalProp = props.getProperty("cashmere.nGlobalExecutors");

        int localExecutors = 4;
        int globalExecutors = 2;
        if (localProp != null) {
            localExecutors = Integer.parseInt(localProp);
        }
        if (globalProp != null) {
            globalExecutors = Integer.parseInt(globalProp);
        }

        ConstellationConfiguration[] e = new ConstellationConfiguration[localExecutors
                + globalExecutors + 1];

        for (int k = 0; k < localExecutors; k++) {
            e[k] = new ConstellationConfiguration(
                    Util.localContext(0), Util.localPool(1),
                    Util.localPool(0), StealStrategy.SMALLEST,
                    StealStrategy.SMALLEST, StealStrategy.SMALLEST);
        }
        for (int k = 0; k < globalExecutors; k++) {
            e[k + localExecutors] = new ConstellationConfiguration(
                    Util.globalContext(0),
                    Util.localPool(0),
                    Util.globalPool(0),
                    StealStrategy.SMALLEST, StealStrategy.SMALLEST,
                    StealStrategy.SMALLEST);
        }
        // These executors are used for Event collectors
        e[localExecutors + globalExecutors] = new ConstellationConfiguration(
                            Util.globalContext(1),
                            Util.globalPool(0), StealPool.NONE,
                            StealStrategy.SMALLEST, StealStrategy.SMALLEST,
                            StealStrategy.SMALLEST);
        return e;
    }

    public static void main(String[] args) throws Exception {
        // the number of tasks to spread over the nodes
        int nGlobalActivities = 1;

        // the number of tasks per node for the many-core devices
        int nLocalActivities = 1;

        // whether to use many-core devices
        boolean mc = true;

        // input size
        int n = 64 * 1024 * 1024;

        // determine the number of tasks based on the size of the pool of nodes
        String nt = System.getProperty("ibis.pool.size");
        if (nt != null) {
            nGlobalActivities = Integer.parseInt(nt);
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-nLocalActivities")) {
                i++;
                nLocalActivities = Integer.parseInt(args[i]);
            } else if (args[i].equals("-nGlobalActivities")) {
                i++;
                nGlobalActivities = Integer.parseInt(args[i]);
            } else if (args[i].equals("-n")) {
                i++;
                n = Integer.parseInt(args[i]);
            } else if (args[i].equals("-mc")) {
                mc = true;
            } else if (args[i].equals("-cpu")) {
                mc = false;
            } else {
                throw new Error("Usage: java VectorAdd [ -cpu | -mc ]"
                        + "[ -nLocalActivities <num> ] "
                        + "[ -nGlobalActivities <num> ]");
            }
        }

        // set up the input data
        float[] a = new float[n];
        for (int i = 0; i < n; i++)
            a[i] = i;

        // b contains all zeros
        float[] b = new float[n];

        // Initialize cashmere with default executors for constellation.

        // This means that there is an executor that executes activities with
        // contexts 'global activity context 1', does not steal jobs, and
        // executors that steal from stealpool 'global 0' can steal its
        // activities.

        // In addition there are several executors (set by the property
        // 'cashmere.nGlobalExecutors') that execute jobs with label 'global
        // activity context 0', steal from stealpool 'global 0', and executors
        // that steal from stealpool 'local 0' can steal their activities.

        // Finally, there are several executors (set by the property
        // 'cashmere.nLocalExecutors') that execute jobs with label 'local 0',
        // steal from stealpool 'local 0', and executors that steal from
        // stealpool 'local 1' can steal their activities.
        Cashmere.initialize(getExecutors(System.getProperties()));
        // retrieve constellation from cashmere.
        Constellation constellation = Cashmere.getConstellation();

        constellation.activate();

        if (constellation.isMaster()) {
            System.out.println("VectorAdd, running on " + (mc ? "MC" : "CPU")
                    + ", n = " + n);
            System.out.println("I am the master!");

            // set up the various activities, staring with the main activity:

            // The overall timer measures the execution of the whole
            // computation.
            Timer timer = Cashmere.getOverallTimer();

            // Start the timer, remember the unique event number to stop the
            // timer later.
            int eventNo = timer.start();

            // The SingleEventCollector is an activity that waits for a single
            // event to come in will finish then. We associate context 'global
            // activity context 1' with it.
            SingleEventCollector sec = new SingleEventCollector(
                    Util.globalContext(1));

            // submit the single event collector
            ActivityIdentifier aid = Cashmere.submit(sec);
            // submit the vectorAddActivity. Set the parent as well.
            Cashmere.submit(new VectorAddActivity(aid, nGlobalActivities,
                    nLocalActivities, n, mc, a, b));

            logger.debug("main(), just submitted, about to waitForEvent() "
                    + "for any event with target " + aid);
            VectorAddResult result = (VectorAddResult) sec.waitForEvent()
                    .getData();
            logger.debug(
                    "main(), done with waitForEvent() on identifier " + aid);
            // stop the timer
            timer.stop(eventNo);
            double time = timer.totalTimeVal() / 1000000.0;
            System.out.println("VectorAdd time: " + time + " seconds");

            // writeFile(result.c);
        }
        logger.debug("calling cashmere.done()");
        Cashmere.done();
        logger.debug("called cashmere.done()");
    }
}
