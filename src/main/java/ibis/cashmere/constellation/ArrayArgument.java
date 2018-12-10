/*
 * Copyright 2018 Vrije Universiteit Amsterdam, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ibis.cashmere.constellation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ibis.cashmere.constellation.deviceAPI.CommandStream;
import ibis.cashmere.constellation.deviceAPI.Device;

public class ArrayArgument extends Argument {

    protected CommandStream readQueue;
    private int referenceCount;
    protected final Device device;

    private static final Logger memlogger = LoggerFactory.getLogger("ibis.cashmere.constellation.Device/memory");

    protected ArrayArgument(Device device, Direction d, CommandStream readQueue) {
        super(d);
        this.readQueue = readQueue;
        this.device = device;

        this.referenceCount = 1;
    }

    @Override
    void clean() {
        if (direction == Direction.OUT || direction == Direction.INOUT) {
            transformBack();
        }
        super.clean();
    }

    public synchronized int decrementAndGetRefCount() {
        referenceCount--;
        if (referenceCount == 0) {
            memlogger.debug("  about to clean");
            clean();
            memlogger.debug("  did a clean");
        } else {
            if (memlogger.isDebugEnabled()) {
                memlogger.debug("referenceCount for {}: {}", this, referenceCount);
            }
        }
        return referenceCount;
    }

    public void incRefCount() {
        referenceCount++;
        if (memlogger.isDebugEnabled()) {
            memlogger.debug("Reference count for {}: {}", this, referenceCount);
        }
    }

    protected void transform() {
    }

    protected void transformBack() {
    }
}
