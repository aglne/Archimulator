/*******************************************************************************
 * Copyright (c) 2010-2014 by Min Cai (min.cai.china@gmail.com).
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
package archimulator.sim.uncore;

import archimulator.model.Experiment;
import archimulator.model.ExperimentStat;
import archimulator.sim.common.BasicSimulationObject;
import archimulator.sim.common.Simulation;
import archimulator.sim.common.SimulationEvent;
import archimulator.sim.uncore.cache.CacheLine;
import archimulator.sim.uncore.coherence.msi.controller.*;
import archimulator.sim.uncore.coherence.msi.message.CoherenceMessage;
import archimulator.sim.uncore.dram.BasicMemoryController;
import archimulator.sim.uncore.dram.FixedLatencyMemoryController;
import archimulator.sim.uncore.dram.MemoryController;
import archimulator.sim.uncore.dram.SimpleMemoryController;
import archimulator.sim.uncore.net.L1sToL2Net;
import archimulator.sim.uncore.net.L2ToMemNet;
import archimulator.sim.uncore.net.Net;
import archimulator.sim.uncore.tlb.TranslationLookasideBuffer;
import net.pickapack.event.BlockingEventDispatcher;
import net.pickapack.event.CycleAccurateEventQueue;
import net.pickapack.fsm.BasicFiniteStateMachine;

import java.io.Serializable;
import java.util.*;

/**
 * Basic memory hierarchy.
 *
 * @author Min Cai
 */
public class BasicMemoryHierarchy extends BasicSimulationObject implements MemoryHierarchy {
    private MemoryController memoryController;
    private DirectoryController l2Controller;
    private List<CacheController> l1IControllers;
    private List<CacheController> l1DControllers;

    private List<TranslationLookasideBuffer> itlbs;
    private List<TranslationLookasideBuffer> dtlbs;

    private L1sToL2Net l1sToL2Net;
    private L2ToMemNet l2ToMemNet;

    private Map<Controller, Map<Controller, PointToPointReorderBuffer>> p2pReorderBuffers;

    /**
     * Create a basic memory hierarchy.
     *
     * @param experiment              the experiment
     * @param simulation              the simulation
     * @param blockingEventDispatcher the blocking event dispatcher
     * @param cycleAccurateEventQueue the cycle accurate event queue
     */
    public BasicMemoryHierarchy(Experiment experiment, Simulation simulation, BlockingEventDispatcher<SimulationEvent> blockingEventDispatcher, CycleAccurateEventQueue cycleAccurateEventQueue) {
        super(experiment, simulation, blockingEventDispatcher, cycleAccurateEventQueue);

        switch (getExperiment().getMemoryControllerType()) {
            case SIMPLE:
                this.memoryController = new SimpleMemoryController(this);
                break;
            case BASIC:
                this.memoryController = new BasicMemoryController(this);
                break;
            default:
                this.memoryController = new FixedLatencyMemoryController(this);
                break;
        }

        this.l2Controller = new DirectoryController(this, "l2");
        this.l2Controller.setNext(this.memoryController);

        this.l1IControllers = new ArrayList<>();
        this.l1DControllers = new ArrayList<>();

        this.itlbs = new ArrayList<>();
        this.dtlbs = new ArrayList<>();

        for (int i = 0; i < getExperiment().getNumCores(); i++) {
            CacheController l1IController = new L1IController(this, "c" + i + "/icache");
            l1IController.setNext(this.l2Controller);
            this.l1IControllers.add(l1IController);

            CacheController l1DController = new L1DController(this, "c" + i + "/dcache");
            l1DController.setNext(this.l2Controller);
            this.l1DControllers.add(l1DController);

            for (int j = 0; j < getExperiment().getNumThreadsPerCore(); j++) {
                TranslationLookasideBuffer itlb = new TranslationLookasideBuffer(this, "c" + i + "t" + j + "/itlb");
                this.itlbs.add(itlb);

                TranslationLookasideBuffer dtlb = new TranslationLookasideBuffer(this, "c" + i + "t" + j + "/dtlb");
                this.dtlbs.add(dtlb);
            }
        }

        this.l1sToL2Net = new L1sToL2Net(this);
        this.l2ToMemNet = new L2ToMemNet(this);

        this.p2pReorderBuffers = new HashMap<>();
    }

    /**
     * Dump the cache controller finite state machine statistics.
     *
     * @param stats the list of statistics to be manipulated
     */
    @Override
    public void dumpCacheControllerFsmStats(List<ExperimentStat> stats) {
        for (CacheController l1IController : this.l1IControllers) {
            dumpCacheControllerFsmStats(stats, l1IController);
        }

        for (CacheController l1DController : this.l1DControllers) {
            dumpCacheControllerFsmStats(stats, l1DController);
        }
        dumpCacheControllerFsmStats(stats, this.l2Controller);
    }

    /**
     * Dump the statistics for the specified general cache controller.
     *
     * @param stats           the list of statistics to be manipulated
     * @param cacheController the general cache controller
     * @param <StateT>        state
     * @param <ConditionT>    transition
     */
    @SuppressWarnings("unchecked")
    private <StateT extends Serializable, ConditionT> void dumpCacheControllerFsmStats(List<ExperimentStat> stats, GeneralCacheController<StateT, ConditionT> cacheController) {
        List<BasicFiniteStateMachine<StateT, ConditionT>> finiteStateMachines = new ArrayList<>();

        for (int set = 0; set < cacheController.getCache().getNumSets(); set++) {
            for (CacheLine<StateT> line : cacheController.getCache().getLines(set)) {
                BasicFiniteStateMachine<StateT, ConditionT> fsm = (BasicFiniteStateMachine<StateT, ConditionT>) line.getStateProvider();
                finiteStateMachines.add(fsm);
            }
        }

        Map<String, String> statsMap = new LinkedHashMap<>();

        cacheController.getFsmFactory().dump(PREFIX_CC_FSM + cacheController.getName(), finiteStateMachines, statsMap);

        for (Map.Entry<String, String> entry : statsMap.entrySet()) {
            stats.add(new ExperimentStat(getSimulation().getPrefix(), entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Transfer a message of the specified size from the source device to the destination device.
     *
     * @param from    the source device
     * @param to      the destination device
     * @param size    the size of the message to be transferred
     * @param message the message to be transferred
     */
    @Override
    public void transfer(final Controller from, final Controller to, int size, final CoherenceMessage message) {
        if (!this.p2pReorderBuffers.containsKey(from)) {
            this.p2pReorderBuffers.put(from, new HashMap<>());
        }

        if (!this.p2pReorderBuffers.get(from).containsKey(to)) {
            this.p2pReorderBuffers.get(from).put(to, new PointToPointReorderBuffer(from, to));
        }

        this.p2pReorderBuffers.get(from).get(to).transfer(message);

        from.getNet(to).transfer(from, to, size, () -> p2pReorderBuffers.get(from).get(to).onDestinationArrived(message));
    }

    /**
     * Get the memory controller.
     *
     * @return the memory controller
     */
    public MemoryController getMemoryController() {
        return memoryController;
    }

    /**
     * Get the L2 cache controller.
     *
     * @return the L2 cache controller
     */
    public DirectoryController getL2Controller() {
        return l2Controller;
    }

    /**
     * Get the list of L1I cache controllers.
     *
     * @return the list of L1I cache controllers
     */
    public List<CacheController> getL1IControllers() {
        return l1IControllers;
    }

    /**
     * Get the list of L1D cache controllers.
     *
     * @return the list of L1D cache controllers
     */
    public List<CacheController> getL1DControllers() {
        return l1DControllers;
    }

    /**
     * Get the list of instruction translation lookaside buffers (iTLBs).
     *
     * @return the list of instruction translation lookaside buffers (iTLBs)
     */
    public List<TranslationLookasideBuffer> getItlbs() {
        return itlbs;
    }

    /**
     * Get the list of data translation lookaside buffers (dTLBs).
     *
     * @return the list of data translation lookaside buffers (dTLBs)
     */
    public List<TranslationLookasideBuffer> getDtlbs() {
        return dtlbs;
    }

    /**
     * Get the net for the L1 cache controllers to the L2 cache controller.
     *
     * @return the net for the L1 cache controllers to the L2 cache controller.
     */
    public Net getL1sToL2Net() {
        return l1sToL2Net;
    }

    /**
     * Get the net for the L2 cache controller to the memory controller.
     *
     * @return the net for the L2 cache controller to the memory controller.
     */
    public L2ToMemNet getL2ToMemNet() {
        return l2ToMemNet;
    }

    /**
     * Get the name of the memory hierarchy.
     *
     * @return the name of the memory hierarchy
     */
    @Override
    public String getName() {
        return "memoryHierarchy";
    }

    /**
     * Statistics prefix for cache coherence finite state machine stuff.
     */
    public static final String PREFIX_CC_FSM = "ccFsm/";
}
