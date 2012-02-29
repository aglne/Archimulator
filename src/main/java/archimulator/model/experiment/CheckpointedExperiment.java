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
package archimulator.model.experiment;

import archimulator.model.capability.ProcessorCapability;
import archimulator.model.capability.ProcessorCapabilityFactory;
import archimulator.model.capability.SimulationCapability;
import archimulator.model.capability.SimulationCapabilityFactory;
import archimulator.model.simulation.ContextConfig;
import archimulator.model.simulation.SimulationStartingImage;
import archimulator.model.strategy.checkpoint.CheckpointToInstructionCountBasedDetailedSimulationStrategy;
import archimulator.model.strategy.checkpoint.RoiBasedRunToCheckpointFunctionalSimulationStrategy;
import archimulator.sim.os.KernelCapability;
import archimulator.sim.os.KernelCapabilityFactory;
import archimulator.sim.uncore.cache.eviction.EvictionPolicyFactory;

import java.util.List;
import java.util.Map;

public class CheckpointedExperiment extends Experiment {
    private int maxInsts;
    private int pthreadSpawnedIndex;

    public CheckpointedExperiment(String title, int numCores, int numThreadsPerCore, List<ContextConfig> contextConfigs, int maxInsts, int l2Size, int l2Associativity, EvictionPolicyFactory l2EvictionPolicyFactory, int pthreadSpawnedIndex, Map<Class<? extends SimulationCapability>, SimulationCapabilityFactory> simulationCapabilityFactories, Map<Class<? extends ProcessorCapability>, ProcessorCapabilityFactory> processorCapabilityFactories, Map<Class<? extends KernelCapability>, KernelCapabilityFactory> kernelCapabilityFactories) {
        super(title, numCores, numThreadsPerCore, contextConfigs, l2Size, l2Associativity, l2EvictionPolicyFactory, simulationCapabilityFactories, processorCapabilityFactories, kernelCapabilityFactories);
        this.maxInsts = maxInsts;
        this.pthreadSpawnedIndex = pthreadSpawnedIndex;
    }

    @Override
    protected void doStart() {
        SimulationStartingImage simulationStartingImage = new SimulationStartingImage();

        this.doSimulation(this.getTitle() + "/checkpointedSimulation/phase0", new RoiBasedRunToCheckpointFunctionalSimulationStrategy(this.getPhaser(), this.pthreadSpawnedIndex, simulationStartingImage), getBlockingEventDispatcher(), getCycleAccurateEventQueue());

        getBlockingEventDispatcher().clearListeners();
        getCycleAccurateEventQueue().resetCurrentCycle();

        this.doSimulation(this.getTitle() + "/checkpointedSimulation/phase1", new CheckpointToInstructionCountBasedDetailedSimulationStrategy(this.getPhaser(), this.maxInsts, simulationStartingImage), getBlockingEventDispatcher(), getCycleAccurateEventQueue());
    }
}
