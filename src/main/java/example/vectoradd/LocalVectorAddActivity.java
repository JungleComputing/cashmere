package example.vectoradd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.Kernel;
import ibis.cashmere.constellation.KernelLaunch;
import ibis.cashmere.constellation.Cashmere;
import ibis.cashmere.constellation.CashmereNotAvailable;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.Event;
import ibis.constellation.Timer;

import example.util.Util;

public class LocalVectorAddActivity extends ParentActivity<VectorAddResult> {

    public static Logger logger = LoggerFactory
            .getLogger("VectorAdd.LocalVectorAddActivity");

    private boolean mc;
    private float[] a;
    private float[] b;

    LocalVectorAddActivity(ActivityIdentifier parent, int n, boolean mc,
            float[] a, float[] b, int offsetInParent) {
        // this activity has the 'local activity context 0', will only execute
        // locally and will not receive events
        super(parent, Util.localContext(0), true, false);

        // record the data structures to execute
        this.mc = mc;
        this.a = a;
        this.b = b;

        this.result = new VectorAddResult(new float[n], offsetInParent);

        if (logger.isDebugEnabled()) {
            logger.debug("Initialized with {} elements and offsetInParent {}",
                    n, offsetInParent);
        }
    }

    @Override
    // Overridden from Activity: We can immediately compute here and finish the
    // activity.
    public int initialize(Constellation cons) {
        result.c = mc ? addVectorMC(cons, a, b) : addVectorCPU(cons, a, b);
        return FINISH;
    }

    @Override
    // Overridden from Activity: This activity does not receive events, so
    // nothing
    // to do
    public int process(Constellation cons, Event event) {
        return FINISH;
    }

    private float[] addVectorCPU(Constellation cons, float[] a, float[] b) {
        Timer timer = Cashmere.getTimer("java",
                cons.identifier().toString(), "vectoradd cpu");
        int event = timer.start();

        if (logger.isDebugEnabled()) {
            logger.debug("Executing vectoradd of size " + a.length);
        }

        float[] sum = new float[a.length];

        for (int i = 0; i < a.length; i++) {
            sum[i] = a[i] + b[i];
        }
        timer.stop(event);
        return sum;
    }

    private float[] addVectorMC(Constellation cons, float[] a, float[] b) {
        float[] sum = new float[a.length];
        try {
            Kernel kernel = Cashmere.getKernel("vectoraddKernel");
            KernelLaunch kernelLaunch = kernel.createLaunch();

            if (logger.isDebugEnabled()) {
                logger.debug("Executing vectoradd of size " + a.length);
            }

            // Changed MCL a bit: added a true to KernelLaunch.launch to make it
            // synchronous. By default the calls are asynchronous.
            MCL.launchVectoraddKernel(kernelLaunch, a.length, sum, a, b);

            return sum;
        } catch (CashmereNotAvailable e) {
            logger.warn("fallback to CPU", e);
            return addVectorCPU(cons, a, b);
        } catch (RuntimeException | Error e) {
            e.printStackTrace(System.out);
            throw e;
        }
    }
}
