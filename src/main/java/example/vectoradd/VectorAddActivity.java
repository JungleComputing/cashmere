package example.vectoradd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.Event;

import example.util.Util;

public class VectorAddActivity extends SubmittingActivity {

    public static Logger logger = LoggerFactory
            .getLogger("VectorAdd.VectorAddActivity");

    private int nGlobalActivities;
    private int nLocalActivities;

    VectorAddActivity(ActivityIdentifier parent, int nGlobalActivities,
            int nLocalActivities, int n, boolean mc, float[] a, float[] b) {

        // this activity has the 'global activity context 1, is not restricted
        // to local,
        // will receive events, and records n, mc, a, and b.
        super(parent, Util.globalContext(1), false, true, n, mc,
                a, b);

        this.nGlobalActivities = nGlobalActivities;
        this.nLocalActivities = nLocalActivities;

        // we create a result data structure with an array of length n, and an
        // offset of 0
        this.result = new VectorAddResult(new float[n], 0);

        if (logger.isDebugEnabled()) {
            logger.debug("Initialized with {} elements", n);
        }
    }

    @Override
    public int initialize(Constellation cons) {
        // we create an array of global activities to submit
        this.activitiesToSubmit = divideInActivities(n, nGlobalActivities, a, b,
                (nCopy, aCopy, bCopy,
                        offsetCopy) -> new GlobalVectorAddActivity(identifier(),
                                nLocalActivities, nCopy, mc, aCopy, bCopy,
                                offsetCopy));

        if (logger.isDebugEnabled()) {
            logger.debug("Submitting {} GlobalVectorAddActivities",
                    activitiesToSubmit.size());
        }
        return super.initialize(cons);
    }

    @Override
    public int process(Constellation cons, Event event) {
        if (logger.isDebugEnabled()) {
            logger.debug("Processing an event");
        }
        return super.process(cons, event);
    }
}
