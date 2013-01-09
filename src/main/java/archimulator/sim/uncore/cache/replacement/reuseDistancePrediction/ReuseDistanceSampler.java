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
package archimulator.sim.uncore.cache.replacement.reuseDistancePrediction;

import archimulator.sim.common.BasicSimulationObject;
import archimulator.sim.common.SimulationEvent;
import archimulator.sim.common.SimulationObject;
import net.pickapack.math.Quantizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Reuse distance sampler.
 *
 * @author Min Cai
 */
public class ReuseDistanceSampler extends BasicSimulationObject {
    private String name;

    private List<ReuseDistanceSamplerEntry> entries;

    private int samplingPeriod;
    private int samplingCounter;

    private Quantizer reuseDistanceQuantizer;

    /**
     * Create a reuse distance sampler.
     *
     * @param parent                 the parent simulation object
     * @param samplingPeriod         the sampling period
     * @param maxReuseDistance       the maximum reuse distance
     * @param reuseDistanceQuantizer the reuse distance quantizer
     */
    public ReuseDistanceSampler(SimulationObject parent, String name, int samplingPeriod, int maxReuseDistance, Quantizer reuseDistanceQuantizer) {
        super(parent);

        this.name = name;
        this.samplingPeriod = samplingPeriod;
        this.reuseDistanceQuantizer = reuseDistanceQuantizer;

        this.samplingCounter = 0;

        this.entries = new ArrayList<ReuseDistanceSamplerEntry>();
        for (int i = 0; i < maxReuseDistance / this.samplingPeriod; i++) {
            this.entries.add(new ReuseDistanceSamplerEntry());
        }
    }

    /**
     * Update.
     *
     * @param pc      the value of the program counter (PC)
     * @param address the address
     */
    public void update(int pc, int address) {
        for (int i = 0; i < this.entries.size(); i++) {
            ReuseDistanceSamplerEntry entry = this.entries.get(i);
            if (entry.isValid() && entry.getAddress() == address) {
                entry.setValid(false);
                this.getBlockingEventDispatcher().dispatch(new ReuseDistanceSampledEvent(this, entry.getPc(), this.reuseDistanceQuantizer.quantize(i * this.samplingPeriod)));
                break;
            }
        }

        if (this.samplingCounter == 0) {
            ReuseDistanceSamplerEntry victimEntry = this.entries.get(this.entries.size() - 1);
            if (victimEntry.isValid()) {
                this.getBlockingEventDispatcher().dispatch(new ReuseDistanceSampledEvent(this, victimEntry.getPc(), this.reuseDistanceQuantizer.getMaxValue()));
            }

            this.entries.remove(victimEntry);
            this.entries.add(0, victimEntry);

            victimEntry.setValid(true);
            victimEntry.setPc(pc);
            victimEntry.setAddress(address);

            this.samplingCounter = this.samplingPeriod - 1;
        } else {
            samplingCounter--;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the list of entries.
     *
     * @return the list of entries
     */
    public List<ReuseDistanceSamplerEntry> getEntries() {
        return entries;
    }

    /**
     * Get the sampling period.
     *
     * @return the sampling period
     */
    public int getSamplingPeriod() {
        return samplingPeriod;
    }

    /**
     * Get the value of the sampling counter.
     *
     * @return the value of the sampling counter
     */
    public int getSamplingCounter() {
        return samplingCounter;
    }

    /**
     * Get the reuse distance quantizer.
     *
     * @return the reuse distance quantizer
     */
    public Quantizer getReuseDistanceQuantizer() {
        return reuseDistanceQuantizer;
    }

    /**
     * The event when a reuse distance is sampled.
     */
    public class ReuseDistanceSampledEvent extends SimulationEvent {
        private int pc;
        private int reuseDistance;

        /**
         * Create a event when a reuse distance is sampled.
         *
         * @param sender        the sender simulation object
         * @param pc            the value of the program counter (PC)
         * @param reuseDistance the reuse distance
         */
        public ReuseDistanceSampledEvent(SimulationObject sender, int pc, int reuseDistance) {
            super(sender);
            this.pc = pc;
            this.reuseDistance = reuseDistance;
        }

        /**
         * Get the value of the program counter (PC).
         *
         * @return the value of the program counter (PC)
         */
        public int getPc() {
            return pc;
        }

        /**
         * Get the reuse distance.
         *
         * @return the reuse distance
         */
        public int getReuseDistance() {
            return reuseDistance;
        }
    }
}
