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
package archimulator.sim.uncore.coherence.llc;

import archimulator.sim.uncore.CacheHierarchy;
import archimulator.sim.uncore.MemoryDevice;
import archimulator.sim.uncore.coherence.common.CoherentCache;
import archimulator.sim.uncore.coherence.common.MESIState;
import archimulator.sim.uncore.coherence.config.CoherentCacheConfig;
import archimulator.sim.uncore.coherence.flc.FirstLevelCache;
import archimulator.sim.uncore.coherence.flow.llc.L1DownwardReadFlow;
import archimulator.sim.uncore.coherence.flow.llc.L1DownwardWriteFlow;
import archimulator.sim.uncore.coherence.flow.llc.L1EvictFlow;
import archimulator.sim.uncore.coherence.message.DownwardReadMessage;
import archimulator.sim.uncore.coherence.message.DownwardWriteMessage;
import archimulator.sim.uncore.coherence.message.EvictMessage;
import archimulator.sim.uncore.coherence.message.MemoryDeviceMessage;
import archimulator.sim.uncore.dram.MainMemory;
import archimulator.sim.uncore.net.Net;
import archimulator.util.action.Action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LastLevelCache extends CoherentCache {
    private Map<FirstLevelCache, ShadowTagDirectory> shadowTagDirectories;

    public LastLevelCache(CacheHierarchy cacheHierarchy, String name, CoherentCacheConfig config) {
        super(cacheHierarchy, name, config, MESIState.INVALID);

        this.shadowTagDirectories = new LinkedHashMap<FirstLevelCache, ShadowTagDirectory>();
    }

    @Override
    protected Net getNet(MemoryDevice to) {
        return to instanceof MainMemory ? this.getCacheHierarchy().getL2ToMemNetwork() : this.getCacheHierarchy().getL1sToL2Network();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void receiveRequest(MemoryDevice source, final MemoryDeviceMessage message) {
        switch (message.getType()) {
            case EVICT:
                L1EvictFlow l1EvictFlow = new L1EvictFlow(this, (FirstLevelCache) source, (EvictMessage) message);
                l1EvictFlow.start(
                        new Action() {
                            @Override
                            public void apply() {
                            }
                        }, new Action() {
                            @Override
                            public void apply() {
                                message.setError(true);
                            }
                        }
                );
                break;
            case DOWNWARD_READ:
                L1DownwardReadFlow l1DownwardReadFlow = new L1DownwardReadFlow(this, (FirstLevelCache) source, (DownwardReadMessage) message);
                l1DownwardReadFlow.start(
                        new Action() {
                            @Override
                            public void apply() {
                            }
                        }, new Action() {
                            @Override
                            public void apply() {
                                message.setError(true);
                            }
                        }
                );
                break;
            case DOWNWARD_WRITE:
                L1DownwardWriteFlow l1DownwardWriteFlow = new L1DownwardWriteFlow(this, (FirstLevelCache) source, (DownwardWriteMessage) message);
                l1DownwardWriteFlow.run(
                        new Action() {
                            @Override
                            public void apply() {
                            }
                        }, new Action() {
                            @Override
                            public void apply() {
                                message.setError(true);
                            }
                        }
                );
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    public void addShadowTagDirectoryForL1(FirstLevelCache l1Cache) {
        this.shadowTagDirectories.put(l1Cache, new ShadowTagDirectory(l1Cache));
    }

    public Map<FirstLevelCache, ShadowTagDirectory> getShadowTagDirectories() {
        return shadowTagDirectories;
    }

    public boolean isShared(int addr) {
        return this.getSharers(addr).size() > 1;
    }

    public boolean isOwned(int addr) {
        return this.getSharers(addr).size() == 1;
    }

    public boolean isOwnedOrShared(int addr) {
        return this.getSharers(addr).size() > 0;
    }

    public FirstLevelCache getOwnerOrFirstSharer(int addr) {
        return this.getSharers(addr).get(0);
    }

    public List<FirstLevelCache> getSharers(int addr) {
        List<FirstLevelCache> sharers = new ArrayList<FirstLevelCache>();
        for (Map.Entry<FirstLevelCache, ShadowTagDirectory> entry : this.shadowTagDirectories.entrySet()) {
            if (entry.getValue().containsTag(addr)) {
                sharers.add(entry.getKey());
            }
        }
        return sharers;
    }

    public void setNext(MainMemory next) {
        super.setNext(next);
    }

    public MainMemory getNext() {
        return (MainMemory) super.getNext();
    }
}
