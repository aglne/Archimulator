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
package archimulator.sim.ext.uncore.llc;

import archimulator.sim.base.event.DumpStatEvent;
import archimulator.sim.base.event.PollStatsEvent;
import archimulator.sim.base.event.ResetStatEvent;
import archimulator.sim.base.experiment.capability.SimulationCapability;
import archimulator.sim.base.simulation.Simulation;
import archimulator.sim.core.BasicThread;
import archimulator.sim.uncore.CacheAccessType;
import archimulator.sim.uncore.cache.*;
import archimulator.sim.uncore.cache.eviction.LRUPolicy;
import archimulator.sim.uncore.coherence.event.CoherentCacheLastPutSOrPutMAndDataFromOwnerEvent;
import archimulator.sim.uncore.coherence.event.CoherentCacheLineReplacementEvent;
import archimulator.sim.uncore.coherence.event.CoherentCacheNonblockingRequestHitToTransientTagEvent;
import archimulator.sim.uncore.coherence.event.CoherentCacheServiceNonblockingRequestEvent;
import archimulator.sim.uncore.coherence.msi.controller.DirectoryController;
import archimulator.util.ValueProvider;
import archimulator.util.ValueProviderFactory;
import net.pickapack.action.Action1;
import net.pickapack.event.BlockingEvent;
import net.pickapack.event.BlockingEventDispatcher;

import java.util.HashMap;
import java.util.Map;

public class HTLLCRequestProfilingCapability implements SimulationCapability {
    private DirectoryController llc;

    private Map<Integer, Map<Integer, Integer>> llcLineBroughterThreadIds;
    private EvictableCache<HTLLCRequestVictimCacheLineState> htLLCRequestVictimCache;

    private long numMTLLCMisses;

    private long numTotalHTLLCRequests;

    private long numUsefulHTLLCRequests;

    private long numGoodHTLLCRequests;
    private long numBadHTLLCRequests;

    private long numLateHTLLCRequests;

    private BlockingEventDispatcher<HTLLCRequestProfilingCapabilityEvent> eventDispatcher;

    public HTLLCRequestProfilingCapability(Simulation simulation) {
        this(simulation.getProcessor().getCacheHierarchy().getL2Cache());
    }

    public HTLLCRequestProfilingCapability(DirectoryController llc) {
        this.llc = llc;

        this.llcLineBroughterThreadIds = new HashMap<Integer, Map<Integer, Integer>>();
        for (int set = 0; set < this.llc.getCache().getNumSets(); set++) {
            HashMap<Integer, Integer> llcLineBroughterThreadIdsPerSet = new HashMap<Integer, Integer>();
            this.llcLineBroughterThreadIds.put(set, llcLineBroughterThreadIdsPerSet);

            for (int way = 0; way < this.llc.getCache().getAssociativity(); way++) {
                llcLineBroughterThreadIdsPerSet.put(way, -1);
            }
        }

        ValueProviderFactory<HTLLCRequestVictimCacheLineState, ValueProvider<HTLLCRequestVictimCacheLineState>> cacheLineStateProviderFactory = new ValueProviderFactory<HTLLCRequestVictimCacheLineState, ValueProvider<HTLLCRequestVictimCacheLineState>>() {
            @Override
            public ValueProvider<HTLLCRequestVictimCacheLineState> createValueProvider(Object... args) {
                int set = (Integer) args[0];
                int way = (Integer) args[1];

                return new HTLLCRequestVictimCacheLineStateValueProvider(set,  way);
            }
        };

        this.htLLCRequestVictimCache = new EvictableCache<HTLLCRequestVictimCacheLineState>(llc, llc.getName() + ".htLLCRequestVictimCache", llc.getCache().getGeometry(), LRUPolicy.class, cacheLineStateProviderFactory);

        this.eventDispatcher = new BlockingEventDispatcher<HTLLCRequestProfilingCapabilityEvent>();

        llc.getBlockingEventDispatcher().addListener(CoherentCacheServiceNonblockingRequestEvent.class, new Action1<CoherentCacheServiceNonblockingRequestEvent>() {
            public void apply(CoherentCacheServiceNonblockingRequestEvent event) {
                if (event.getCacheController().equals(HTLLCRequestProfilingCapability.this.llc)) {
//                    pw.printf(
//                            "[%d] llc.[%d,%d] {%s} %s: %s {%s} %s 0x%08x: 0x%08x (%s)\n",
//                            HTLLCRequestProfilingCapability.this.llc.getCycleAccurateEventQueue().getCurrentCycle(),
//                            event.getLineFound().getSet(),
//                            event.getLineFound().getWay(),
//                            event.getLineFound().getState(),
//                            (event.getLineFound().getState() != event.getLineFound().getInitialState() ? String.format("0x%08x", event.getLineFound().getTag()) : "N/A"),
//                            event.getRequesterAccess().getThread().getName(),
//                            event.getRequesterAccess().getId(),
//                            event.getRequesterAccess().getType(),
//                            event.getRequesterAccess().getVirtualPc(),
//                            event.getRequesterAccess().getPhysicalTag(),
//                            event.isHitInCache() ? "hit" : "miss"
//                    );
//                    pw.flush();

                    boolean requesterIsHT = BasicThread.isHelperThread(event.getRequesterAccess().getThread());
                    CacheLine<?> llcLine = event.getLineFound();

                    int set = llcLine.getSet();

                    boolean lineFoundIsHT = HTLLCRequestProfilingCapability.this.getLLCLineBroughterThreadId(set, llcLine.getWay()) == BasicThread.getHelperThreadId();

                    handleLineFill(event, requesterIsHT, llcLine, set, lineFoundIsHT);

                    handleRequest(event, requesterIsHT, llcLine, set, lineFoundIsHT);
                }
            }
        });

        llc.getBlockingEventDispatcher().addListener(CoherentCacheLineReplacementEvent.class, new Action1<CoherentCacheLineReplacementEvent>() {
            @Override
            public void apply(CoherentCacheLineReplacementEvent event) {
                handleReplacement(event);
            }
        });

        llc.getBlockingEventDispatcher().addListener(CoherentCacheLastPutSOrPutMAndDataFromOwnerEvent.class, new Action1<CoherentCacheLastPutSOrPutMAndDataFromOwnerEvent>() {
            @Override
            public void apply(CoherentCacheLastPutSOrPutMAndDataFromOwnerEvent event) {
//                pw.printf(
//                        "[%d] llc.[%d,%d] {%s} %s: %s {%s} %s 0x%08x: 0x%08x LAST_PUTS or PUTM_AND_DATA_FROM_OWNER\n",
//                        HTLLCRequestProfilingCapability.this.llc.getCycleAccurateEventQueue().getCurrentCycle(),
//                        event.getLineFound().getSet(),
//                        event.getLineFound().getWay(),
//                        event.getLineFound().getState(),
//                        (event.getLineFound().getState() != event.getLineFound().getInitialState() ? String.format("0x%08x", event.getLineFound().getTag()) : "N/A"),
//                        event.getRequesterAccess().getThread().getName(),
//                        event.getRequesterAccess().getId(),
//                        event.getRequesterAccess().getType(),
//                        event.getRequesterAccess().getVirtualPc(),
//                        event.getRequesterAccess().getPhysicalTag()
//                );
//                pw.flush();

                CacheLine<?> llcLine = event.getLineFound();

                int set = llcLine.getSet();

                boolean lineFoundIsHT = HTLLCRequestProfilingCapability.this.getLLCLineBroughterThreadId(set, llcLine.getWay()) == BasicThread.getHelperThreadId();

                markInvalid(set, llcLine.getWay());

                if(lineFoundIsHT) {
                    removeLRU(set);
                }
            }
        });

        llc.getBlockingEventDispatcher().addListener(CoherentCacheNonblockingRequestHitToTransientTagEvent.class, new Action1<CoherentCacheNonblockingRequestHitToTransientTagEvent>() {
            @SuppressWarnings("Unchecked")
            public void apply(CoherentCacheNonblockingRequestHitToTransientTagEvent event) {
                if (event.getCacheController().equals(HTLLCRequestProfilingCapability.this.llc)) {
                    markLateHTRequest(event);
                }
            }
        });

        llc.getBlockingEventDispatcher().addListener(ResetStatEvent.class, new Action1<ResetStatEvent>() {
            public void apply(ResetStatEvent event) {
                numMTLLCMisses = 0;

                numTotalHTLLCRequests = 0;

                numUsefulHTLLCRequests = 0;

                numGoodHTLLCRequests = 0;
                numBadHTLLCRequests = 0;

                numLateHTLLCRequests = 0;
            }
        });

        llc.getBlockingEventDispatcher().addListener(PollStatsEvent.class, new Action1<PollStatsEvent>() {
            public void apply(PollStatsEvent event) {
                dumpStats(event.getStats());
            }
        });

        llc.getBlockingEventDispatcher().addListener(DumpStatEvent.class, new Action1<DumpStatEvent>() {
            public void apply(DumpStatEvent event) {
                if (event.getType() == DumpStatEvent.Type.DETAILED_SIMULATION) {
                    dumpStats(event.getStats());
                }
            }
        });
    }

    private void dumpStats(Map<String, Object> stats) {
        stats.put("llcHTRequestProfilingCapability." + this.llc.getName() + ".numMTLLCMisses", String.valueOf(this.numMTLLCMisses));

        stats.put("llcHTRequestProfilingCapability." + this.llc.getName() + ".numTotalHTLLCRequests", String.valueOf(this.numTotalHTLLCRequests));

        stats.put("llcHTRequestProfilingCapability." + this.llc.getName() + ".numUsefulHTLLCRequests", String.valueOf(this.numUsefulHTLLCRequests));

        stats.put("llcHTRequestProfilingCapability." + this.llc.getName() + ".htLLCRequestAccuracy", String.valueOf(100.0 * (double) this.numUsefulHTLLCRequests / this.numTotalHTLLCRequests) + "%");
        stats.put("llcHTRequestProfilingCapability." + this.llc.getName() + ".htLLCRequestCoverage", String.valueOf(100.0 * (double) this.numUsefulHTLLCRequests / (this.numMTLLCMisses + this.numUsefulHTLLCRequests)) + "%");

        stats.put("llcHTRequestProfilingCapability." + this.llc.getName() + ".numGoodHTLLCRequests", String.valueOf(this.numGoodHTLLCRequests));
        stats.put("llcHTRequestProfilingCapability." + this.llc.getName() + ".numBadHTLLCRequests", String.valueOf(this.numBadHTLLCRequests));
        stats.put("llcHTRequestProfilingCapability." + this.llc.getName() + ".numUglyHTLLCRequests", String.valueOf(this.numTotalHTLLCRequests - this.numGoodHTLLCRequests - this.numBadHTLLCRequests));

        stats.put("llcHTRequestProfilingCapability." + this.llc.getName() + ".numLateHTLLCRequests", String.valueOf(this.numLateHTLLCRequests));
    }

    private void markLateHTRequest(CoherentCacheNonblockingRequestHitToTransientTagEvent event) {
        boolean requesterIsHT = BasicThread.isHelperThread(event.getRequesterAccess().getThread());
        CacheLine<?> llcLine = event.getLineFound();

        int set = llcLine.getSet();

        boolean lineFoundIsHT = this.getLLCLineBroughterThreadId(set, llcLine.getWay()) == BasicThread.getHelperThreadId();

        if (!requesterIsHT && lineFoundIsHT) {
            this.numLateHTLLCRequests++;
            this.eventDispatcher.dispatch(new LateHTLLCRequestEvent());
        }
    }

//    private static PrintWriter pw;

    static {
//        try {
//            pw = new PrintWriter(new FileWriter(FileUtils.getUserDirectoryPath() + "/event_trace.txt"));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }

    private void handleLineFill(CoherentCacheServiceNonblockingRequestEvent event, boolean requesterIsHT, CacheLine<?> llcLine, int set, boolean lineFoundIsHT) {
        if (!event.isHitInCache()) {
            if (requesterIsHT) {
                this.numTotalHTLLCRequests++;
                this.eventDispatcher.dispatch(new HTLLCRequestEvent());
            }
        }

        if (!requesterIsHT) {
            if (!event.isHitInCache()) {
                this.numMTLLCMisses++;
            } else if (event.isHitInCache() && lineFoundIsHT) {
                this.numUsefulHTLLCRequests++;
            }
        }

        if (requesterIsHT && !event.isHitInCache() && !event.isEviction()) {
            this.markHT(set, llcLine.getWay());
            this.insertNullEntry(set);
        } else if (requesterIsHT && !event.isHitInCache() && event.isEviction() && !lineFoundIsHT) {
            this.markHT(set, llcLine.getWay());
            this.insertDataEntry(set, llcLine.getTag());
        } else if (requesterIsHT && !event.isHitInCache() && event.isEviction() && lineFoundIsHT) {
        } else if (!requesterIsHT && !event.isHitInCache() && event.isEviction() && lineFoundIsHT) {
            this.markMT(set, llcLine.getWay());
            this.removeLRU(set);
        } else if (!requesterIsHT && !lineFoundIsHT) {
            boolean htLLCRequestFound = false;

            for (int way = 0; way < this.htLLCRequestVictimCache.getAssociativity(); way++) {
                if (this.htLLCRequestVictimCache.getLine(set, way).getState() != HTLLCRequestVictimCacheLineState.INVALID) {
                    htLLCRequestFound = true;
                    break;
                }
            }

            if (htLLCRequestFound) {
                this.removeLRU(set);
                this.insertDataEntry(set, llcLine.getTag());
            }
        }
    }

    private void handleRequest(CoherentCacheServiceNonblockingRequestEvent event, boolean requesterIsHT, CacheLine<?> llcLine, int set, boolean lineFoundIsHT) {
        boolean mtHit = event.isHitInCache() && !requesterIsHT && !lineFoundIsHT;
        boolean htHit = event.isHitInCache() && !requesterIsHT && lineFoundIsHT;

        CacheLine<HTLLCRequestVictimCacheLineState> vtLine = this.findHTLLCRequestVictimLine(this.llc.getCache().getTag(event.getAddress()));

        boolean vtHit = !requesterIsHT && vtLine != null;

        if (!mtHit && !htHit && vtHit) {
            this.numBadHTLLCRequests++;
            this.eventDispatcher.dispatch(new BadHTLLCRequestEvent());
            this.setLRU(set, vtLine.getWay());
        } else if (!mtHit && htHit && !vtHit) {
            this.markMT(set, llcLine.getWay());
            this.numGoodHTLLCRequests++;
            this.removeLRU(set);
        } else if (!mtHit && htHit && vtHit) {
            this.markMT(set, llcLine.getWay());
            this.setLRU(set, vtLine.getWay());
            this.removeLRU(set);
        } else if (mtHit && vtHit) {
            this.setLRU(set, vtLine.getWay());
        }
    }

    private void handleReplacement(CoherentCacheLineReplacementEvent event) {
//        pw.printf(
//                "[%d] llc.[%d,%d] {%s} %s: %s {%s} %s 0x%08x: 0x%08x REPLACEMENT\n",
//                llc.getCycleAccurateEventQueue().getCurrentCycle(),
//                event.getLineFound().getSet(),
//                event.getLineFound().getWay(),
//                event.getLineFound().getState(),
//                (event.getLineFound().getState() != event.getLineFound().getInitialState() ? String.format("0x%08x", event.getLineFound().getTag()) : "N/A"),
//                event.getRequesterAccess().getThread().getName(),
//                event.getRequesterAccess().getId(),
//                event.getRequesterAccess().getType(),
//                event.getRequesterAccess().getVirtualPc(),
//                event.getRequesterAccess().getPhysicalTag()
//        );
//        pw.flush();
    }

    private void markInvalid(int set, int way) {
        this.setLLCLineBroughterThreadId(set, way, -1);
    }

    private void markHT(int set, int way) {
        this.setLLCLineBroughterThreadId(set, way, BasicThread.getHelperThreadId());
    }

    private void markMT(int set, int way) {
        this.setLLCLineBroughterThreadId(set, way, BasicThread.getMainThreadId());
    }

    public int getLLCLineBroughterThreadId(int set, int way) {
        return this.llcLineBroughterThreadIds.get(set).get(way);
    }

    private void setLLCLineBroughterThreadId(int set, int way, int llcLineBroughterThreadId) {
//        pw.printf("[%d] llcLineBroughterThreadIds[%d,%d] = %d\n", llc.getCycleAccurateEventQueue().getCurrentCycle(), set, way, llcLineBroughterThreadId);
//        pw.flush();
        this.llcLineBroughterThreadIds.get(set).put(way, llcLineBroughterThreadId);
    }

    private void insertDataEntry(int set, int tag) {
        CacheMiss<HTLLCRequestVictimCacheLineState> newMiss = this.findInvalidLineAndNewMiss(tag, set);
        CacheLine<HTLLCRequestVictimCacheLineState> line = newMiss.getLine();
        HTLLCRequestVictimCacheLineStateValueProvider stateProvider = (HTLLCRequestVictimCacheLineStateValueProvider) line.getStateProvider();
        stateProvider.setState(HTLLCRequestVictimCacheLineState.DATA);
        line.setTag(tag);
//        pw.printf("[%d] htLLCRequestVictimCache[%d,%d]: insertDataEntry(0x%08x)\n", llc.getCycleAccurateEventQueue().getCurrentCycle(), set, line.getWay(), tag);
//        pw.flush();
        newMiss.commit();
    }

    private void insertNullEntry(int set) {
        CacheMiss<HTLLCRequestVictimCacheLineState> newMiss = this.findInvalidLineAndNewMiss(0, set);
        CacheLine<HTLLCRequestVictimCacheLineState> line = newMiss.getLine();
        HTLLCRequestVictimCacheLineStateValueProvider stateProvider = (HTLLCRequestVictimCacheLineStateValueProvider) line.getStateProvider();
        stateProvider.setState(HTLLCRequestVictimCacheLineState.NULL);
        line.setTag(0);
//        pw.printf("[%d] htLLCRequestVictimCache[%d,%d]: insertNullEntry(0x%08x)\n", llc.getCycleAccurateEventQueue().getCurrentCycle(), set, line.getWay(), 0);
//        pw.flush();
        newMiss.commit();
    }

    private void setLRU(int set, int way) {
        this.getLruPolicyForHtRequestVictimCache().setLRU(set, way);
//        pw.printf("[%d] htLLCRequestVictimCache[%d,%d]: setLRU\n", llc.getCycleAccurateEventQueue().getCurrentCycle(), set, way);
//        pw.flush();
    }

    private void removeLRU(int set) {
        LRUPolicy<HTLLCRequestVictimCacheLineState> lru = this.getLruPolicyForHtRequestVictimCache();

        for (int i = this.llc.getCache().getAssociativity() - 1; i >= 0; i--) {
            int way = lru.getWayInStackPosition(set, i);
            CacheLine<HTLLCRequestVictimCacheLineState> line = this.htLLCRequestVictimCache.getLine(set, way);
            if (!line.getState().equals(HTLLCRequestVictimCacheLineState.INVALID)) {
                HTLLCRequestVictimCacheLineStateValueProvider stateProvider = (HTLLCRequestVictimCacheLineStateValueProvider) line.getStateProvider();
                stateProvider.setState(HTLLCRequestVictimCacheLineState.INVALID);
                line.setTag(CacheLine.INVALID_TAG);
//                pw.printf("[%d] htLLCRequestVictimCache[%d,%d]: removeLRU\n", llc.getCycleAccurateEventQueue().getCurrentCycle(), set, way);
//                pw.flush();
                return;
            }
        }

        throw new IllegalArgumentException();
    }

    private LRUPolicy<HTLLCRequestVictimCacheLineState> getLruPolicyForHtRequestVictimCache() {
        return (LRUPolicy<HTLLCRequestVictimCacheLineState>) this.htLLCRequestVictimCache.getEvictionPolicy();
    }

    private CacheMiss<HTLLCRequestVictimCacheLineState> findInvalidLineAndNewMiss(int address, int set) {
        CacheReference reference = new CacheReference(this.llc, null, address, this.htLLCRequestVictimCache.getTag(address), CacheAccessType.UNKNOWN, set);

        for (int way = 0; way < this.htLLCRequestVictimCache.getAssociativity(); way++) {
            CacheLine<HTLLCRequestVictimCacheLineState> line = this.htLLCRequestVictimCache.getLine(set, way);
            if (line.getState() == line.getInitialState()) {
                return new CacheMiss<HTLLCRequestVictimCacheLineState>(this.htLLCRequestVictimCache, reference, way);
            }
        }

        throw new IllegalArgumentException();
    }

    private CacheLine<HTLLCRequestVictimCacheLineState> findHTLLCRequestVictimLine(int tag) {
        return this.htLLCRequestVictimCache.findLine(tag);
    }

    public BlockingEventDispatcher<HTLLCRequestProfilingCapabilityEvent> getEventDispatcher() {
        return eventDispatcher;
    }

    public static enum HTLLCRequestVictimCacheLineState {
        INVALID,
        NULL,
        DATA
    }

    private static class HTLLCRequestVictimCacheLineStateValueProvider implements ValueProvider<HTLLCRequestVictimCacheLineState> {
        private final int set;
        private final int way;
        private HTLLCRequestVictimCacheLineState state;

        public HTLLCRequestVictimCacheLineStateValueProvider(int set, int way) {
            this.set = set;
            this.way = way;
            this.state = HTLLCRequestVictimCacheLineState.INVALID;
        }

        @Override
        public HTLLCRequestVictimCacheLineState get() {
            return state;
        }

        public HTLLCRequestVictimCacheLineState getState() {
            return state;
        }

        public void setState(HTLLCRequestVictimCacheLineState state) {
            this.state = state;
        }

        @Override
        public HTLLCRequestVictimCacheLineState getInitialValue() {
            return HTLLCRequestVictimCacheLineState.INVALID;
        }

        public int getSet() {
            return set;
        }

        public int getWay() {
            return way;
        }
    }

    public abstract class HTLLCRequestProfilingCapabilityEvent implements BlockingEvent {
    }

    public class HTLLCRequestEvent extends HTLLCRequestProfilingCapabilityEvent {
    }

    public class BadHTLLCRequestEvent extends HTLLCRequestProfilingCapabilityEvent {
    }

    public class LateHTLLCRequestEvent extends HTLLCRequestProfilingCapabilityEvent {
    }
}
