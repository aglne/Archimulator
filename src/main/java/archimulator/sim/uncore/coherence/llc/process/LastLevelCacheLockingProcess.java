/*******************************************************************************
 * Copyright (c) 2010-2012 by Min Cai (min.cai.china@gmail.com).
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
package archimulator.sim.uncore.coherence.llc.process;

import archimulator.sim.uncore.CacheAccessType;
import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.common.process.FindAndLockProcess;
import archimulator.sim.uncore.coherence.common.process.LockingProcess;
import archimulator.sim.uncore.coherence.llc.LastLevelCache;

public abstract class LastLevelCacheLockingProcess extends LockingProcess {
    public LastLevelCacheLockingProcess(LastLevelCache cache, final MemoryHierarchyAccess access, final int tag, final CacheAccessType cacheAccessType) {
        super(cache, access, tag, cacheAccessType);
    }

    @Override
    public FindAndLockProcess newFindAndLockProcess(CacheAccessType cacheAccessType) {
        return new LastLevelCacheFindAndLockProcess(getCache(), this.access, this.tag, cacheAccessType);
    }

    public LastLevelCache getCache() {
        return (LastLevelCache) super.getCache();
    }
}
