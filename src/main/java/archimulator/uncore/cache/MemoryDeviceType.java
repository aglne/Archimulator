/**
 * ****************************************************************************
 * Copyright (c) 2010-2015 by Min Cai (min.cai.china@gmail.com).
 * <p>
 * This file is part of the Archimulator multicore architectural simulator.
 * <p>
 * Archimulator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Archimulator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with Archimulator. If not, see <http://www.gnu.org/licenses/>.
 * ****************************************************************************
 */
package archimulator.uncore.cache;

/**
 * Memory device type.
 *
 * @author Min Cai
 */
public enum MemoryDeviceType {
    /**
     * L1 instruction cache controller.
     */
    L1I_CONTROLLER,

    /**
     * L1 data cache controller.
     */
    L1D_CONTROLLER,

    /**
     * L2 cache controller.
     */
    L2_CONTROLLER,

    /**
     * Memory controller.
     */
    MEMORY_CONTROLLER
}
