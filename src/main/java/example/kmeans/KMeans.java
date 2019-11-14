package example.kmeans;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.Kernel;
import ibis.cashmere.constellation.KernelLaunch;
import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.CashmereNotAvailable;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.ConstellationConfiguration;
import ibis.constellation.Event;
import ibis.constellation.NoSuitableExecutorException;
import ibis.constellation.StealPool;
import ibis.constellation.StealStrategy;
import ibis.constellation.Timer;
import ibis.constellation.util.MultiEventCollector;
import ibis.constellation.util.SingleEventCollector;

import example.util.Util;

public class KMeans extends Activity {

    public static Logger logger = LoggerFactory.getLogger("KMeans");

    private static final long serialVersionUID = 1L;

    public static final float THRESHOLD = 0.1f;
    public static final int MAX_ITERATIONS = 1000;

    private static int nFeatures = 4; // Default

    private static Points points;

    private int jobNo;

    private KMeansResult result;

    private boolean gpu;

    private float[] centers;

    private int numTasks;

    private int gpuJobsPerTask;

    private ActivityIdentifier parent;

    private int numResults;

    // Read initial "centers" file.
    private static final float[] readCenters(String dir) throws Exception {
        File d = new File(dir, "centers");
        DataInputStream is = new DataInputStream(
                new BufferedInputStream(new FileInputStream(d)));
        nFeatures = is.readInt();
        int len = is.readInt();
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            result[i] = is.readFloat();
        }
        is.close();
        return result;
    }

    // Read "points" directory.
    private static final float[][] readPoints(String dir, int numTasks)
        throws Exception {
        File d = new File(dir, "points");
        DataInputStream is = null;
        int len = 0;
        float[] result = null;
        try {
            is = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(d)));
            int n = is.readInt();
            if (n != nFeatures) {
                throw new Error("Wrong number of features");
            }
            len = is.readInt();
            result = new float[len];
            for (int i = 0; i < len; i++) {
                result[i] = is.readFloat();
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }

        int nPoints = len / nFeatures;
        int pointsPerTask = nPoints / numTasks;
        int p1 = nPoints - numTasks * pointsPerTask;

        float[][] pts = new float[numTasks][];
        int pIndex = 0;
        for (int i = 0; i < p1; i++) {
            pts[i] = new float[(pointsPerTask + 1) * nFeatures];
            for (int j = 0; j <= pointsPerTask; j++) {
                for (int k = 0; k < nFeatures; k++) {
                    pts[i][k * pointsPerTask + j] = result[pIndex * nFeatures
                        + k];
                }
                pIndex++;
            }
        }
        for (int i = p1; i < numTasks; i++) {
            pts[i] = new float[pointsPerTask * nFeatures];
            for (int j = 0; j < pointsPerTask; j++) {
                for (int k = 0; k < nFeatures; k++) {
                    pts[i][k * pointsPerTask + j] = result[pIndex * nFeatures
                        + k];
                }
                pIndex++;
            }
        }
        return pts;
    }

    static ConstellationConfiguration[] getConstellationConfigurations(
            Properties props) {

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
            e[k] = new ConstellationConfiguration(Util.localContext(0),
                    Util.localPool(1), Util.localPool(0), StealStrategy.SMALLEST,
                    StealStrategy.SMALLEST, StealStrategy.SMALLEST);
        }
        for (int k = 0; k < globalExecutors; k++) {
            e[k + localExecutors] = new ConstellationConfiguration(
                    Util.globalContext(0), Util.localPool(0), Util.globalPool(0),
                    StealStrategy.SMALLEST, StealStrategy.SMALLEST,
                    StealStrategy.SMALLEST);
        }
        e[localExecutors + globalExecutors] = new ConstellationConfiguration(
                Util.globalContext(1), Util.globalPool(0), StealPool.NONE,
                StealStrategy.SMALLEST, StealStrategy.SMALLEST,
                StealStrategy.SMALLEST);
        return e;
    }

    protected KMeans(ActivityIdentifier parent, float[] centers, int numTasks,
            int gpuJobsPerTask, int jobNo, boolean gpu) {
        super(numTasks > 1 ? Util.globalContext(1)
                : numTasks == 1 ? Util.globalContext(0)
                : Util.localContext(0),
                numTasks > 0);
        logger.info("Creating KMeans object, numTasks = " + numTasks);
        this.parent = parent;
        this.centers = centers;
        this.numTasks = numTasks;
        this.gpuJobsPerTask = gpuJobsPerTask;
        this.jobNo = jobNo;
        this.gpu = gpu;
    }

    @Override
    public int initialize(Constellation cons) {

        if (numTasks == 0) {
            logger.info("Executing job " + jobNo);
            result = kMeansCluster(cons, centers, jobNo, gpu);
            logger.info("Done with job " + jobNo);
            return FINISH;
        } else if (numTasks > 1) {
            result = new KMeansResult(new float[centers.length],
                    new int[centers.length / nFeatures]);
            for (int i = 0; i < numTasks; i++) {
                logger.info("Submit task " + i);
                try {
                    cons.submit(new KMeans(identifier(), centers, 1,
                                gpuJobsPerTask, jobNo, gpu));
                } catch (NoSuitableExecutorException e) {
                    logger.error("Could not submit", e);
                    return FINISH;
                }
                jobNo += gpuJobsPerTask;
            }
            return SUSPEND;
        } else /* if (numTasks == 1) */ {
            result = new KMeansResult(new float[centers.length],
                    new int[centers.length / nFeatures]);
            MultiEventCollector c = new MultiEventCollector(
                    Util.localContext(0), gpuJobsPerTask);
            try {
                ActivityIdentifier aid = cons.submit(c);
                for (int i = 0; i < gpuJobsPerTask; i++) {
                    logger.info("Submit gpu task " + (jobNo + i));
                    cons.submit(new KMeans(aid, centers, 0, gpuJobsPerTask,
                                jobNo + i, gpu));
                }
            } catch (NoSuitableExecutorException e) {
                logger.error("Could not submit", e);
                return FINISH;
            }
            Event[] events = c.waitForEvents();
            Timer timer = Cashmere.getTimer("java",
                    cons.identifier().toString(), "CPU collect results");
            int event = timer.start();
            logger.info("Collecting results for task " + jobNo);
            for (Event e : events) {
                result.add((KMeansResult) e.getData(), nFeatures);
            }
            timer.stop(event);
            logger.info("Done with task " + jobNo);
            return FINISH;
        }
    }

    @Override
    public void cleanup(Constellation cons) {
        if (parent != null) {
            cons.send(new Event(identifier(), parent, result));
        }
    }

    @Override
    public int process(Constellation cons, Event arg0) {
        numResults++;
        result.add((KMeansResult) arg0.getData(), nFeatures);
        if (numTasks > 1 && numResults == numTasks) {
            return FINISH;
        } else {
            return SUSPEND;
        }
    }

    private int[] kMeansClusterCPU(Constellation cons, int jobNo,
            float[] centers, float[] pts, int nFeatures, int jobSize) {
        Timer timer = Cashmere.getTimer("java", cons.identifier().toString(),
                "CPU compute distances");
        int event = timer.start();
        int nCenters = centers.length / nFeatures;
        int[] pointsCluster = new int[jobSize];

        if (logger.isDebugEnabled()) {
            logger.debug("Executing job " + jobNo + " of size " + jobSize);
        }

        for (int pIndex = 0; pIndex < jobSize; pIndex++) {
            float minimum = Float.MAX_VALUE;
            int cluster = -1;
            for (int i = 0; i < nCenters; i++) {
                float distanceSq = eucledianDistanceSq(pts, pIndex, centers, i,
                        nFeatures, jobSize);
                if (distanceSq < minimum) {
                    minimum = distanceSq;
                    cluster = i;
                }
            }
            pointsCluster[pIndex] = cluster;
        }
        timer.stop(event);
        return pointsCluster;
    }

    private int[] kMeansClusterGPU(Constellation cons, int jobNo,
            float[] centers, float[] pts, int nFeatures, int jobSize) {
        int[] pointsCluster = new int[jobSize];
        try {
            Kernel kernel = Cashmere.getKernel("kmeans");
            KernelLaunch kernelLaunch = kernel.createLaunch();

            if (logger.isDebugEnabled()) {
                logger.debug("Executing job " + jobNo + " of size " + jobSize);
            }

            MCL.launchKmeans_kernelKernel(kernelLaunch, jobSize,
                    centers.length / nFeatures, nFeatures, pts, centers,
                    pointsCluster);

            // System.out.println("Executed job " + jobNo);

            return pointsCluster;
        } catch (CashmereNotAvailable e) {
            logger.warn("fallback to CPU", e);
            return kMeansClusterCPU(cons, jobNo, centers, pts, nFeatures,
                    jobSize);
        } catch (RuntimeException e) {
            e.printStackTrace(System.out);
            throw e;
        } catch (Error e) {
            e.printStackTrace(System.out);
            throw e;
        }
    }

    public KMeansResult kMeansCluster(Constellation cons, float[] centers,
            int jobNo, boolean gpu) {

        int nFeatures = points.nFeatures();
        float[] newCenters = new float[centers.length];
        int nCenters = centers.length / nFeatures;
        int[] counts = new int[nCenters];
        KMeansResult result = new KMeansResult(newCenters, counts);
        float[] pts = points.getPoints(jobNo);
        int jobSize = pts.length / nFeatures;

        int[] pointsCluster = gpu
            ? kMeansClusterGPU(cons, jobNo, centers, pts, nFeatures,
                    jobSize)
            : kMeansClusterCPU(cons, jobNo, centers, pts, nFeatures,
                    jobSize);

        Timer timer = Cashmere.getTimer("java", cons.identifier().toString(),
                "CPU compute centers");
        int event = timer.start();

        for (int pIndex = 0; pIndex < jobSize; pIndex++) {
            int cluster = pointsCluster[pIndex];
            for (int i = 0; i < nFeatures; i++) {
                newCenters[cluster * nFeatures + i] += pts[i * jobSize
                    + pIndex];
            }
            counts[cluster]++;
        }

        timer.stop(event);

        return result;
    }

    /***** SUPPORT METHODS *****/
    private static final float eucledianDistanceSq(float[] pts, int i1,
            float[] set2, int i2, int nFeatures, int jobSize) {
        float sum = 0;
        for (int i = 0; i < nFeatures; i++) {
            float diff = set2[i2 * nFeatures + i] - pts[i * jobSize + i1];
            sum += (diff * diff);
        }
        return sum;
    }

    private static Map<String, List<String>> createDefines(int nFeatures) {
        Map<String, List<String>> mapDefines = new HashMap<String, List<String>>();

        List<String> defines = new ArrayList<String>();
        defines.add(String.format("#define MCL_nfeatures %d\n", nFeatures));
        mapDefines.put("kmeans.cu", defines);
        mapDefines.put("kmeans_cc_2_0.cl", defines);
        mapDefines.put("kmeans_xeon_phi.cl", defines);
        mapDefines.put("kmeans_hd7970.cl", defines);
        return mapDefines;
    }

    public static void main(String[] args) throws Exception {

        int numTasks = 1;
        int gpuJobsPerTask = 1;
        int maxIter = MAX_ITERATIONS;
        boolean runOnGpu = true;
        String dataDir = null;
        int nPoints = 1024 * 1024 * 128;
        int nCenters = 1024;

        String nt = System.getProperty("ibis.pool.size");
        if (nt != null) {
            numTasks = Integer.parseInt(nt);
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-iters")) {
                i++;
                maxIter = Integer.parseInt(args[i]);
            } else if (args[i].equals("-gpuJobsPerTask")) {
                i++;
                gpuJobsPerTask = Integer.parseInt(args[i]);
            } else if (args[i].equals("-nTasks")) {
                i++;
                numTasks = Integer.parseInt(args[i]);
            } else if (args[i].equals("-nCenters")) {
                i++;
                nCenters = Integer.parseInt(args[i]);
            } else if (args[i].equals("-nPoints")) {
                i++;
                nPoints = Integer.parseInt(args[i]);
            } else if (args[i].equals("-nFeatures")) {
                i++;
                nFeatures = Integer.parseInt(args[i]);
            } else if (args[i].equals("-gpu")) {
                runOnGpu = true;
            } else if (args[i].equals("-cpu")) {
                runOnGpu = false;
            } else {
                if (dataDir == null) {
                    dataDir = args[i];
                } else {
                    throw new Error(
                            "Usage: java KMeans [ -cpu | -gpu ] [ -iters <num> ] [ -nTasks <num> ] <dataDir>");
                }
            }
        }

        points = new Points();

        float[] centers;

        if (dataDir == null) {
            centers = generateRandom(nFeatures * nCenters, 307L);
            points.generatePoints(nPoints, nFeatures,
                    numTasks * gpuJobsPerTask);
        } else {
            centers = readCenters(dataDir);
            nCenters = centers.length / nFeatures;

            points.initializePoints(
                    readPoints(dataDir, numTasks * gpuJobsPerTask), nFeatures);

            nPoints = points.nPoints();
        }

        /*
         * System.out.println("Original centers:"); for (int i = 0; i <
         * nCenters; i++) { System.out.print("("); for (int j = 0; j <
         * nFeatures; j++) { System.out.print(centers[i*nFeatures+j]); if (j <
         * nFeatures - 1) { System.out.print(", "); } } System.out.println(")");
         * }
         */

        Properties p = System.getProperties();
        Cashmere.initialize(getConstellationConfigurations(p), p, createDefines(nFeatures));
        Constellation constellation = Cashmere.getConstellation();

        constellation.activate();

        if (constellation.isMaster()) {
            System.out
                .println("KMeans, running on " + (runOnGpu ? "GPU" : "CPU")
                        + ", number of tasks = " + numTasks);
            System.out.println("Number of features: " + nFeatures
                    + ", number of centers: " + centers.length / nFeatures
                    + ", number of points: " + nPoints);

            int iteration = 0;
            float max;

            System.out.println("I am the master!");

            Timer timer = Cashmere.getOverallTimer();

            int eventNo = timer.start();
            do {
                SingleEventCollector a = new SingleEventCollector(
                        Util.globalContext(0));
                ActivityIdentifier aid = Cashmere.submit(a);
                Cashmere.submit(new KMeans(aid, centers, numTasks,
                            gpuJobsPerTask, 0, runOnGpu));
                KMeansResult results = (KMeansResult) a.waitForEvent()
                    .getData();
                iteration++;
                results.avg(nFeatures);

                max = 0.0f;

                // Compute new centers
                for (int i = 0; i < nCenters; i++) {
                    if (results.counts[i] > 0) {
                        float sum = 0;
                        for (int j = 0; j < nFeatures; j++) {
                            float diff = centers[i * nFeatures + j]
                                - results.centers[i * nFeatures + j];
                            sum += (diff * diff);
                        }
                        float tmp = (float) Math.sqrt(sum);
                        if (tmp > max)
                            max = tmp;
                        for (int j = 0; j < nFeatures; j++) {
                            centers[i * nFeatures
                                + j] = results.centers[i * nFeatures + j];
                        }
                    }
                }

                System.out.println(
                        "Iteration " + iteration + ", max diff = " + max);
            } while (max >= THRESHOLD && iteration < maxIter);

            timer.stop(eventNo);

            double time = timer.totalTimeVal() / 1000000.0;

            System.out.println("KMeans time: " + time + " seconds");
            System.out
                .printf("Kmeans performance: %f GFLOPS\n",
                        (iteration * 3.0 * nFeatures
                         * (centers.length / nFeatures) * nPoints)
                        / 1.0e9 / time);

            // Print out centroid results.
            /*
             * System.out.println("Centers:"); for (int i = 0; i < nCenters;
             * i++) { System.out.print("("); for (int j = 0; j < nFeatures; j++)
             * { System.out.print(centers[i*nFeatures+j]); if (j < nFeatures -
             * 1) { System.out.print(", "); } } System.out.println(")"); }
             */
        }

        Cashmere.done();
    }

    public static float[] generateRandom(int sz, long seed) {
        float[] a = new float[sz];
        Random r = new Random(seed);
        for (int i = 0; i < a.length; i++) {
            a[i] = r.nextFloat() * 100.0f;
        }
        return a;
    }

}
