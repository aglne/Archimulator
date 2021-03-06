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
package archimulator.uncore.mlp;

import archimulator.uncore.MemoryHierarchyAccess;

/**
 * Pending L2 cache hit.
 *
 * @author Min Cai
 */
public class PendingL2Hit {
    private MemoryHierarchyAccess access;
    private long beginCycle;
    private long endCycle;
    private double mlpCost;
    private int stackDistance;
    private int numCommittedInstructionsSinceAccess;
    private int numCyclesElapsedSinceAccess;

    /**
     * Create a pending L2 cache hit.
     *
     * @param access the memory hierarchy access
     * @param beginCycle the time in cycles when the access begins
     */
    public PendingL2Hit(MemoryHierarchyAccess access, long beginCycle) {
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
     * Get the stack distance.
     *
     * @return the stack distance
     */
    public int getStackDistance() {
        return stackDistance;
    }

    /**
     * Set the stack distance.
     *
     * @param stackDistance the stack distance
     */
    public void setStackDistance(int stackDistance) {
        this.stackDistance = stackDistance;
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
     * Get the MLP-cost.
     *
     * @return the MLP-cost
     */
    public double getMlpCost() {
        return mlpCost;
    }

    /**
     * Set the MLP-cost.
     *
     * @param mlpCost the MLP-cost
     */
    public void setMlpCost(double mlpCost) {
        this.mlpCost = mlpCost;
    }

    /**
     * Get the number of committed dynamic instructions since the access.
     *
     * @return the number of committed dynamic instructions since the access
     */
    public int getNumCommittedInstructionsSinceAccess() {
        return numCommittedInstructionsSinceAccess;
    }

    /**
     * Increment the number of committed dynamic instructions since the access.
     */
    public void incrementNumCommittedInstructionsSinceAccess() {
        this.numCommittedInstructionsSinceAccess++;
    }

    /**
     * Get the number of cycles since the access.
     *
     * @return the number of cycles since the access
     */
    public int getNumCyclesElapsedSinceAccess() {
        return numCyclesElapsedSinceAccess;
    }

    /**
     * Increment the number of cycles elapsed since the access.
     */
    public void incrementNumCyclesElapsedSinceAccess() {
        this.numCyclesElapsedSinceAccess++;
    }

    @Override
    public String toString() {
        return String.format("PendingL2Hit{access=%s, beginCycle=%d, endCycle=%d, mlpCost=%s, stackDistance=%d, numCommittedInstructionsSinceAccess=%d, numCyclesElapsedSinceAccess=%d}", access, beginCycle, endCycle, mlpCost, stackDistance, numCommittedInstructionsSinceAccess, numCyclesElapsedSinceAccess);
    }
}
