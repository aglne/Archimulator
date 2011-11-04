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
package archimulator.core.bpred;

public class CombinedBranchPredictorConfig extends BranchPredictorConfig {
    private int bimodSize;
    private int l1Size;
    private int l2Size;
    private int metaSize;
    private int shiftWidth;
    private boolean xor;
    private int btbSets;
    private int btbAssoc;
    private int retStackSize;

    public CombinedBranchPredictorConfig() {
        this(2048, 1, 1024, 1024, 8, false, 512, 4, 8);
    }

    public CombinedBranchPredictorConfig(int bimodSize, int l1Size, int l2Size, int metaSize, int shiftWidth, boolean xor, int btbSets, int btbAssoc, int retStackSize) {
        super(BranchPredictorType.COMBINED);

        this.bimodSize = bimodSize;
        this.l1Size = l1Size;
        this.l2Size = l2Size;
        this.metaSize = metaSize;
        this.shiftWidth = shiftWidth;
        this.xor = xor;
        this.btbSets = btbSets;
        this.btbAssoc = btbAssoc;
        this.retStackSize = retStackSize;
    }

    public int getBimodSize() {
        return bimodSize;
    }

    public int getL1Size() {
        return l1Size;
    }

    public int getL2Size() {
        return l2Size;
    }

    public int getMetaSize() {
        return metaSize;
    }

    public int getShiftWidth() {
        return shiftWidth;
    }

    public boolean isXor() {
        return xor;
    }

    public int getBtbSets() {
        return btbSets;
    }

    public int getBtbAssoc() {
        return btbAssoc;
    }

    public int getRetStackSize() {
        return retStackSize;
    }
}
