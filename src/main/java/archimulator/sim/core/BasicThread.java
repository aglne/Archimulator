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
package archimulator.sim.core;

import archimulator.model.ContextMapping;
import archimulator.sim.core.bpred.BranchPredictorUpdate;
import archimulator.sim.core.bpred.DynamicBranchPredictor;
import archimulator.sim.core.event.InstructionCommittedEvent;
import archimulator.sim.core.event.InstructionDecodedEvent;
import archimulator.sim.isa.PseudoCallEncounteredEvent;
import archimulator.sim.isa.RegisterDependencyType;
import archimulator.sim.isa.StaticInstruction;
import archimulator.sim.isa.StaticInstructionType;
import archimulator.sim.os.ContextState;
import archimulator.sim.uncore.MemoryHierarchyThread;
import net.pickapack.Reference;
import net.pickapack.action.Action;
import net.pickapack.action.Action1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BasicThread extends AbstractBasicThread {
    private int lineSizeOfICache;

    private int fetchNpc;
    private int fetchNnpc;
    private boolean fetchStalled;
    private int lastFetchedCacheLine;

    private DynamicInstruction lastDecodedDynamicInstruction;
    private boolean lastDecodedDynamicInstructionCommitted;

    private long lastCommitCycle;
    private int noInstructionCommittedCounterThreshold;

    private DynamicInstruction nextInstructionInCacheWarmupPhase;

    public BasicThread(Core core, int num) {
        super(core, num);

        this.lineSizeOfICache = this.core.getL1ICacheController().getCache().getGeometry().getLineSize();

        final Reference<Integer> savedRegisterValue = new Reference<Integer>(-1);

        this.getBlockingEventDispatcher().addListener(PseudoCallEncounteredEvent.class, new Action1<PseudoCallEncounteredEvent>() {
            public void apply(PseudoCallEncounteredEvent event) {
                if (event.getContext() == getContext()) {
                    ContextMapping contextMapping = event.getContext().getProcess().getContextMapping();

                    if (contextMapping.getSimulatedProgram().getHelperThreadEnabled()) {
                        if (event.getImm() == 3820) {
                            savedRegisterValue.set(event.getContext().getRegisterFile().getGpr(event.getRs()));
                            event.getContext().getRegisterFile().setGpr(event.getRs(), getHelperThreadLookahead(contextMapping));
                        } else if (event.getImm() == 3821) {
                            event.getContext().getRegisterFile().setGpr(event.getRs(), savedRegisterValue.get());
                        } else if (event.getImm() == 3822) {
                            savedRegisterValue.set(event.getContext().getRegisterFile().getGpr(event.getRs()));
                            event.getContext().getRegisterFile().setGpr(event.getRs(), getHelperThreadStride(contextMapping));
                        } else if (event.getImm() == 3823) {
                            event.getContext().getRegisterFile().setGpr(event.getRs(), savedRegisterValue.get());
                        }
                    }
                }
            }
        });
    }

    protected int getHelperThreadLookahead(ContextMapping contextMapping) {
        if (contextMapping.getSimulatedProgram().getHelperThreadEnabled()) {
            return contextMapping.getDynamicHelperThreadParams() ? 20 : contextMapping.getHelperThreadLookahead();
        }

        throw new IllegalArgumentException();
    }

    protected int getHelperThreadStride(ContextMapping contextMapping) {
        if (contextMapping.getSimulatedProgram().getHelperThreadEnabled()) {
            return contextMapping.getDynamicHelperThreadParams() ? 10 : contextMapping.getHelperThreadStride();
        }

        throw new IllegalArgumentException();
    }

    public void fastForwardOneCycle() {
        if (this.context != null && this.context.getState() == ContextState.RUNNING) {
            StaticInstruction staticInstruction;
            do {
                staticInstruction = this.context.decodeNextInstruction();
                StaticInstruction.execute(staticInstruction, this.context);

                if (!this.context.isPseudoCallEncounteredInLastInstructionExecution() && staticInstruction.getMnemonic().getType() != StaticInstructionType.NOP) {
                    this.totalInstructions++;
                }

            }
            while (this.context != null && this.context.getState() == ContextState.RUNNING && (this.context.isPseudoCallEncounteredInLastInstructionExecution() || staticInstruction.getMnemonic().getType() == StaticInstructionType.NOP));
        }
    }

    public void warmupCacheOneCycle() {
        if (this.context != null && this.context.getState() == ContextState.RUNNING && !this.fetchStalled) {
            if (this.nextInstructionInCacheWarmupPhase == null) {
                StaticInstruction staticInstruction;
                do {
                    staticInstruction = this.context.decodeNextInstruction();
                    this.nextInstructionInCacheWarmupPhase = new DynamicInstruction(this, this.context.getRegisterFile().getPc(), staticInstruction);
                    StaticInstruction.execute(staticInstruction, this.context);

                    if (!this.context.isPseudoCallEncounteredInLastInstructionExecution() && staticInstruction.getMnemonic().getType() != StaticInstructionType.NOP) {
                        this.totalInstructions++;
                    }
                }
                while (this.context != null && this.context.getState() == ContextState.RUNNING && !this.fetchStalled && (this.context.isPseudoCallEncounteredInLastInstructionExecution() || staticInstruction.getMnemonic().getType() == StaticInstructionType.NOP));
            }

            int pc = this.nextInstructionInCacheWarmupPhase.getPc();

            int cacheLineToFetch = aligned(pc, this.lineSizeOfICache);
            if (cacheLineToFetch != this.lastFetchedCacheLine) {
                if (this.core.canIfetch(this, pc)) {
                    this.core.ifetch(this, pc, pc, new Action() {
                        public void apply() {
                            fetchStalled = false;
                        }
                    });

                    this.fetchStalled = true;
                    this.lastFetchedCacheLine = cacheLineToFetch;
                } else {
                    return;
                }
            }

            int effectiveAddress = this.nextInstructionInCacheWarmupPhase.getEffectiveAddress();

            if (this.nextInstructionInCacheWarmupPhase.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.LOAD) {
                if (this.core.canLoad(this, effectiveAddress)) {
                    this.core.load(this.nextInstructionInCacheWarmupPhase, effectiveAddress, pc, new Action() {
                        public void apply() {
                        }
                    });

                    this.nextInstructionInCacheWarmupPhase = null;
                }
            } else if (this.nextInstructionInCacheWarmupPhase.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.STORE) {
                if (this.core.canStore(this, effectiveAddress)) {
                    this.core.store(this.nextInstructionInCacheWarmupPhase, effectiveAddress, pc, new Action() {
                        public void apply() {
                        }
                    });

                    this.nextInstructionInCacheWarmupPhase = null;
                }
            } else {
                this.nextInstructionInCacheWarmupPhase = null;
            }
        }
    }

    public void updateFetchNpcAndNnpcFromRegs() {
        this.fetchNpc = this.context.getRegisterFile().getNpc();
        this.fetchNnpc = this.context.getRegisterFile().getNnpc();

        this.lastCommitCycle = this.getCycleAccurateEventQueue().getCurrentCycle();
    }

    private boolean canFetch() {
        if (!this.context.useICache()) {
            this.lastFetchedCacheLine = aligned(this.fetchNpc, this.lineSizeOfICache);
            return true;
        } else {
            if (this.fetchStalled) {
                return false;
            }

            int cacheLineToFetch = aligned(this.fetchNpc, this.lineSizeOfICache);
            if (cacheLineToFetch != this.lastFetchedCacheLine) {
                if (!this.core.canIfetch(this, this.fetchNpc)) {
                    return false;
                } else {
                    this.core.ifetch(this, this.fetchNpc, this.fetchNpc, new Action() {
                        public void apply() {
                            fetchStalled = false;
                        }
                    });

                    this.fetchStalled = true;
                    this.lastFetchedCacheLine = cacheLineToFetch;

                    return false;
                }
            }

            return true;
        }
    }

    public void fetch() {
        if (!this.canFetch()) {
            return;
        }

        boolean hasDone = false;

        while (!hasDone) {
            if (this.context.getState() != ContextState.RUNNING) {
                break;
            }

            if (this.decodeBuffer.isFull()) {
                this.fetchStallsOnDecodeBufferIsFull++;
                break;
            }

            if (this.context.getRegisterFile().getNpc() != this.fetchNpc) {
                if (this.context.isSpeculative()) {
                    this.context.getRegisterFile().setNpc(this.fetchNpc);
                } else {
                    this.context.enterSpeculativeState();
                }
            }

            DynamicInstruction dynamicInstruction;

            do {
                StaticInstruction staticInstruction = this.context.decodeNextInstruction();
                dynamicInstruction = new DynamicInstruction(this, this.context.getRegisterFile().getPc(), staticInstruction);
                StaticInstruction.execute(staticInstruction, this.context);

                if (this.context.isPseudoCallEncounteredInLastInstructionExecution() || dynamicInstruction.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.NOP) {
                    this.updateFetchNpcAndNnpcFromRegs();
                }

            }
            while (this.context.isPseudoCallEncounteredInLastInstructionExecution() || dynamicInstruction.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.NOP);

            this.fetchNpc = this.fetchNnpc;

            if (!this.context.isSpeculative() && this.context.getState() != ContextState.RUNNING) {
                this.lastDecodedDynamicInstruction = dynamicInstruction;
                this.lastDecodedDynamicInstructionCommitted = false;
            }

            this.getBlockingEventDispatcher().dispatch(new InstructionDecodedEvent(dynamicInstruction));

            if ((this.fetchNpc + 4) % this.lineSizeOfICache == 0) {
                hasDone = true;
            }

            BranchPredictorUpdate branchPredictorUpdate = new BranchPredictorUpdate();

            Reference<Integer> returnAddressStackRecoverIndexRef = new Reference<Integer>(0);
            int destination = dynamicInstruction.getStaticInstruction().getMnemonic().isControl() ? this.branchPredictor.predict(this.fetchNpc, 0, dynamicInstruction.getStaticInstruction().getMnemonic(), branchPredictorUpdate, returnAddressStackRecoverIndexRef) : this.fetchNpc + 4;

            this.fetchNnpc = destination <= 1 ? this.fetchNpc + 4 : destination;

            if (this.fetchNnpc != this.fetchNpc + 4) {
                hasDone = true;
            }

            this.decodeBuffer.getEntries().add(new DecodeBufferEntry(dynamicInstruction, this.context.getRegisterFile().getNpc(), this.context.getRegisterFile().getNnpc(), this.fetchNnpc, returnAddressStackRecoverIndexRef.get(), branchPredictorUpdate, this.context.isSpeculative()));
        }
    }

    public boolean registerRenameOne() {
        DecodeBufferEntry decodeBufferEntry = this.decodeBuffer.getEntries().get(0);

        DynamicInstruction dynamicInstruction = decodeBufferEntry.getDynamicInstruction();

        for (Map.Entry<RegisterDependencyType, Integer> entry : dynamicInstruction.getStaticInstruction().getNumFreePhysicalRegistersToAllocate().entrySet()) {
            if (this.getPhysicalRegisterFile(entry.getKey()).getNumFreePhysicalRegisters() < entry.getValue()) {
                return false;
            }
        }

        if ((dynamicInstruction.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.LOAD || dynamicInstruction.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.STORE) && this.getLoadStoreQueue().isFull()) {
            this.registerRenameStallsOnLoadStoreQueueFull++;
            return false;
        }

        ReorderBufferEntry reorderBufferEntry = new ReorderBufferEntry(this, dynamicInstruction, decodeBufferEntry.getNpc(), decodeBufferEntry.getNnpc(), decodeBufferEntry.getPredictedNnpc(), decodeBufferEntry.getReturnAddressStackRecoverIndex(), decodeBufferEntry.getBranchPredictorUpdate(), decodeBufferEntry.isSpeculative());
        reorderBufferEntry.setEffectiveAddressComputation(dynamicInstruction.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.LOAD || dynamicInstruction.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.STORE);

        for (int inputDependency : reorderBufferEntry.getDynamicInstruction().getStaticInstruction().getInputDependencies()) {
            reorderBufferEntry.getSourcePhysicalRegisters().put(inputDependency, this.renameTable.get(inputDependency));
        }

        for (int outputDependency : reorderBufferEntry.getDynamicInstruction().getStaticInstruction().getOutputDependencies()) {
            if (outputDependency != 0) {
                reorderBufferEntry.getOldPhysicalRegisters().put(outputDependency, this.renameTable.get(outputDependency));
                PhysicalRegisterFile.PhysicalRegister physReg = this.getPhysicalRegisterFile(RegisterDependencyType.getType(outputDependency)).allocate(outputDependency);
                this.renameTable.put(outputDependency, physReg);
                reorderBufferEntry.getTargetPhysicalRegisters().put(outputDependency, physReg);
            }
        }

        for (PhysicalRegisterFile.PhysicalRegister physicalRegister : reorderBufferEntry.getSourcePhysicalRegisters().values()) {
            if (!physicalRegister.isReady()) {
                reorderBufferEntry.setNumNotReadyOperands(reorderBufferEntry.getNumNotReadyOperands() + 1);
                physicalRegister.getDependents().add(reorderBufferEntry);
            }
        }

        if (reorderBufferEntry.isEffectiveAddressComputation()) {
            PhysicalRegisterFile.PhysicalRegister physicalRegister = reorderBufferEntry.getSourcePhysicalRegisters().get(reorderBufferEntry.getDynamicInstruction().getStaticInstruction().getInputDependencies().get(0));
            if (!physicalRegister.isReady()) {
                physicalRegister.getEffectiveAddressComputationOperandDependents().add(reorderBufferEntry);
            } else {
                reorderBufferEntry.setEffectiveAddressComputationOperandReady(true);
            }
        }

        if (dynamicInstruction.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.LOAD || dynamicInstruction.getStaticInstruction().getMnemonic().getType() == StaticInstructionType.STORE) {
            LoadStoreQueueEntry loadStoreQueueEntry = new LoadStoreQueueEntry(this, dynamicInstruction, decodeBufferEntry.getNpc(), decodeBufferEntry.getNnpc(), decodeBufferEntry.getPredictedNnpc(), 0, null, false);
            loadStoreQueueEntry.setEffectiveAddress(dynamicInstruction.getEffectiveAddress());

            loadStoreQueueEntry.setSourcePhysicalRegisters(reorderBufferEntry.getSourcePhysicalRegisters());
            loadStoreQueueEntry.setTargetPhysicalRegisters(reorderBufferEntry.getTargetPhysicalRegisters());

            for (PhysicalRegisterFile.PhysicalRegister physicalRegister : loadStoreQueueEntry.getSourcePhysicalRegisters().values()) {
                if (!physicalRegister.isReady()) {
                    physicalRegister.getDependents().add(loadStoreQueueEntry);
                }
            }

            loadStoreQueueEntry.setNumNotReadyOperands(reorderBufferEntry.getNumNotReadyOperands());

            PhysicalRegisterFile.PhysicalRegister storeAddressPhysicalRegister = loadStoreQueueEntry.getSourcePhysicalRegisters().get(loadStoreQueueEntry.getDynamicInstruction().getStaticInstruction().getInputDependencies().get(0));
            if (!storeAddressPhysicalRegister.isReady()) {
                storeAddressPhysicalRegister.getStoreAddressDependents().add(loadStoreQueueEntry);
            } else {
                loadStoreQueueEntry.setStoreAddressReady(true);
            }

            this.loadStoreQueue.getEntries().add(loadStoreQueueEntry);

            reorderBufferEntry.setLoadStoreQueueEntry(loadStoreQueueEntry);
        }

        this.reorderBuffer.getEntries().add(reorderBufferEntry);

        this.decodeBuffer.getEntries().remove(0);

        return true;
    }

    public boolean dispatchOne() {
        for (ReorderBufferEntry reorderBufferEntry : this.reorderBuffer.getEntries()) {
            if (!reorderBufferEntry.isDispatched()) {
                if (reorderBufferEntry.isAllOperandReady()) {//TODO: is it correct or efficient?
                    this.core.getReadyInstructionQueue().add(reorderBufferEntry);
                } else {
                    this.core.getWaitingInstructionQueue().add(reorderBufferEntry);
                }

                reorderBufferEntry.setDispatched();

                if (reorderBufferEntry.getLoadStoreQueueEntry() != null) {
                    LoadStoreQueueEntry loadStoreQueueEntry = reorderBufferEntry.getLoadStoreQueueEntry();

                    if (loadStoreQueueEntry.getDynamicInstruction().getStaticInstruction().getMnemonic().getType() == StaticInstructionType.STORE) {
                        if (loadStoreQueueEntry.isAllOperandReady()) {//TODO: is it correct or efficient?
                            this.core.getReadyStoreQueue().add(loadStoreQueueEntry);
                        } else {
                            this.core.getWaitingStoreQueue().add(loadStoreQueueEntry);
                        }
                    }

                    loadStoreQueueEntry.setDispatched();
                }

                return true;
            }
        }

        return false;
    }

    public void refreshLoadStoreQueue() { //TODO: to be clarified
        List<Integer> stdUnknowns = new ArrayList<Integer>();

        for (LoadStoreQueueEntry loadStoreQueueEntry : this.loadStoreQueue.getEntries()) {
            if (loadStoreQueueEntry.getDynamicInstruction().getStaticInstruction().getMnemonic().getType() == StaticInstructionType.STORE) {
                if (loadStoreQueueEntry.isStoreAddressReady()) {
                    break;
                } else if (!loadStoreQueueEntry.isAllOperandReady()) {
                    stdUnknowns.add(loadStoreQueueEntry.getEffectiveAddress());
                } else {
                    for (int i = 0; i < stdUnknowns.size(); i++) {
                        if (stdUnknowns.get(i) == loadStoreQueueEntry.getEffectiveAddress()) {
                            stdUnknowns.set(i, 0);
                        }
                    }
                }
            }

            if (loadStoreQueueEntry.getDynamicInstruction().getStaticInstruction().getMnemonic().getType() == StaticInstructionType.LOAD && loadStoreQueueEntry.isDispatched() && !this.core.getReadyLoadQueue().contains(loadStoreQueueEntry) && !loadStoreQueueEntry.isIssued() && !loadStoreQueueEntry.isCompleted() && loadStoreQueueEntry.isAllOperandReady()) {
                if (!stdUnknowns.contains(loadStoreQueueEntry.getEffectiveAddress())) {
                    this.core.getReadyLoadQueue().add(loadStoreQueueEntry);
                }
            }
        }
    }

    public void commit() {
        int COMMIT_TIMEOUT = 1000000;

        if (this.getCycleAccurateEventQueue().getCurrentCycle() - this.lastCommitCycle > COMMIT_TIMEOUT) {
            if (noInstructionCommittedCounterThreshold > 5) {
                getSimulation().dumpPendingFlowTree();

//                Logger.fatalf(Logger.THREAD, "%s: No instruction committed for %d cycles, %d committed.", this.getCycleAccurateEventQueue().getCurrentCycle(), this.getName(), COMMIT_TIMEOUT, this.totalInstructions);
            } else {
                this.lastCommitCycle = this.getCycleAccurateEventQueue().getCurrentCycle();
                this.noInstructionCommittedCounterThreshold++;
            }
        }

        int numCommitted = 0;

        while (!this.reorderBuffer.isEmpty() && numCommitted < getExperiment().getArchitecture().getCommitWidth()) {
            ReorderBufferEntry reorderBufferEntry = this.reorderBuffer.getEntries().get(0);

            if (!reorderBufferEntry.isCompleted()) {
                reorderBufferEntry.getDynamicInstruction().setCyclesSpentAtHeadOfReorderBuffer(reorderBufferEntry.getDynamicInstruction().getCyclesSpentAtHeadOfReorderBuffer() + 1);
                break;
            }

            if (reorderBufferEntry.isSpeculative()) {
                if (this.branchPredictor.isDynamic()) {
                    ((DynamicBranchPredictor) this.branchPredictor).getReturnAddressStack().recover(reorderBufferEntry.getReturnAddressStackRecoverIndex());
                }

                this.context.exitSpeculativeState();

                this.fetchNpc = this.context.getRegisterFile().getNpc();
                this.fetchNnpc = this.context.getRegisterFile().getNnpc();

                this.squash();
                break;
            }

            if (reorderBufferEntry.isEffectiveAddressComputation()) {
                LoadStoreQueueEntry loadStoreQueueEntry = reorderBufferEntry.getLoadStoreQueueEntry();

                if (!loadStoreQueueEntry.isCompleted()) {
                    break;
                }

                this.core.removeFromQueues(loadStoreQueueEntry);

                this.loadStoreQueue.getEntries().remove(loadStoreQueueEntry);
            }

            for (int outputDependency : reorderBufferEntry.getDynamicInstruction().getStaticInstruction().getOutputDependencies()) {
                if (outputDependency != 0) {
                    reorderBufferEntry.getOldPhysicalRegisters().get(outputDependency).reclaim();
                    reorderBufferEntry.getTargetPhysicalRegisters().get(outputDependency).commit();
                }
            }

            if (reorderBufferEntry.getDynamicInstruction().getStaticInstruction().getMnemonic().isControl()) {
                this.branchPredictor.update(
                        reorderBufferEntry.getDynamicInstruction().getPc(),
                        reorderBufferEntry.getNnpc(),
                        reorderBufferEntry.getNnpc() != (reorderBufferEntry.getNpc() + 4),
                        reorderBufferEntry.getPredictedNnpc() != (reorderBufferEntry.getNpc() + 4),
                        reorderBufferEntry.getPredictedNnpc() == reorderBufferEntry.getNnpc(),
                        reorderBufferEntry.getDynamicInstruction().getStaticInstruction().getMnemonic(),
                        reorderBufferEntry.getBranchPredictorUpdate()
                );
            }

            this.core.removeFromQueues(reorderBufferEntry);

            this.getBlockingEventDispatcher().dispatch(new InstructionCommittedEvent(reorderBufferEntry.getDynamicInstruction()));

            if (this.context.getState() == ContextState.FINISHED && reorderBufferEntry.getDynamicInstruction() == this.lastDecodedDynamicInstruction) {
                this.lastDecodedDynamicInstructionCommitted = true;
            }

            this.reorderBuffer.getEntries().remove(0);

            this.totalInstructions++;

            this.lastCommitCycle = this.getCycleAccurateEventQueue().getCurrentCycle();

            numCommitted++;

//			Logger.infof(Logger.DEBUG, "%s: instruction committed: reorderBufferEntry.id=%d", this.getName(), reorderBufferEntry.getId());
        }
    }

    public void squash() {
//		Logger.infof(Logger.THREAD, "%s: squash", this.getName());

        while (!this.reorderBuffer.isEmpty()) {
            ReorderBufferEntry reorderBufferEntry = this.reorderBuffer.getEntries().get(this.reorderBuffer.getEntries().size() - 1);

            if (reorderBufferEntry.isEffectiveAddressComputation()) {
                LoadStoreQueueEntry loadStoreQueueEntry = reorderBufferEntry.getLoadStoreQueueEntry();

                this.core.removeFromQueues(loadStoreQueueEntry);

                this.loadStoreQueue.getEntries().remove(loadStoreQueueEntry);
            }

            this.core.removeFromQueues(reorderBufferEntry);

            for (int outputDependency : reorderBufferEntry.getDynamicInstruction().getStaticInstruction().getOutputDependencies()) {
                if (outputDependency != 0) {
                    reorderBufferEntry.getTargetPhysicalRegisters().get(outputDependency).recover();
                    this.renameTable.put(outputDependency, reorderBufferEntry.getOldPhysicalRegisters().get(outputDependency));
                }
            }

            reorderBufferEntry.getTargetPhysicalRegisters().clear();

            this.reorderBuffer.getEntries().remove(reorderBufferEntry);
        }

        if (!this.reorderBuffer.getEntries().isEmpty() || !this.loadStoreQueue.getEntries().isEmpty()) {
            throw new IllegalArgumentException();
        }

        this.core.getFunctionalUnitPool().releaseAll(); //TODO: is it correct or just release those FUs that this thread uses?

        this.decodeBuffer.getEntries().clear();
    }

    private PhysicalRegisterFile getPhysicalRegisterFile(RegisterDependencyType type) {
        switch (type) {
            case INTEGER:
                return this.intPhysicalRegisterFile;
            case FLOAT:
                return this.fpPhysicalRegisterFile;
            default:
                return this.miscPhysicalRegisterFile;
        }
    }

    public boolean isLastDecodedDynamicInstructionCommitted() {
        return this.lastDecodedDynamicInstruction == null || lastDecodedDynamicInstructionCommitted;
    }

    private static int aligned(int n, int alignment) {
        return n & ~(alignment - 1);
    }

    public static boolean isMainThread(MemoryHierarchyThread thread) {
        return isMainThread(thread.getId());
    }

    public static boolean isMainThread(int threadId) {
        return threadId == getMainThreadId();
    }

    public static int getMainThreadId() {
        return 0; //TODO: main thread should not be hard coded.
    }

    public static boolean isHelperThread(MemoryHierarchyThread thread) {
        return isHelperThread(thread.getId());
    }

    public static boolean isHelperThread(int threadId) {
        return threadId == getHelperThreadId();
    }

    public static int getHelperThreadId() {
        return 2; //TODO: helper thread should not be hard coded.
    }
}
