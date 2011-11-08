/*******************************************************************************
 * Copyright (c) 2010-2011 by Min Cai (min.cai.china@gmail.com).
 *
 * This file is part of the Archimulator multicore architectural simulator.
 *
 * Archimulator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Archimulator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Archimulator. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package archimulator.core;

import archimulator.mem.CacheHierarchy;
import archimulator.os.Kernel;
import archimulator.sim.SimulationObject;
import archimulator.sim.capability.ProcessorCapability;

import java.util.List;

public interface Processor extends SimulationObject {
    List<Core> getCores();

    ProcessorConfig getConfig();

    Kernel getKernel();

    void updateContextToThreadAssignments();

    CacheHierarchy getCacheHierarchy();

    <CapabilityT extends ProcessorCapability> CapabilityT getCapability(Class<? extends CapabilityT> clz);
}
