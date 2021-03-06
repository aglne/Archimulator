/**
 * ****************************************************************************
 * Copyright (c) 2010-2015 by Min Cai (min.cai.china@gmail.com).
 * <p>
 * This file is part of the Archimulator multicore architectural simulator.
 * <p>
 * Archimulator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Archimulator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with Archimulator. If not, see <http://www.gnu.org/licenses/>.
 * ****************************************************************************
 */
package archimulator.core;

import archimulator.common.BasicSimulationObject;
import archimulator.uncore.MemoryAccessInitiatedEvent;
import archimulator.uncore.MemoryHierarchyAccess;
import archimulator.uncore.MemoryHierarchyAccessType;
import archimulator.uncore.coherence.msi.controller.CacheController;
import archimulator.util.action.Action;
import archimulator.util.math.Counter;

/**
 * Abstract memory hierarchy core.
 *
 * @author Min Cai
 */
public abstract class AbstractMemoryHierarchyCore extends BasicSimulationObject implements MemoryHierarchyCore {
    /**
     * The number of the core.
     */
    private int num;

    /**
     * The name of the core.
     */
    private String name;

    /**
     * The processor.
     */
    private Processor processor;

    /**
     * The L1I cache controller.
     */
    private CacheController l1IController;

    /**
     * The L1D cache controller.
     */
    private CacheController l1DController;

    /**
     * Create an abstract memory hierarchy core.
     *
     * @param processor the parent processor
     * @param num       the number of the core
     */
    public AbstractMemoryHierarchyCore(Processor processor, int num) {
        super(processor);

        this.num = num;
        this.name = "c" + this.num;

        this.processor = processor;
    }

    @Override
    public void doMeasurementOneCycle() {
    }

    @Override
    public boolean canIfetch(Thread thread, int virtualAddress) {
        int physicalTag = this.l1IController.getCache().getTag(thread.getContext().getProcess().getMemory().getPhysicalAddress(virtualAddress));
        return this.l1IController.canAccess(MemoryHierarchyAccessType.IFETCH, physicalTag);
    }

    @Override
    public boolean canLoad(Thread thread, int virtualAddress) {
        int physicalTag = this.l1DController.getCache().getTag(thread.getContext().getProcess().getMemory().getPhysicalAddress(virtualAddress));
        return this.l1DController.canAccess(MemoryHierarchyAccessType.LOAD, physicalTag);
    }

    @Override
    public boolean canStore(Thread thread, int virtualAddress) {
        int physicalTag = this.l1DController.getCache().getTag(thread.getContext().getProcess().getMemory().getPhysicalAddress(virtualAddress));
        return this.l1DController.canAccess(MemoryHierarchyAccessType.STORE, physicalTag);
    }

    @Override
    public void ifetch(Thread thread, int virtualAddress, int virtualPc, final Action onCompletedCallback) {
        final int physicalAddress = thread.getContext().getProcess().getMemory().getPhysicalAddress(virtualAddress);
        final int physicalTag = this.l1IController.getCache().getTag(physicalAddress);

        final Counter counterPending = new Counter(0);

        counterPending.increment();

        MemoryHierarchyAccess alias = this.l1IController.findAccess(physicalTag);
        MemoryHierarchyAccess access = this.l1IController.beginAccess(null, thread, MemoryHierarchyAccessType.IFETCH, virtualPc, physicalAddress, physicalTag, () -> {
            counterPending.decrement();

            if (counterPending.getValue() == 0) {
                onCompletedCallback.apply();
            }
        });

        if (alias == null) {
            counterPending.increment();

            thread.getItlb().access(access, () -> {
                counterPending.decrement();

                if (counterPending.getValue() == 0) {
                    onCompletedCallback.apply();
                }
            });

            this.l1IController.receiveIfetch(access, () -> l1IController.endAccess(physicalTag));
        }

        this.getBlockingEventDispatcher().dispatch(new MemoryAccessInitiatedEvent(thread, virtualPc, physicalAddress, physicalTag, MemoryHierarchyAccessType.IFETCH));
    }

    @Override
    public void load(DynamicInstruction dynamicInstruction, int virtualAddress, int virtualPc, final Action onCompletedCallback) {
        final int physicalAddress = dynamicInstruction.getThread().getContext().getProcess().getMemory().getPhysicalAddress(virtualAddress);
        final int physicalTag = this.l1DController.getCache().getTag(physicalAddress);

        final Counter counterPending = new Counter(0);

        counterPending.increment();

        MemoryHierarchyAccess alias = this.l1DController.findAccess(physicalTag);
        MemoryHierarchyAccess access = this.l1DController.beginAccess(dynamicInstruction, dynamicInstruction.getThread(), MemoryHierarchyAccessType.LOAD, virtualPc, physicalAddress, physicalTag, new Action() {
            public void apply() {
                counterPending.decrement();

                if (counterPending.getValue() == 0) {
                    onCompletedCallback.apply();
                }
            }
        });

        if (alias == null) {
            counterPending.increment();

            dynamicInstruction.getThread().getDtlb().access(access, () -> {
                counterPending.decrement();

                if (counterPending.getValue() == 0) {
                    onCompletedCallback.apply();
                }
            });

            this.l1DController.receiveLoad(access, () -> l1DController.endAccess(physicalTag));
        }

        this.getBlockingEventDispatcher().dispatch(new MemoryAccessInitiatedEvent(dynamicInstruction.getThread(), virtualPc, physicalAddress, physicalTag, MemoryHierarchyAccessType.LOAD));
    }

    @Override
    public void store(DynamicInstruction dynamicInstruction, int virtualAddress, int virtualPc, final Action onCompletedCallback) {
        final int physicalAddress = dynamicInstruction.getThread().getContext().getProcess().getMemory().getPhysicalAddress(virtualAddress);
        final int physicalTag = this.l1DController.getCache().getTag(physicalAddress);

        final Counter counterPending = new Counter(0);

        counterPending.increment();

        MemoryHierarchyAccess alias = this.l1DController.findAccess(physicalTag);
        MemoryHierarchyAccess access = this.l1DController.beginAccess(dynamicInstruction, dynamicInstruction.getThread(), MemoryHierarchyAccessType.STORE, virtualPc, physicalAddress, physicalTag, new Action() {
            public void apply() {
                counterPending.decrement();

                if (counterPending.getValue() == 0) {
                    onCompletedCallback.apply();
                }
            }
        });

        if (alias == null) {
            counterPending.increment();

            dynamicInstruction.getThread().getDtlb().access(access, () -> {
                counterPending.decrement();

                if (counterPending.getValue() == 0) {
                    onCompletedCallback.apply();
                }
            });

            this.l1DController.receiveStore(access, () -> l1DController.endAccess(physicalTag));
        }

        this.getBlockingEventDispatcher().dispatch(new MemoryAccessInitiatedEvent(dynamicInstruction.getThread(), virtualPc, physicalAddress, physicalTag, MemoryHierarchyAccessType.STORE));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getNum() {
        return num;
    }

    @Override
    public Processor getProcessor() {
        return processor;
    }

    @Override
    public CacheController getL1IController() {
        return l1IController;
    }

    @Override
    public void setL1IController(CacheController l1IController) {
        this.l1IController = l1IController;
        this.l1IController.setCore(this);
    }

    @Override
    public CacheController getL1DController() {
        return l1DController;
    }

    @Override
    public void setL1DController(CacheController l1DController) {
        this.l1DController = l1DController;
        this.l1DController.setCore(this);
    }
}
