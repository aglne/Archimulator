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
package archimulator.sim.uncore.coherence.event;

import archimulator.sim.uncore.CacheAccessType;
import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.cache.CacheLine;
import archimulator.sim.uncore.coherence.msi.controller.GeneralCacheController;

public class CoherentCacheLineReplacementEvent extends CoherentCacheEvent {
    private MemoryHierarchyAccess requesterAccess;
    private int address;
    private CacheLine<?> lineFound;
    private boolean hitInCache;
    private boolean eviction;
    private CacheAccessType accessType;

    public CoherentCacheLineReplacementEvent(GeneralCacheController cacheController, MemoryHierarchyAccess requesterAccess, int address, CacheLine<?> lineFound, boolean hitInCache, boolean eviction, CacheAccessType accessType) {
        super(cacheController);

        this.requesterAccess = requesterAccess;
        this.address = address;
        this.lineFound = lineFound;
        this.hitInCache = hitInCache;
        this.eviction = eviction;
        this.accessType = accessType;
    }

    public MemoryHierarchyAccess getRequesterAccess() {
        return requesterAccess;
    }

    public int getAddress() {
        return address;
    }

    public CacheLine<?> getLineFound() {
        return lineFound;
    }

    public boolean isHitInCache() {
        return hitInCache;
    }

    public boolean isEviction() {
        return eviction;
    }

    public CacheAccessType getAccessType() {
        return accessType;
    }

    @Override
    public String toString() {
        return String.format("CoherentCacheLineReplacementEvent{address=0x%08x, requesterAccess=%s, lineFound=%s, hitInCache=%s, eviction=%s, accessType=%s}", address, requesterAccess, lineFound, hitInCache, eviction, accessType);
    }
}
