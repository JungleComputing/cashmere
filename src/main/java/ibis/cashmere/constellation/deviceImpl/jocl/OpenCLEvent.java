package ibis.cashmere.constellation.deviceImpl.jocl;

import static org.jocl.CL.CL_EVENT_REFERENCE_COUNT;
import static org.jocl.CL.clGetEventInfo;
import static org.jocl.CL.clReleaseEvent;
import static org.jocl.CL.clRetainEvent;

import java.util.concurrent.atomic.AtomicInteger;

import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_event;

import ibis.cashmere.constellation.deviceAPI.DeviceEvent;

public class OpenCLEvent implements DeviceEvent {

    /*
     * Debugging
     */
    private static AtomicInteger nrEvents = new AtomicInteger();

    private final cl_event event;

    public OpenCLEvent(cl_event event) {
        this.event = event;
    }

    @Override
    public long getTime(TimeType tp) {
        // TODO Auto-generated method stub
        return 0;
    }

    public void showEvent(String type) {
        if (type == null) {
            type = "unknown";
        }
        int[] result = new int[1];
        clGetEventInfo(event, CL_EVENT_REFERENCE_COUNT, Sizeof.cl_uint, Pointer.to(result), null);
        logger.debug(String.format("%s event %s with refcount: %d", type, event, result[0]));
    }

    public void retain() {
        if (logger.isDebugEnabled()) {
            logger.debug("about to do a clRetainEvent on {}", event);
            nrEvents.incrementAndGet();
        }
        clRetainEvent(event);
    }

    public void clean() {
        int nrReferences = 0;
        if (logger.isDebugEnabled()) {
            int[] result = new int[1];
            clGetEventInfo(event, CL_EVENT_REFERENCE_COUNT, Sizeof.cl_uint, Pointer.to(result), null);
            nrReferences = result[0];
        }

        clReleaseEvent(event);
        if (logger.isDebugEnabled()) {
            nrEvents.decrementAndGet();

            logger.debug(String.format("releasing event %s with refcount: %d", event, nrReferences));
            if (nrReferences > 1) {
                logger.debug("{} still needs to be released another time", event);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof OpenCLEvent)) {
            return false;
        }
        return event.equals(((OpenCLEvent) o).event);
    }

    @Override
    public int hashCode() {
        return event.hashCode();
    }
}
