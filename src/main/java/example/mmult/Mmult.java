package example.mmult;

// Class Mmult
//
// Matrix multiply functionality
// This is the ony really interesting part
// The rest is dead weight

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.Cashmere;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.ConstellationConfiguration;
import ibis.constellation.Context;
import ibis.constellation.AbstractContext;
import ibis.constellation.Event;
import ibis.constellation.NoSuitableExecutorException;
import ibis.constellation.StealPool;
import ibis.constellation.StealStrategy;
import ibis.constellation.Timer;
import ibis.constellation.util.ByteBuffers;
import ibis.constellation.util.SingleEventCollector;

import example.util.Util;

final class Mmult extends Activity implements ByteBuffers {

    public static Matrix a;
    public static Matrix b;

    public static Logger logger = LoggerFactory.getLogger("Mmult");

    private ActivityIdentifier parent;
    private int task;
    private int rec;
    private boolean gpu;
    private byte[] aPosition;
    private byte[] bPosition;
    private Result c;
    private int numResults;
    private AbstractContext childContext;

    private static class Result implements java.io.Serializable, ByteBuffers {
        Matrix m;
        int subjob;

        public Result(Matrix m, int subjob) {
            this.m = m;
            this.subjob = subjob;
        }

        @Override
        public void popByteBuffers(List<ByteBuffer> l) {
            if (m != null) {
                m.setByteBuffers(l);
            }
        }

        @Override
        public void pushByteBuffers(List<ByteBuffer> l) {
            if (m != null) {
                m.getByteBuffers(l);
            }
        }
    }

    protected Mmult(ActivityIdentifier parent, AbstractContext context, int task,
            int rec, boolean gpu, byte[] aPos, byte[] bPos, Result c) {
        super(context, task > 0 || rec > 0);
        if (logger.isDebugEnabled()) {
            logger.debug("Creating job: apos = " + Arrays.toString(aPos)
                    + ", bpos = " + Arrays.toString(bPos));
        }
        this.parent = parent;
        this.gpu = gpu;
        this.task = task;
        this.rec = rec;
        this.aPosition = aPos;
        this.bPosition = bPos;
        this.c = c;
    }

    byte[] newPos(byte[] srcPos, int direction) {
        byte[] result;
        if (srcPos == null) {
            result = new byte[1];
        } else {
            result = new byte[srcPos.length + 1];
            System.arraycopy(srcPos, 0, result, 0, srcPos.length);
        }

        result[result.length - 1] = (byte) direction;

        return result;
    }

    // real functionality: tasked-mat-mul
    @Override
    public int initialize(Constellation cons) {
        childContext = task <= 0 && rec == 1
                ? Util.localContext(0)
                : new Context(Util.globalName(0),
                        task <= 0 ? rec : task * 10);
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Initializing job: apos = " + Arrays.toString(aPosition)
                            + ", bpos = " + Arrays.toString(bPosition));
        }
        if (task == 0) {
            if (rec == 0) {
                // switch to serial recursive part
                // pass instance variables
                // System.out.println("C = " + c);
                // System.out.println("A = " + a);
                // System.out.println("a.m = " + a.m);
                // System.out.println("B = " + b);
                // System.out.println("b.m = " + b.m);

                // added this to support input 0 0 2048
                // CTimer timer = cashmere.getTimer("java",
                // executor.identifier()
                // .toString(), "multiply");
                // int evt = timer.start();
                Matrix am = aPosition == null ? a : a.getSubMatrix(aPosition);
                Matrix bm = bPosition == null ? b : b.getSubMatrix(bPosition);
                c.m.matMul(am, bm, gpu);
                // timer.stop(evt);
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "Done job: apos = " + Arrays.toString(aPosition)
                                    + ", bpos = " + Arrays.toString(bPosition));
                }
                return FINISH;
            } else {
                c.m.allocateSubs();
                rec--;
            }
        } else if (task > 0) {
            c.m.allocateSubs();
            task--;
        }

        ActivityIdentifier id = identifier();
        FlexibleEventCollector evtColl = null;
        try {
            if (childContext.equals(Util.localContext(0))) {
                // The executor that spawns all local jobs, but itself still
                // steals
                // global jobs,
                // should block, and not steal new jobs while it is processing
                // the
                // current one.
                // So, it explicitly waits for events, but does not suspend, so
                // that
                // is does not
                // steal.
                evtColl = new FlexibleEventCollector(
                        Util.localContext(1));
                id = cons.submit(evtColl);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Job: apos = " + Arrays.toString(aPosition)
                        + ", bpos = " + Arrays.toString(bPosition)
                        + ": generating subjobs");
            }

            /* f_00 = */cons.submit(new Mmult(id, childContext, task, rec, gpu,
                    newPos(aPosition, 00), newPos(bPosition, 00),
                    new Result(c.m._00, 0)));
            /* f_01 = */cons.submit(new Mmult(id, childContext, task, rec, gpu,
                    newPos(aPosition, 00), newPos(bPosition, 01),
                    new Result(c.m._01, 1)));
            /* f_10 = */cons.submit(new Mmult(id, childContext, task, rec, gpu,
                    newPos(aPosition, 10), newPos(bPosition, 00),
                    new Result(c.m._10, 2)));
            /* f_11 = */cons.submit(new Mmult(id, childContext, task, rec, gpu,
                    newPos(aPosition, 10), newPos(bPosition, 01),
                    new Result(c.m._11, 3)));
        } catch (NoSuitableExecutorException e) {
            logger.error("Could not submit", e);
            return FINISH;
        }
        if (evtColl != null) {
            while (numResults < 8) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Job: apos = " + Arrays.toString(aPosition)
                            + ", bpos = " + Arrays.toString(bPosition)
                            + ", waiting for results");
                }
                Event[] evnts = evtColl.waitForEvents();
                if (logger.isDebugEnabled()) {
                    logger.debug("Got " + evnts.length
                            + " results; numResults was " + numResults);
                }
                for (Event e : evnts) {
                    try {
                        processResult(cons, (Result) e.getData(), id);
                    } catch (NoSuitableExecutorException ex) {
                        logger.error("Could not submit", ex);
                        return FINISH;
                    }
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("numResults = " + numResults);
                }
            }
            return FINISH;
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Job: apos = " + Arrays.toString(aPosition)
                        + ", bpos = " + Arrays.toString(bPosition)
                        + ", suspending");
            }
            return SUSPEND;
        }
    }

    private synchronized void processResult(Constellation cons, Result r,
            ActivityIdentifier id) throws NoSuitableExecutorException {
        numResults++;
        Matrix tmp = r.m;
        switch (r.subjob) {
        case 0:
            c.m._00 = null;
            cons.submit(new Mmult(id, childContext, task, rec, gpu,
                    newPos(aPosition, 01), newPos(bPosition, 10),
                    new Result(tmp, 4)));
            break;
        case 1:
            c.m._01 = null;
            cons.submit(new Mmult(id, childContext, task, rec, gpu,
                    newPos(aPosition, 01), newPos(bPosition, 11),
                    new Result(tmp, 5)));
            break;
        case 2:
            c.m._10 = null;
            cons.submit(new Mmult(id, childContext, task, rec, gpu,
                    newPos(aPosition, 11), newPos(bPosition, 10),
                    new Result(tmp, 6)));
            break;
        case 3:
            c.m._11 = null;
            cons.submit(new Mmult(id, childContext, task, rec, gpu,
                    newPos(aPosition, 11), newPos(bPosition, 11),
                    new Result(tmp, 7)));
            break;
        case 4:
            c.m._00 = tmp;
            break;
        case 5:
            c.m._01 = tmp;
            break;
        case 6:
            c.m._10 = tmp;
            break;
        case 7:
            c.m._11 = tmp;
            break;
        case -1:
            c.m = tmp;
            break;
        }
    }

    @Override
    public int process(Constellation cons, Event e) {
        Result r = (Result) e.getData();

        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Job: apos = " + Arrays.toString(aPosition) + ", bpos = "
                            + Arrays.toString(bPosition) + ", got result");
        }
        try {
            processResult(cons, r, identifier());
        } catch (NoSuitableExecutorException e1) {
            logger.error("Could not submit", e);
            return FINISH;
        }
        if (numResults == 8 || r.subjob == -1) {
            if (logger.isDebugEnabled()) {
                logger.debug("Job: apos = " + Arrays.toString(aPosition)
                        + ", bpos = " + Arrays.toString(bPosition)
                        + ", finishing");
            }
            return FINISH;
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Job: apos = " + Arrays.toString(aPosition)
                        + ", bpos = " + Arrays.toString(bPosition)
                        + ", suspending");
            }
            return SUSPEND;
        }
    }

    @Override
    public void cleanup(Constellation cons) {
        if (parent != null) {
            cons.send(new Event(identifier(), parent, c));
        }
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Job: apos = " + Arrays.toString(aPosition) + ", bpos = "
                            + Arrays.toString(bPosition) + ", cleanup done");
        }
    }

    public static int power(int base, int exponent) {
        return (int) Math.pow(base, exponent);
    }

    public static ConstellationConfiguration[] getExecutors(Properties props,
            int task, int rec) {

        String localProp = props.getProperty("cashmere.nLocalExecutors");
        String globalProp = props.getProperty("cashmere.nGlobalExecutors");
        String localProp2 = props.getProperty("cashmere.nLocalExecutors2");

        int localExecutors = 4;
        int globalExecutors = 1;
        int localExecutors2 = 1;
        if (localProp != null) {
            localExecutors = Integer.parseInt(localProp);
        }
        if (globalProp != null) {
            globalExecutors = Integer.parseInt(globalProp);
        }
        if (localProp2 != null) {
            localExecutors2 = Integer.parseInt(localProp2);
        }

        ConstellationConfiguration[] e = new ConstellationConfiguration[localExecutors
                + globalExecutors + localExecutors2 /* + 1 */];

        for (int k = 0; k < localExecutors; k++) {
            e[k] = new ConstellationConfiguration(
                    Util.localContext(0), Util.localPool(1),
                    Util.localPool(0), StealStrategy.SMALLEST,
                    StealStrategy.SMALLEST, StealStrategy.SMALLEST);
        }
        for (int k = 0; k < globalExecutors; k++) {
            e[k + localExecutors] = new ConstellationConfiguration(
                    Util.globalContext(0),
                    StealPool.merge(Util.globalPool(0),
                            Util.localPool(0)),
                    Util.globalPool(0),
                    // StealStrategy.SMALLEST, StealStrategy.ANY,
                    // StealStrategy.BIGGEST);
                    StealStrategy.BIGGEST, StealStrategy.BIGGEST,
                    StealStrategy.SMALLEST);
        }
        for (int k = 0; k < localExecutors2; k++) {
            // These executors are used for Event collectors
            e[k + localExecutors
                    + globalExecutors] = new ConstellationConfiguration(
                            Util.localContext(1),
                            Util.localPool(1), Util.localPool(0),
                            StealStrategy.SMALLEST, StealStrategy.SMALLEST,
                            StealStrategy.SMALLEST);
        }
        return e;
    }

    public static void main(String args[]) throws Exception {
        int task = 2, rec = 2, loop = power(2, 2);
        double time = 0.0;
        boolean gpu = true;

        if (args.length >= 3) {
            task = Integer.parseInt(args[0]);
            rec = Integer.parseInt(args[1]);
            loop = Integer.parseInt(args[2]);
            if (args.length == 4) {
                if (args[3].equals("-cpu")) {
                    gpu = false;
                } else if (args[3].equals("-gpu")) {
                    gpu = true;
                }
            } else if (args.length != 3) {
                System.out
                        .println("usage: mmult [task rec loop [ -gpu | -cpu]]");
                System.exit(66);
            }
            if (loop % 2 == 1) {
                System.err.println("The loop size must be even");
            }
        } else if (args.length != 0) {
            System.out.println("usage: mmult [task rec loop [ -gpu | -cpu]]");
            System.exit(66);
        }

        int cells = power(2, task + rec) * loop;

        a = new Matrix(task, rec, loop, 1.0f, false, true);
        b = new Matrix(task, rec, loop, 1.0f, false, true);

        Properties props = System.getProperties();

        Cashmere.initialize(getExecutors(props, task, rec), props, null,
                power(4, task + rec), 4 * loop * loop);
        Constellation constellation = Cashmere.getConstellation();

        constellation.activate();

        boolean isMaster = constellation.isMaster();

        Matrix c = new Matrix(task, rec, loop, 0.0f, false);

        if (isMaster) {
            System.out.println("Running Matrix multiply, on a matrix of size "
                    + cells + " x " + cells + ", threads = " + power(8, task));

            Timer timer = Cashmere.getOverallTimer();
            int eventNo = timer.start();
            SingleEventCollector a = new SingleEventCollector(
                    Util.localContext(1));
            ActivityIdentifier aid = Cashmere.submit(a);
            Cashmere.submit(new Mmult(aid,
                    new Context(Util.globalName(0), task), task, rec, gpu,
                    new byte[0], new byte[0], new Result(c, -1)));
            c = ((Result) a.waitForEvent().getData()).m;
            timer.stop(eventNo);

            time = timer.totalTimeVal() / 1000000.0; // seconds

        }
        Cashmere.done();
        if (isMaster) {
            System.out.println("checking result, should be " + ((float) cells));
            if (c.check(task, rec, cells)) {
                // System.out.println("\nC:");
                // c.print(task, rec, loop);
                System.out.println("application time Mmult (" + task + "," + rec
                        + "," + loop + "," + cells + ") took " + time + " s");
                System.out.printf("application performance: %f GFLOPS\n",
                        2l * cells * cells * cells / 1.0e9 / time);
                System.out.println("application result Mmult (" + task + ","
                        + rec + "," + loop + "," + cells + ") = OK");
                System.out.println("Test succeeded!");
                //
                // String fileName = String.format("results_%dx%d.data", cells,
                // cells);
                // try {
                // PrintStream outputFile = new PrintStream(
                // new FileOutputStream(fileName, true));
                // outputFile.printf("%d,%d,%d,%d %f\n", task, rec, loop,
                // loop, time);
                // outputFile.close();
                // } catch (FileNotFoundException e) {
                // e.printStackTrace();
                // System.out.println(e);
                // }

            } else {
                System.out.println("application time Mmult (" + task + "," + rec
                        + "," + loop + "," + cells + ") GAVE WRONG RESULT!");
                System.out.println(
                        "application result Mmult (" + task + "," + rec + ","
                                + loop + "," + cells + ") GAVE WRONG RESULT!");
                System.out.println("Test failed!");
            }
        }
    }

    @Override
    public String toString() {
        return "Mmult, task = " + task + ", rec = " + rec;
    }

    @Override
    public void pushByteBuffers(List<ByteBuffer> list) {
        if (c != null) {
            c.pushByteBuffers(list);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Mmult.pushByteBuffers: size = " + list.size());
        }
    }

    @Override
    public void popByteBuffers(List<ByteBuffer> list) {
        if (logger.isDebugEnabled()) {
            logger.debug("Mmult.popByteBuffers: size = " + list.size());
        }
        if (c != null && list.size() != 0) {
            c.popByteBuffers(list);
        }
    }
}
