package example.mmult;

import java.util.ArrayList;

import ibis.constellation.AbstractContext;
import ibis.constellation.Activity;
import ibis.constellation.Constellation;
import ibis.constellation.Context;
import ibis.constellation.Event;

public class FlexibleEventCollector extends Activity {

    private static final long serialVersionUID = -538414301465754654L;

    private final ArrayList<Event> events = new ArrayList<Event>();
    private boolean waiting = false;
    private int count;

    public FlexibleEventCollector(AbstractContext c) {
        super(c, true);
    }

    public FlexibleEventCollector() {
        super(Context.DEFAULT, true);
    }

    @Override
    public int initialize(Constellation cons) {

        if (Mmult.logger.isDebugEnabled()) {
            Mmult.logger.debug("FlexibleEventCollector.initialize");
        }

        return SUSPEND;
    }

    @Override
    public synchronized int process(Constellation cons, Event e) {

        if (Mmult.logger.isDebugEnabled()) {
            Mmult.logger.debug("FlexibleEventCollector.process called");
        }
        events.add(e);
        count++;

        if (waiting) {
            notifyAll();
        }

        return SUSPEND;
    }

    @Override
    public void cleanup(Constellation cons) {
        // empty
    }

    @Override
    public String toString() {
        return "FlexibleEventCollector(" + identifier() + ")";
    }

    public synchronized Event[] waitForEvents() {

        while (events.size() == 0) {

            waiting = true;

            try {
                wait();
            } catch (Exception e) {
                // ignore
            }

            waiting = false;
        }

        Event[] result = events.toArray(new Event[events.size()]);

        events.clear();

        return result;
    }
}
