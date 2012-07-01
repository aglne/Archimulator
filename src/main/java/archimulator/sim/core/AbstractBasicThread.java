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

import archimulator.sim.base.event.ResetStatEvent;
import archimulator.sim.base.simulation.BasicSimulationObject;
import archimulator.sim.core.bpred.*;
import archimulator.sim.isa.ArchitecturalRegisterFile;
import archimulator.sim.isa.RegisterDependencyType;
import archimulator.sim.os.Context;
import archimulator.sim.uncore.tlb.TranslationLookasideBuffer;
import net.pickapack.action.Action1;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractBasicThread extends BasicSimulationObject implements Thread {
    protected int num;
    protected Context context;
    protected int id;
    protected String name;
    protected Core core;

    protected BranchPredictor bpred;

    protected RegisterRenameTable renameTable;

    protected PipelineBuffer<DecodeBufferEntry> decodeBuffer;
    protected PipelineBuffer<ReorderBufferEntry> reorderBuffer;
    protected PipelineBuffer<LoadStoreQueueEntry> loadStoreQueue;

    private TranslationLookasideBuffer itlb;
    private TranslationLookasideBuffer dtlb;

    protected PhysicalRegisterFile intPhysicalRegisterFile;
    protected PhysicalRegisterFile fpPhysicalRegisterFile;
    protected PhysicalRegisterFile miscPhysicalRegisterFile;

    protected long totalInsts;

    protected long decodeBufferFull;
    protected long reorderBufferFull;
    protected long loadStoreQueueFull;

    protected long intPhysicalRegisterFileFull;
    protected long fpPhysicalRegisterFileFull;
    protected long miscPhysicalRegisterFileFull;

    protected long fetchStallsOnDecodeBufferIsFull;

    protected long registerRenameStallsOnDecodeBufferIsEmpty;
    protected long registerRenameStallsOnReorderBufferIsFull;
    protected long registerRenameStallsOnLoadStoreQueueFull;

    protected long selectionStallOnCanNotLoad;
    protected long selectionStallOnCanNotStore;
    protected long selectionStallOnNoFreeFunctionalUnit;

    public AbstractBasicThread(Core core, int num) {
        super(core);

        this.core = core;

        this.num = num;
        this.id = this.core.getNum() * this.core.getProcessor().getConfig().getNumThreadsPerCore() + this.num;

        this.name = "c" + this.core.getNum() + "t" + this.num;

        switch (core.getProcessor().getConfig().getBpred().getType()) {
            case PERFECT:
                this.bpred = new PerfectBranchPredictor(this, this.name + ".bpred");
                break;
            case TAKEN:
                this.bpred = new TakenBranchPredictor(this, this.name + ".bpred");
                break;
            case NOT_TAKEN:
                this.bpred = new NotTakenBranchPredictor(this, this.name + ".bpred");
                break;
            case TWO_BIT:
                this.bpred = new TwoBitBranchPredictor(this, this.name + ".bpred", (TwoBitBranchPredictorConfig) core.getProcessor().getConfig().getBpred());
                break;
            case TWO_LEVEL:
                this.bpred = new TwoLevelBranchPredictor(this, this.name + ".bpred", (TwoLevelBranchPredictorConfig) core.getProcessor().getConfig().getBpred());
                break;
            case COMBINED:
                this.bpred = new CombinedBranchPredictor(this, this.name + ".bpred", (CombinedBranchPredictorConfig) core.getProcessor().getConfig().getBpred());
                break;
            default:
                throw new IllegalArgumentException();
        }

        this.intPhysicalRegisterFile = new PhysicalRegisterFile(this.name + ".intPhysicalRegisterFile", core.getProcessor().getConfig().getPhysicalRegisterFileCapacity());
        this.fpPhysicalRegisterFile = new PhysicalRegisterFile(this.name + ".fpPhysicalRegisterFile", core.getProcessor().getConfig().getPhysicalRegisterFileCapacity());
        this.miscPhysicalRegisterFile = new PhysicalRegisterFile(this.name + ".miscPhysicalRegisterFile", core.getProcessor().getConfig().getPhysicalRegisterFileCapacity());

        this.renameTable = new RegisterRenameTable(this.name + ".renameTable");

        for (int i = 0; i < ArchitecturalRegisterFile.NUM_INT_REGS; i++) {
            int dep = RegisterDependencyType.toRegisterDependency(RegisterDependencyType.INTEGER, i);
            PhysicalRegisterFile.PhysicalRegister physReg = this.intPhysicalRegisterFile.getEntries().get(i);
            physReg.reserve(dep);
            this.renameTable.put(dep, physReg);
        }

        for (int i = 0; i < ArchitecturalRegisterFile.NUM_FLOAT_REGS; i++) {
            int dep = RegisterDependencyType.toRegisterDependency(RegisterDependencyType.FLOAT, i);
            PhysicalRegisterFile.PhysicalRegister physReg = this.fpPhysicalRegisterFile.getEntries().get(i);
            physReg.reserve(dep);
            this.renameTable.put(dep, physReg);
        }

        for (int i = 0; i < ArchitecturalRegisterFile.NUM_MISC_REGS; i++) {
            int dep = RegisterDependencyType.toRegisterDependency(RegisterDependencyType.MISC, i);
            PhysicalRegisterFile.PhysicalRegister physReg = this.miscPhysicalRegisterFile.getEntries().get(i);
            physReg.reserve(dep);
            this.renameTable.put(dep, physReg);
        }

        this.decodeBuffer = new PipelineBuffer<DecodeBufferEntry>(this.core.getProcessor().getConfig().getDecodeBufferCapacity());
        this.reorderBuffer = new PipelineBuffer<ReorderBufferEntry>(this.core.getProcessor().getConfig().getReorderBufferCapacity());
        this.loadStoreQueue = new PipelineBuffer<LoadStoreQueueEntry>(this.core.getProcessor().getConfig().getLoadStoreQueueCapacity());

        this.getBlockingEventDispatcher().addListener(ResetStatEvent.class, new Action1<ResetStatEvent>() {
            public void apply(ResetStatEvent event) {
                AbstractBasicThread.this.totalInsts = 0;

                AbstractBasicThread.this.decodeBufferFull = 0;
                AbstractBasicThread.this.reorderBufferFull = 0;
                AbstractBasicThread.this.loadStoreQueueFull = 0;

                AbstractBasicThread.this.intPhysicalRegisterFileFull = 0;
                AbstractBasicThread.this.fpPhysicalRegisterFileFull = 0;
                AbstractBasicThread.this.miscPhysicalRegisterFileFull = 0;

                AbstractBasicThread.this.fetchStallsOnDecodeBufferIsFull = 0;

                AbstractBasicThread.this.registerRenameStallsOnDecodeBufferIsEmpty = 0;
                AbstractBasicThread.this.registerRenameStallsOnReorderBufferIsFull = 0;
                AbstractBasicThread.this.registerRenameStallsOnLoadStoreQueueFull = 0;

                AbstractBasicThread.this.selectionStallOnCanNotLoad = 0;
                AbstractBasicThread.this.selectionStallOnCanNotStore = 0;
                AbstractBasicThread.this.selectionStallOnNoFreeFunctionalUnit = 0;
            }
        });
    }

    public void updatePerCycleStats() {
        if (this.decodeBuffer.isFull()) {
            this.decodeBufferFull++;
        }

        if (this.reorderBuffer.isFull()) {
            this.reorderBufferFull++;
        }

        if (this.loadStoreQueue.isFull()) {
            this.loadStoreQueueFull++;
        }

        if (this.intPhysicalRegisterFile.isFull()) {
            this.intPhysicalRegisterFileFull++;
        }

        if (this.fpPhysicalRegisterFile.isFull()) {
            this.fpPhysicalRegisterFileFull++;
        }

        if (this.miscPhysicalRegisterFile.isFull()) {
            this.miscPhysicalRegisterFileFull++;
        }
    }

    public int getNum() {
        return num;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Core getCore() {
        return core;
    }

    @Override
    public BranchPredictor getBpred() {
        return bpred;
    }

    public PipelineBuffer<DecodeBufferEntry> getDecodeBuffer() {
        return decodeBuffer;
    }

    public PipelineBuffer<ReorderBufferEntry> getReorderBuffer() {
        return reorderBuffer;
    }

    public PipelineBuffer<LoadStoreQueueEntry> getLoadStoreQueue() {
        return loadStoreQueue;
    }

    public long getTotalInsts() {
        return totalInsts;
    }

    public TranslationLookasideBuffer getItlb() {
        return itlb;
    }

    public void setItlb(TranslationLookasideBuffer itlb) {
        this.itlb = itlb;
    }

    public TranslationLookasideBuffer getDtlb() {
        return dtlb;
    }

    public void setDtlb(TranslationLookasideBuffer dtlb) {
        this.dtlb = dtlb;
    }

    public List<TranslationLookasideBuffer> getTlbs() {
        List<TranslationLookasideBuffer> tlbs = new ArrayList<TranslationLookasideBuffer>();
        tlbs.add(getItlb());
        tlbs.add(getDtlb());
        return tlbs;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void incRegisterRenameStallsOnDecodeBufferIsEmpty() {
        this.registerRenameStallsOnDecodeBufferIsEmpty++;
    }

    public void incRegisterRenameStallsOnReorderBufferIsFull() {
        this.registerRenameStallsOnReorderBufferIsFull++;
    }

    public void incSelectionStallOnCanNotLoad() {
        this.selectionStallOnCanNotLoad++;
    }

    public void incSelectionStallOnCanNotStore() {
        this.selectionStallOnCanNotStore++;
    }

    public void incSelectionStallOnNoFreeFunctionalUnit() {
        this.selectionStallOnNoFreeFunctionalUnit++;
    }

    @Override
    public long getDecodeBufferFull() {
        return decodeBufferFull;
    }

    @Override
    public long getReorderBufferFull() {
        return reorderBufferFull;
    }

    @Override
    public long getLoadStoreQueueFull() {
        return loadStoreQueueFull;
    }

    @Override
    public long getIntPhysicalRegisterFileFull() {
        return intPhysicalRegisterFileFull;
    }

    @Override
    public long getFpPhysicalRegisterFileFull() {
        return fpPhysicalRegisterFileFull;
    }

    @Override
    public long getMiscPhysicalRegisterFileFull() {
        return miscPhysicalRegisterFileFull;
    }

    @Override
    public long getFetchStallsOnDecodeBufferIsFull() {
        return fetchStallsOnDecodeBufferIsFull;
    }

    @Override
    public long getRegisterRenameStallsOnDecodeBufferIsEmpty() {
        return registerRenameStallsOnDecodeBufferIsEmpty;
    }

    @Override
    public long getRegisterRenameStallsOnReorderBufferIsFull() {
        return registerRenameStallsOnReorderBufferIsFull;
    }

    @Override
    public long getRegisterRenameStallsOnLoadStoreQueueFull() {
        return registerRenameStallsOnLoadStoreQueueFull;
    }

    @Override
    public long getSelectionStallOnCanNotLoad() {
        return selectionStallOnCanNotLoad;
    }

    @Override
    public long getSelectionStallOnCanNotStore() {
        return selectionStallOnCanNotStore;
    }

    @Override
    public long getSelectionStallOnNoFreeFunctionalUnit() {
        return selectionStallOnNoFreeFunctionalUnit;
    }
}
