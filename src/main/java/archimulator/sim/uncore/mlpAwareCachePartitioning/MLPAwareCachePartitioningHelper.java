/*******************************************************************************
 * Copyright (c) 2010-2013 by Min Cai (min.cai.china@gmail.com).
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
package archimulator.sim.uncore.mlpAwareCachePartitioning;

import archimulator.sim.common.Simulation;
import archimulator.sim.core.event.InstructionCommittedEvent;
import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.coherence.event.GeneralCacheControllerServiceNonblockingRequestEvent;
import archimulator.sim.uncore.coherence.event.LastLevelCacheControllerLineInsertEvent;
import archimulator.sim.uncore.coherence.msi.controller.DirectoryController;
import archimulator.sim.uncore.mlp.PendingL2Hit;
import archimulator.sim.uncore.mlp.PendingL2Miss;
import net.pickapack.Pair;
import net.pickapack.action.Action;
import net.pickapack.action.Action1;
import net.pickapack.action.Function1;
import org.paukov.combinatorics.Factory;
import org.paukov.combinatorics.Generator;
import org.paukov.combinatorics.ICombinatoricsVector;

import java.util.*;

/**
 * Memory level parallelism (MLP) aware cache partitioning helper.
 *
 * @author Min Cai
 */
public class MLPAwareCachePartitioningHelper {
    private DirectoryController l2CacheController;

    private Map<Integer, PendingL2Miss> pendingL2Misses;
    private Map<Integer, Map<Integer, PendingL2Hit>> pendingL2Hits;

    private L2CacheAccessMLPCostProfile l2CacheAccessMLPCostProfile;

    private int numCyclesElapsedPerInterval;

    private long numIntervals;
    private int numCyclesElapsed;

    private List<Integer> partition;

    private MemoryLatencyMeter memoryLatencyMeter;

    private Map<Integer, MLPAwareStackDistanceProfile> mlpAwareStackDistanceProfiles;
    private int numThreads;

    private Map<Integer, Map<Integer, LRUStack>> lruStacks;

    private Function1<Double, Integer> mlpCostQuantizer;
    private List<List<Integer>> partitions;

    /**
     * Create an MLP aware cache partitioning helper.
     *
     * @param simulation the simulation
     */
    public MLPAwareCachePartitioningHelper(Simulation simulation) {
        this(simulation.getProcessor().getMemoryHierarchy().getL2CacheController());
    }

    /**
     * Create an MLP aware cache partitioning helper.
     *
     * @param l2CacheController the L2 cache controller
     */
    public MLPAwareCachePartitioningHelper(final DirectoryController l2CacheController) {
        this.l2CacheController = l2CacheController;

        this.pendingL2Misses = new LinkedHashMap<Integer, PendingL2Miss>();
        this.pendingL2Hits = new LinkedHashMap<Integer, Map<Integer, PendingL2Hit>>();

        int l2Associativity = this.l2CacheController.getCache().getAssociativity();

        this.l2CacheAccessMLPCostProfile = new L2CacheAccessMLPCostProfile(l2Associativity);

        this.numThreads = this.l2CacheController.getExperiment().getArchitecture().getNumThreadsPerCore() * this.l2CacheController.getExperiment().getArchitecture().getNumCores();

        this.partition = new ArrayList<Integer>();

        this.memoryLatencyMeter = new MemoryLatencyMeter();

        this.mlpAwareStackDistanceProfiles = new LinkedHashMap<Integer, MLPAwareStackDistanceProfile>();

        this.lruStacks = new LinkedHashMap<Integer, Map<Integer, LRUStack>>();

        this.mlpCostQuantizer = new Function1<Double, Integer>() {
            @Override
            public Integer apply(Double rawValue) {
                if (rawValue < 0) {
                    throw new IllegalArgumentException();
                }

                if (rawValue <= 42) {
                    return 0;
                } else if (rawValue <= 85) {
                    return 1;
                } else if (rawValue <= 128) {
                    return 2;
                } else if (rawValue <= 170) {
                    return 3;
                } else if (rawValue <= 213) {
                    return 4;
                } else if (rawValue <= 246) {
                    return 5;
                } else if (rawValue <= 300) {
                    return 6;
                } else {
                    return 7;
                }
            }
        };

        if (l2Associativity < this.numThreads) {
            throw new IllegalArgumentException();
        }

        for (int i = 0; i < this.numThreads; i++) {
            this.partition.add(l2Associativity / this.numThreads);
        }

        this.numCyclesElapsedPerInterval = 5000000;

        l2CacheController.getBlockingEventDispatcher().addListener(GeneralCacheControllerServiceNonblockingRequestEvent.class, new Action1<GeneralCacheControllerServiceNonblockingRequestEvent>() {
            public void apply(GeneralCacheControllerServiceNonblockingRequestEvent event) {
                if (event.getCacheController().equals(MLPAwareCachePartitioningHelper.this.l2CacheController)) {
                    if (!event.isHitInCache()) {
                        profileBeginServicingL2CacheMiss(event.getAccess());
                    } else {
                        profileBeginServicingL2CacheHit(event.getAccess());
                    }
                }
            }
        });

        l2CacheController.getBlockingEventDispatcher().addListener(LastLevelCacheControllerLineInsertEvent.class, new Action1<LastLevelCacheControllerLineInsertEvent>() {
            @Override
            public void apply(LastLevelCacheControllerLineInsertEvent event) {
                if (event.getCacheController().equals(MLPAwareCachePartitioningHelper.this.l2CacheController)) {
                    profileEndServicingL2CacheMiss(event.getAccess());
                }
            }
        });

        l2CacheController.getCycleAccurateEventQueue().getPerCycleEvents().add(new Action() {
            @Override
            public void apply() {
                updateL2CacheAccessMlpCostsPerCycle();
                updateL2CacheHitElapsedCyclesPerCycle();
                freeInvalidL2CacheHitsPerCycle();

                numCyclesElapsed++;

                if (numCyclesElapsed == numCyclesElapsedPerInterval) {
                    newInterval();

                    numCyclesElapsed = 0;
                    numIntervals++;
                }

            }
        });

        l2CacheController.getBlockingEventDispatcher().addListener(InstructionCommittedEvent.class, new Action1<InstructionCommittedEvent>() {
            @Override
            public void apply(InstructionCommittedEvent event) {
                if (pendingL2Hits.containsKey(event.getDynamicInstruction().getThread().getId())) {
                    for (PendingL2Hit pendingL2Hit : pendingL2Hits.get(event.getDynamicInstruction().getThread().getId()).values()) {
                        pendingL2Hit.incrementNumCommittedInstructionsSinceAccess();
                    }
                }
            }
        });
    }

    /**
     * New interval.
     */
    private void newInterval() {
        this.partition = getOptimalMlpCostSumAndPartition().getSecond();
    }

    /**
     * To be invoked per cycle for updating MLP-costs for in-flight L2 cache accesses.
     */
    private void updateL2CacheAccessMlpCostsPerCycle() {
        for (PendingL2Miss pendingL2Miss : this.pendingL2Misses.values()) {
            pendingL2Miss.setMlpCost(pendingL2Miss.getMlpCost() + (double) 1 / this.l2CacheAccessMLPCostProfile.getN(pendingL2Miss.getStackDistance()));
        }

        for (Map<Integer, PendingL2Hit> pendingL2HitsPerThread : this.pendingL2Hits.values()) {
            for (PendingL2Hit pendingL2Hit : pendingL2HitsPerThread.values()) {
                pendingL2Hit.setMlpCost(pendingL2Hit.getMlpCost() + (double) 1 / this.l2CacheAccessMLPCostProfile.getN(pendingL2Hit.getStackDistance()));
            }
        }
    }

    /**
     * To be invoked per cycle for updating elapsed cycles for in-flight L2 cache hits.
     */
    private void updateL2CacheHitElapsedCyclesPerCycle() {
        for (Map<Integer, PendingL2Hit> pendingL2HitsPerThread : this.pendingL2Hits.values()) {
            for (PendingL2Hit pendingL2Hit : pendingL2HitsPerThread.values()) {
                pendingL2Hit.incrementNumCyclesElapsedSinceAccess();
            }
        }
    }

    /**
     * To be invoked per cycle for freeing invalid in-flight L2 cache hits.
     */
    private void freeInvalidL2CacheHitsPerCycle() {
        for (Map<Integer, PendingL2Hit> pendingL2HitsPerThread : this.pendingL2Hits.values()) {
            List<Integer> tagsToFree = new ArrayList<Integer>();

            for (PendingL2Hit pendingL2Hit : pendingL2HitsPerThread.values()) {
                if (pendingL2Hit.getNumCommittedInstructionsSinceAccess() >= this.l2CacheController.getExperiment().getArchitecture().getReorderBufferCapacity()
                        || pendingL2Hit.getNumCyclesElapsedSinceAccess() >= memoryLatencyMeter.getAverageLatency()) {
                    tagsToFree.add(pendingL2Hit.getAccess().getPhysicalTag());
                }
            }

            for (int tag : tagsToFree) {
                profileEndServicingL2CacheHit(pendingL2HitsPerThread.get(tag).getAccess());
            }
        }
    }

    /**
     * Profile the beginning of servicing an L2 cache miss.
     *
     * @param access the memory hierarchy access
     */
    private void profileBeginServicingL2CacheMiss(MemoryHierarchyAccess access) {
        int tag = access.getPhysicalTag();
        int set = this.l2CacheController.getCache().getSet(tag);

        LRUStack lruStack = getLruStack(access.getThread().getId(), set);

        final int stackDistance = lruStack.access(tag);

        PendingL2Miss pendingL2Miss = new PendingL2Miss(access, l2CacheController.getCycleAccurateEventQueue().getCurrentCycle()) {
            {
                setStackDistance(stackDistance);
            }
        };
        this.pendingL2Misses.put(tag, pendingL2Miss);

        this.l2CacheAccessMLPCostProfile.incrementCounter(stackDistance);
    }

    /**
     * Profile the end of servicing an L2 cache miss.
     *
     * @param access the memory hierarchy access
     */
    private void profileEndServicingL2CacheMiss(MemoryHierarchyAccess access) {
        int tag = access.getPhysicalTag();

        PendingL2Miss pendingL2Miss = this.pendingL2Misses.get(tag);
        pendingL2Miss.setEndCycle(this.l2CacheController.getCycleAccurateEventQueue().getCurrentCycle());

        this.l2CacheAccessMLPCostProfile.decrementCounter(pendingL2Miss.getStackDistance());

        this.pendingL2Misses.remove(tag);

        this.memoryLatencyMeter.newSample(pendingL2Miss.getNumCycles());

        MLPAwareStackDistanceProfile mlpAwareStackDistanceProfile = this.getMlpAwareStackDistanceProfile(access.getThread().getId());

        if (pendingL2Miss.getStackDistance() == -1) {
            mlpAwareStackDistanceProfile.incrementMissCounter(this.mlpCostQuantizer.apply(pendingL2Miss.getMlpCost()));
        } else {
            mlpAwareStackDistanceProfile.incrementHitCounter(pendingL2Miss.getStackDistance(), this.mlpCostQuantizer.apply(pendingL2Miss.getMlpCost()));
        }
    }

    /**
     * Profile the beginning of servicing an L2 cache hit.
     *
     * @param access the memory hierarchy access
     */
    private void profileBeginServicingL2CacheHit(MemoryHierarchyAccess access) {
        int tag = access.getPhysicalTag();
        int set = this.l2CacheController.getCache().getSet(tag);

        LRUStack lruStack = getLruStack(access.getThread().getId(), set);

        final int stackDistance = lruStack.access(tag);

        PendingL2Hit pendingL2Hit = new PendingL2Hit(access, l2CacheController.getCycleAccurateEventQueue().getCurrentCycle()) {
            {
                setStackDistance(stackDistance);
            }
        };

        if (!this.pendingL2Hits.containsKey(access.getThread().getId())) {
            this.pendingL2Hits.put(access.getThread().getId(), new LinkedHashMap<Integer, PendingL2Hit>());
        }

        this.pendingL2Hits.get(access.getThread().getId()).put(tag, pendingL2Hit);

        this.l2CacheAccessMLPCostProfile.incrementCounter(stackDistance);
    }

    /**
     * Profile the end of servicing an L2 cache hit.
     *
     * @param access the memory hierarchy access
     */
    private void profileEndServicingL2CacheHit(MemoryHierarchyAccess access) {
        int tag = access.getPhysicalTag();

        PendingL2Hit pendingL2Hit = this.pendingL2Hits.get(access.getThread().getId()).get(tag);
        pendingL2Hit.setEndCycle(this.l2CacheController.getCycleAccurateEventQueue().getCurrentCycle());

        this.l2CacheAccessMLPCostProfile.decrementCounter(pendingL2Hit.getStackDistance());

        this.pendingL2Hits.get(access.getThread().getId()).remove(tag);

        MLPAwareStackDistanceProfile mlpAwareStackDistanceProfile = this.getMlpAwareStackDistanceProfile(access.getThread().getId());

        if (pendingL2Hit.getStackDistance() == -1) {
            mlpAwareStackDistanceProfile.incrementMissCounter(this.mlpCostQuantizer.apply(pendingL2Hit.getMlpCost()));
        } else {
            mlpAwareStackDistanceProfile.incrementHitCounter(pendingL2Hit.getStackDistance(), this.mlpCostQuantizer.apply(pendingL2Hit.getMlpCost()));
        }
    }

    /**
     * Get the LRU stack for the specified thread ID and set index in the L2 cache.
     *
     * @param threadId the thread ID
     * @param set      the set index
     * @return the LRU stack for the specified thread ID and set index in the L2 cache
     */
    private LRUStack getLruStack(int threadId, int set) {
        if (!this.lruStacks.containsKey(threadId)) {
            this.lruStacks.put(threadId, new LinkedHashMap<Integer, LRUStack>());
        }

        if (!this.lruStacks.get(threadId).containsKey(set)) {
            this.lruStacks.get(threadId).put(set, new LRUStack(threadId, set, this.l2CacheController.getCache().getAssociativity()));
        }

        return this.lruStacks.get(threadId).get(set);
    }

    /**
     * Get the total MLP-cost for the specified thread ID and associativity.
     *
     * @param threadId      the thread ID
     * @param associativity the associativity
     * @return the total MLP-cost for the specified thread ID and associativity
     */
    public int getTotalMlpCost(int threadId, int associativity) {
        if (associativity > this.l2CacheController.getCache().getAssociativity()) {
            throw new IllegalArgumentException();
        }

        MLPAwareStackDistanceProfile mlpAwareStackDistanceProfile = getMlpAwareStackDistanceProfile(threadId);

        int totalMlpCost = 0;

        for (int i = associativity - 1; i < this.l2CacheController.getCache().getAssociativity(); i++) {
            totalMlpCost += mlpAwareStackDistanceProfile.getHitCounters().get(i);
        }

        totalMlpCost += mlpAwareStackDistanceProfile.getMissCounter();

        return totalMlpCost;
    }

    /**
     * Get the MLP-aware stack distance profile for the specified thread ID.
     *
     * @param threadId the thread ID
     * @return the MLP-aware stack distance profile for the specified thread ID
     */
    private MLPAwareStackDistanceProfile getMlpAwareStackDistanceProfile(int threadId) {
        if (!this.mlpAwareStackDistanceProfiles.containsKey(threadId)) {
            this.mlpAwareStackDistanceProfiles.put(threadId, new MLPAwareStackDistanceProfile(this.l2CacheController.getCache().getAssociativity()));
        }

        return this.mlpAwareStackDistanceProfiles.get(threadId);
    }

    /**
     * Get the minimal sum of MLP-cost and its associated optimal partition.
     *
     * @return the minimal sum of MLP-cost and its associated optimal partition
     */
    private Pair<Integer, List<Integer>> getOptimalMlpCostSumAndPartition() {
        if (partitions == null) {
            partitions = partition(this.l2CacheController.getCache().getAssociativity(), this.numThreads);
        }

        int minMlpCostSum = Integer.MAX_VALUE;
        List<Integer> minPartition = null;

        for (List<Integer> partition : partitions) {
            int sum = 0;

            for (int i = 0; i < partition.size(); i++) {
                sum += getTotalMlpCost(i, partition.get(i));
            }

            if (sum < minMlpCostSum) {
                minMlpCostSum = sum;
                minPartition = partition;
            }
        }

        return new Pair<Integer, List<Integer>>(minMlpCostSum, minPartition);
    }

    /**
     * Get the partition.
     *
     * @return the partition
     */
    public List<Integer> getPartition() {
        return partition;
    }

    /**
     * Get the measuring interval in cycles.
     *
     * @return the measuring interval in cycles
     */
    public int getNumCyclesElapsedPerInterval() {
        return numCyclesElapsedPerInterval;
    }

    /**
     * Get the number of intervals.
     *
     * @return the number of intervals
     */
    public long getNumIntervals() {
        return numIntervals;
    }

    /**
     * Get the L2 cache access MLP-cost profile.
     *
     * @return the L2 cache access MLP-cost profile
     */
    public L2CacheAccessMLPCostProfile getL2CacheAccessMLPCostProfile() {
        return l2CacheAccessMLPCostProfile;
    }

    /**
     * Get the memory latency meter.
     *
     * @return the memory latency meter
     */
    public MemoryLatencyMeter getMemoryLatencyMeter() {
        return memoryLatencyMeter;
    }

    /**
     * Get the map of MLP aware stack distance profiles.
     *
     * @return the map of MLP aware stack distance profiles
     */
    public Map<Integer, MLPAwareStackDistanceProfile> getMlpAwareStackDistanceProfiles() {
        return mlpAwareStackDistanceProfiles;
    }

    /**
     * Get the map of LRU stacks.
     *
     * @return the map of LRU stacks
     */
    public Map<Integer, Map<Integer, LRUStack>> getLruStacks() {
        return lruStacks;
    }

    /**
     * Divide the integer n into k partitions.
     *
     * @param n the integer n to be partitioned
     * @param k the number of partitions
     * @return the partition list of the specified integer n
     */
    public static List<List<Integer>> partition(int n, int k) {
        List<List<Integer>> result = new ArrayList<List<Integer>>();

        Generator<Integer> generator = Factory.createCompositionGenerator(n);

        for (ICombinatoricsVector<Integer> vector : generator) {
            if (vector.getSize() == k) {
                result.add(vector.getVector());
            }
        }

        return result;
    }
}
