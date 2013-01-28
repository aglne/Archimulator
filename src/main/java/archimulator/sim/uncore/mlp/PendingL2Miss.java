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
package archimulator.sim.uncore.mlp;

import archimulator.sim.uncore.MemoryHierarchyAccess;

/**
 * Pending L2 cache miss.
 *
 * @author Min Cai
 */
public class PendingL2Miss {
    private MemoryHierarchyAccess access;
    private long beginCycle;
    private long endCycle;

    private transient int numMlpSamples;
    private transient int mlpSum;

    /**
     * Create a pending L2 cache miss.
     *
     * @param access     the memory hierarchy access
     * @param beginCycle the time in cycles when the access begins
     */
    public PendingL2Miss(MemoryHierarchyAccess access, long beginCycle) {
        this.access = access;
        this.beginCycle = beginCycle;
    }

    /**
     * Get the memory hierarchy access.
     *
     * @return the memory hierarchy access
     */
    public MemoryHierarchyAccess getAccess() {
        return access;
    }

    /**
     * Get the time in cycles when the access begins.
     *
     * @return the time in cycles when the access begins
     */
    public long getBeginCycle() {
        return beginCycle;
    }

    /**
     * Get the time in cycles when the access ends.
     *
     * @return the time in cycles when the access ends
     */
    public long getEndCycle() {
        return endCycle;
    }

    /**
     * Set the time in cycles when the access ends.
     *
     * @param endCycle the time in cycles when the access ends
     */
    public void setEndCycle(long endCycle) {
        this.endCycle = endCycle;
    }

    /**
     * Get the number of MLP samples.
     *
     * @return the number of MLP samples
     */
    public int getNumMlpSamples() {
        return numMlpSamples;
    }

    /**
     * Set the number of MLP samples.
     *
     * @param numMlpSamples the number of MLP samples
     */
    public void setNumMlpSamples(int numMlpSamples) {
        this.numMlpSamples = numMlpSamples;
    }

    /**
     * Get the MLP sum.
     *
     * @return the MLP sum
     */
    public int getMlpSum() {
        return mlpSum;
    }

    /**
     * Set the MLP sum.
     *
     * @param mlpSum the MLP sum
     */
    public void setMlpSum(int mlpSum) {
        this.mlpSum = mlpSum;
    }

    /**
     * Get the time in cycles spent servicing the access.
     *
     * @return the time in cycles spent servicing the access
     */
    public int getNumCycles() {
        return (int) (this.endCycle - this.beginCycle);
    }

    /**
     * Get the average MLP.
     *
     * @return the average MLP
     */
    public double getAverageMlp() {
        return (double) mlpSum / (double) numMlpSamples;
    }

    /**
     * Get the aggregated MLP cost.
     *
     * @return the aggregated MLP cost
     */
    public double getMlpCost() {
        return (double) mlpSum / numMlpSamples;
    }
}
