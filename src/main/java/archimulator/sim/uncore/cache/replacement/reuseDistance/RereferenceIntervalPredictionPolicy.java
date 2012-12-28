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
package archimulator.sim.uncore.cache.replacement.reuseDistance;

import archimulator.sim.uncore.MemoryHierarchyAccess;
import archimulator.sim.uncore.cache.*;
import archimulator.sim.uncore.cache.replacement.CacheReplacementPolicy;
import net.pickapack.util.ValueProvider;
import net.pickapack.util.ValueProviderFactory;

import java.io.Serializable;

/**
 * Rereference interval prediction policy.
 *
 * @author Min Cai
 * @param <StateT> the state type of the parent evictable cache
 */
public class RereferenceIntervalPredictionPolicy<StateT extends Serializable> extends CacheReplacementPolicy<StateT> {
    private int predictedRereferenceIntervalMaxValue;
    private DynamicInsertionPolicy insertionPolicy;
    private Cache<Boolean> mirrorCache;

    public RereferenceIntervalPredictionPolicy(EvictableCache<StateT> cache) {
        super(cache);

        this.predictedRereferenceIntervalMaxValue = 3;

        this.mirrorCache = new Cache<Boolean>(cache, getCache().getName() + ".rereferenceIntervalPredictionEvictionPolicy.mirrorCache", cache.getGeometry(), new ValueProviderFactory<Boolean, ValueProvider<Boolean>>() {
            @Override
            public ValueProvider<Boolean> createValueProvider(Object... args) {
                return new BooleanValueProvider();
            }
        });

        this.insertionPolicy = new archimulator.sim.uncore.cache.replacement.reuseDistance.DynamicInsertionPolicy(cache, 4, ((1 << 10) - 1), 8); //TODO: parameter passing
    }

    @Override
    public CacheAccess<StateT> handleReplacement(MemoryHierarchyAccess access, int set, int tag) {
        do {
            /* Search for victim whose predicted rereference interval value is furthest in future */
            for (int way = 0; way < this.getCache().getAssociativity(); way++) {
                CacheLine<Boolean> mirrorLine = this.mirrorCache.getLine(set, way);
                BooleanValueProvider stateProvider = (BooleanValueProvider) mirrorLine.getStateProvider();
                if (stateProvider.predictedRereferenceInterval == this.predictedRereferenceIntervalMaxValue) {
                    return new CacheAccess<StateT>(this.getCache(), access, set, way, tag);
                }
            }

            /* If victim is not found, then move all rereference prediction values into future and then repeat the search again */
            for (int way = 0; way < this.getCache().getAssociativity(); way++) {
                CacheLine<Boolean> mirrorLine = this.mirrorCache.getLine(set, way);
                BooleanValueProvider stateProvider = (BooleanValueProvider) mirrorLine.getStateProvider();
                stateProvider.predictedRereferenceInterval++;
            }
        } while (true);
    }

    @Override
    public void handlePromotionOnHit(MemoryHierarchyAccess access, int set, int way) {
        // Set the line's predicted rereference interval value to near-immediate (0)
        CacheLine<Boolean> mirrorLine = this.mirrorCache.getLine(set, way);
        BooleanValueProvider stateProvider = (BooleanValueProvider) mirrorLine.getStateProvider();
        stateProvider.predictedRereferenceInterval = 0;
    }

    @Override
    public void handleInsertionOnMiss(MemoryHierarchyAccess access, int set, int way) {
        this.insertionPolicy.recordMiss(set);

        if (this.insertionPolicy.shouldDoNormalFill(access.getThread().getId(), set)) {
            CacheLine<Boolean> mirrorLine = this.mirrorCache.getLine(set, way);
            BooleanValueProvider stateProvider = (BooleanValueProvider) mirrorLine.getStateProvider();
            stateProvider.predictedRereferenceInterval = this.predictedRereferenceIntervalMaxValue - 1;
        }
    }

    private class BooleanValueProvider implements ValueProvider<Boolean> {
        private boolean state;
        private int predictedRereferenceInterval;

        public BooleanValueProvider() {
            this.state = true;
            this.predictedRereferenceInterval = predictedRereferenceIntervalMaxValue;
        }

        @Override
        public Boolean get() {
            return state;
        }

        @Override
        public Boolean getInitialValue() {
            return true;
        }
    }
}
