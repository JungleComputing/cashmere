package ibis.cashmere.constellation.deviceImpl.jcuda;

import static jcuda.driver.JCudaDriver.cuEventDestroy;

import ibis.cashmere.constellation.deviceAPI.DeviceEvent;
import jcuda.driver.CUevent;

// TODO: implement reference counting here?

public class CudaEvent implements DeviceEvent {

    private CUevent evnt;

    public CudaEvent(CUevent evnt) {
        this.evnt = evnt;
    }

    @Override
    public long getTime(TimeType tp) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void retain() {
        // TODO Auto-generated method stub
    }

    @Override
    public void clean() {
        if (evnt != null) {
            cuEventDestroy(evnt);
            evnt = null;
        }
    }

    @Override
    public void show(String type) {
        // TODO Auto-generated method stub

    }

    CUevent getEvent() {
        return evnt;
    }
}