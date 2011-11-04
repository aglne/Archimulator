/*******************************************************************************
 * Copyright (c) 2010-2011 by Min Cai (min.cai.china@gmail.com).
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
package archimulator.sim.strategy;

import archimulator.core.Core;
import archimulator.sim.Logger;
import archimulator.util.action.Action1;
import archimulator.sim.event.DumpStatEvent;
import archimulator.sim.event.PauseSimulationEvent;
import archimulator.sim.event.ResetStatEvent;
import archimulator.sim.event.StopSimulationEvent;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public abstract class SequentialSimulationStrategy extends SimulationStrategy {
    private boolean requestStop;
    private boolean requestPause;
    private CyclicBarrier phaser;

    public SequentialSimulationStrategy(CyclicBarrier phaser) {
        this.phaser = phaser;
    }

    public abstract boolean canDoFastForwardOneCycle();

    public abstract boolean canDoCacheWarmupOneCycle();

    public abstract boolean canDoMeasurementOneCycle();

    public abstract void beginSimulation();

    public abstract void endSimulation();

    private void doFastForward() {
        this.getSimulation().getLogger().info(Logger.SIMULATION, "Switched to fast forward mode.");

        while (!requestStop && !this.getSimulation().getProcessor().getKernel().getContexts().isEmpty() && this.canDoFastForwardOneCycle()) {
            for (Core core : this.getSimulation().getProcessor().getCores()) {
                core.doFastForwardOneCycle();
            }

            this.advanceOneCycle();
        }
    }

    private void doCacheWarmup() {
        this.getSimulation().getLogger().info(Logger.SIMULATION, "Switched to cache warmup mode.");

        while (!requestStop && !this.getSimulation().getProcessor().getKernel().getContexts().isEmpty() && this.canDoCacheWarmupOneCycle()) {
            for (Core core : this.getSimulation().getProcessor().getCores()) {
                core.doCacheWarmupOneCycle();
            }

            this.advanceOneCycle();
        }
    }

    private void doMeasurement() {
        this.getSimulation().getLogger().info(Logger.SIMULATION, "Switched to measurement mode.");

        while (!requestStop && !this.getSimulation().getProcessor().getKernel().getContexts().isEmpty() && this.canDoMeasurementOneCycle()) {
            for (Core core : this.getSimulation().getProcessor().getCores()) {
                core.doMeasurementOneCycle();
            }

            this.advanceOneCycle();
        }
    }

    private void advanceOneCycle() {
        this.doHouseKeeping();

        if (this.requestPause) {
            try {
                this.phaser.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BrokenBarrierException e) {
                throw new RuntimeException(e);
            }
            this.requestPause = false;
        }

        this.getSimulation().getCycleAccurateEventQueue().advanceOneCycle();
    }

    @Override
    public void execute() {
        getSimulation().getBlockingEventDispatcher().addListener(PauseSimulationEvent.class, new Action1<PauseSimulationEvent>() {
            public void apply(PauseSimulationEvent event) {
                requestPause = true;
            }
        });

        getSimulation().getBlockingEventDispatcher().addListener(StopSimulationEvent.class, new Action1<StopSimulationEvent>() {
            public void apply(StopSimulationEvent event) {
                requestStop = true;
            }
        });

        this.beginSimulation();

        this.getSimulation().getStopWatch().start();

        if (this.isSupportFastForward()) {
            this.doFastForward();

            this.getSimulation().getBlockingEventDispatcher().dispatch(new DumpStatEvent(DumpStatEvent.Type.FUNCTIONAL_SIMULATION, this.getSimulation().getStatInFastForward()));
            this.getSimulation().getBlockingEventDispatcher().dispatch(new ResetStatEvent());
        }

        if (this.isSupportCacheWarmup()) {
            this.getSimulation().getBlockingEventDispatcher().dispatch(new ResetStatEvent());

            this.getSimulation().getStopWatch().reset();
            this.getSimulation().getStopWatch().start();

            this.doCacheWarmup();

            this.getSimulation().getBlockingEventDispatcher().dispatch(new DumpStatEvent(DumpStatEvent.Type.DETAILED_SIMULATION, this.getSimulation().getStatInWarmup()));
            this.getSimulation().getBlockingEventDispatcher().dispatch(new ResetStatEvent());
        }

        if (this.isSupportMeasurement()) {
            this.getSimulation().getBlockingEventDispatcher().dispatch(new ResetStatEvent());

            this.getSimulation().getStopWatch().reset();
            this.getSimulation().getStopWatch().start();

            this.doMeasurement();

            this.getSimulation().getBlockingEventDispatcher().dispatch(new DumpStatEvent(DumpStatEvent.Type.DETAILED_SIMULATION, this.getSimulation().getStatInMeasurement()));
            this.getSimulation().getBlockingEventDispatcher().dispatch(new ResetStatEvent());
        }

        this.getSimulation().getStopWatch().stop();

        this.endSimulation();
    }
}
