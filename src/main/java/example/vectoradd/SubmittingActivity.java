package example.vectoradd;

import java.util.ArrayList;
import java.util.Arrays;

import ibis.constellation.AbstractContext;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.Event;
import ibis.constellation.NoSuitableExecutorException;

// A SubmittingActivity extends ParentActivity with result VectorAddResult.

// It keeps track of ParentActivities that need to be submitted.  It expects
// each of these parent activities to return an event and it will keep track of
// those events.

// As such, this Activity can implement the initialize() that submits all the
// activities (that need to be initialized by the implementer) and the
// process() that simply processes an event for each submitted activity.  The
// process() method will finish if all results have been received.

class SubmittingActivity extends ParentActivity<VectorAddResult> {

    protected ArrayList<ParentActivity<VectorAddResult>> activitiesToSubmit;
    protected int nResults;

    protected int n;
    protected boolean mc;
    protected float[] a;
    protected float[] b;

    protected SubmittingActivity(ActivityIdentifier parent,
            AbstractContext context, boolean restrictToLocal,
            boolean willReceiveEvents, int n, boolean mc, float[] a,
            float[] b) {
        super(parent, context, restrictToLocal, willReceiveEvents);

        this.n = n;
        this.mc = mc;
        this.a = a;
        this.b = b;
        this.nResults = 0;
    }

    @Override
    // Submit each activity in activitiesToSubmit and suspend.
    public int initialize(Constellation cons) {
        for (ParentActivity<VectorAddResult> activity : activitiesToSubmit) {
            try {
                cons.submit(activity);
            } catch (NoSuitableExecutorException e) {
                throw new Error(e);
            }
        }
        return SUSPEND;
    }

    @Override
    // Process all the events and afterwards finish()
    public int process(Constellation cons, Event event) {
        nResults++;
        result.add((VectorAddResult) event.getData());
        if (nResults == activitiesToSubmit.size()) {
            return FINISH;
        } else {
            return SUSPEND;
        }
    }

    // A functional interface that allows one to provide an anonymous function
    // that initializes an activity to divideInActivities
    public interface InitActivity<T extends ParentActivity<VectorAddResult>> {
        public T apply(int n, float[] a, float[] b, int offset);
    }

    // Divide the input of size n over nActivities activities. Using
    // initActivity, we create a new Activity that we put in the array of
    // activities that we return.
    protected <T extends ParentActivity<VectorAddResult>> ArrayList<T> divideInActivities(
            int n, int nActivities, float[] a, float[] b,
            InitActivity<T> initActivity) {
        ArrayList<T> ts = new ArrayList<T>(nActivities);

        int nElsPerActivity = n / nActivities;

        for (int i = 0; i < nActivities; i++) {
            int offset = i * nElsPerActivity;
            float[] aCopy = Arrays.copyOfRange(a, offset,
                    offset + nElsPerActivity);
            float[] bCopy = Arrays.copyOfRange(b, offset,
                    offset + nElsPerActivity);
            T t = initActivity.apply(nElsPerActivity, aCopy, bCopy, offset);
            ts.add(0, t);
        }

        return ts;
    }
}
