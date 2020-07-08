package example.vectoradd;

import ibis.constellation.AbstractContext;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.Event;

// The ParentActivity extends Activity and keeps track of a parent and a
// result, paramaterized on type T.  This Activity implements cleanup that
// sends the result to the parent.
public abstract class ParentActivity<T> extends Activity {

    protected ActivityIdentifier parent;
    protected T result;

    protected ParentActivity(ActivityIdentifier parent, AbstractContext context,
            boolean restrictToLocal, boolean willReceiveEvents) {
        super(context, ! restrictToLocal, willReceiveEvents);

        this.parent = parent;
    }

    protected ParentActivity(ActivityIdentifier parent, AbstractContext context,
            boolean willReceiveEvents) {
        this(parent, context, false, willReceiveEvents);
    }

    @Override
    public void cleanup(Constellation cons) {
        cons.send(new Event(identifier(), parent, result));
    }
}
