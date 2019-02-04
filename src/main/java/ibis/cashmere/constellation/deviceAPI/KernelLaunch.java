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

package ibis.cashmere.constellation.deviceAPI;

import ibis.cashmere.constellation.Kernel;
import ibis.cashmere.constellation.Launch;

/**
 * Represents one specific launch of a <code>Kernel</code> . While {@link #launch launch} methods can only be called once, it is
 * possible to create multiple launches from a {@link Kernel}.
 */
public abstract class KernelLaunch extends Launch {

    // A KernelLaunch can only be created from within the package or subclass.
    protected KernelLaunch(String kernelName, String threadName, Device device) {
        super(kernelName, threadName, device);
    }

    /**
     * Launch the <code>Kernel</code> with the specified parameters. The launch will be a synchronous launch.
     *
     * @param gridX
     *            the size of the grid in the X direction
     * @param gridY
     *            the size of the grid in the Y direction
     * @param gridZ
     *            the size of the grid in the Z direction
     * @param blockX
     *            the size of the block in the X direction
     * @param blockY
     *            the size of the block in the Y direction
     * @param blockZ
     *            the size of the block in the Z direction
     */
    public final void launch(int gridX, int gridY, int gridZ, int blockX, int blockY, int blockZ) {
        launch(gridX, gridY, gridZ, blockX, blockY, blockZ, true);
    }

    /**
     * Launch the <code>Kernel</code> with the specified parameters.
     *
     * @param gridX
     *            the size of the grid in the X direction
     * @param gridY
     *            the size of the grid in the Y direction
     * @param gridZ
     *            the size of the grid in the Z direction
     * @param blockX
     *            the size of the block in the X direction
     * @param blockY
     *            the size of the block in the Y direction
     * @param blockZ
     *            the size of the block in the Z direction
     * @param synchronous
     *            indicates whether the launch should be synchronous or asynchronous
     */
    public abstract void launch(int gridX, int gridY, int gridZ, int blockX, int blockY, int blockZ, boolean synchronous);
}
