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
package archimulator.uncore.coherence.msi.event.cache;

import archimulator.uncore.MemoryHierarchyAccess;
import archimulator.uncore.coherence.msi.controller.CacheController;
import archimulator.uncore.coherence.msi.event.ControllerEvent;
import archimulator.uncore.coherence.msi.flow.CacheCoherenceFlow;

/**
 * L1 cache controller event.
 *
 * @author Min Cai
 */
public abstract class CacheControllerEvent extends ControllerEvent {
    private CacheControllerEventType type;

    /**
     * Create an L1 cache controller event.
     *
     * @param generator    the generator L1 cache controller
     * @param producerFlow the producer flow
     * @param type         the type of the cache controller event
     * @param access       the memory hierarchy access
     * @param tag          the tag
     */
    public CacheControllerEvent(CacheController generator, CacheCoherenceFlow producerFlow, CacheControllerEventType type, MemoryHierarchyAccess access, int tag) {
        super(generator, producerFlow, access, tag);
        this.type = type;
    }

    /**
     * Get the type of the cache controller event.
     *
     * @return the type of the cache controller event
     */
    public CacheControllerEventType getType() {
        return type;
    }
}
