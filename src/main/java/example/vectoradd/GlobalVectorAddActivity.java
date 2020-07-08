package example.vectoradd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.Cashmere;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.Event;

import example.util.Util;

public class GlobalVectorAddActivity extends SubmittingActivity {

    public static Logger logger = LoggerFactory
            .getLogger("VectorAdd.GlobalVectorAddActivity");

    private int nLocalActivities;

    GlobalVectorAddActivity(ActivityIdentifier parent, int nLocalActivities,
            int n, boolean mc, float[] a, float[] b, int offsetInParent) {
        // this activity has the 'global activity context 0, is not restricted
        // to local, will receive events, and records n, mc, a, and b.
        super(parent, Util.globalContext(0), false, true, n, mc,
                a, b);

        this.nLocalActivities = nLocalActivities;

        // Create a new result and remember the offset in the parent.
        this.result = new VectorAddResult(new float[n], offsetInParent);

        if (logger.isDebugEnabled()) {
            logger.debug("Initialized with {} elements and offsetInParent {}",
                    n, offsetInParent);
        }
    }

    @Override
    public int initialize(Constellation cons) {
        this.activitiesToSubmit = divideInActivities(n, nLocalActivities, a, b,
                (nCopy, aCopy, bCopy, offsetCopy) -> new LocalVectorAddActivity(
                        identifier(), nCopy, mc, aCopy, bCopy, offsetCopy));

        if (logger.isDebugEnabled()) {
            logger.debug("Submitting {} LocalVectorAddActivities",
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
