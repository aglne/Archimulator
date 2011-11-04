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
package archimulator.mem.cache.eviction.ext;

import archimulator.mem.cache.*;
import archimulator.mem.cache.eviction.EvictionPolicy;
import archimulator.mem.cache.eviction.EvictionPolicyFactory;
import archimulator.util.action.Function2;

import java.io.Serializable;

public class RereferenceIntervalPredictionEvictionPolicy<StateT extends Serializable, LineT extends CacheLine<StateT>> extends EvictionPolicy<StateT, LineT> {
    private int predictedRereferenceIntervalMaxValue;
    private DynamicInsertionPolicy insertionPolicy;
    private MirrorCache mirrorCache;

    public RereferenceIntervalPredictionEvictionPolicy(EvictableCache<StateT, LineT> cache) {
        super(cache);

        this.predictedRereferenceIntervalMaxValue = 3;

        this.mirrorCache = new MirrorCache();

        this.insertionPolicy = new DynamicInsertionPolicy(cache, 4, ((1 << 10) - 1), 8); //TODO: parameter passing
    }

    @Override
    public CacheMiss<StateT, LineT> handleReplacement(CacheReference reference) {
        int set = reference.getSet();

        do {
            /* Search for victim whose predicted rereference interval value is furthest in future */
            for (int way = 0; way < this.getCache().getAssociativity(); way++) {
                if (this.mirrorCache.getLine(set, way).predictedRereferenceInterval == this.predictedRereferenceIntervalMaxValue) {
                    return new CacheMiss<StateT, LineT>(this.getCache(), reference, way);
                }
            }

            /* If victim is not found, then move all rereference prediction values into future and then repeat the search again */
            for (int way = 0; way < this.getCache().getAssociativity(); way++) {
                this.mirrorCache.getLine(set, way).predictedRereferenceInterval++;
            }
        } while (true);
    }

    @Override
    public void handlePromotionOnHit(CacheHit<StateT, LineT> hit) {
        // Set the line's predicted rereference interval value to near-immediate (0)
        this.mirrorCache.getLine(hit.getReference().getSet(), hit.getWay()).predictedRereferenceInterval = 0;
    }

    @Override
    public void handleInsertionOnMiss(CacheMiss<StateT, LineT> miss) {
        this.insertionPolicy.recordMiss(miss.getReference().getSet());

        if (this.insertionPolicy.shouldDoNormalFill(miss.getReference().getAccess().getThread().getId(), miss.getReference().getSet())) {
            this.mirrorCache.getLine(miss.getReference().getSet(), miss.getWay()).predictedRereferenceInterval = this.predictedRereferenceIntervalMaxValue - 1;
        }
    }

    private class MirrorCacheLine extends CacheLine<Boolean> {
        private int predictedRereferenceInterval;

        private MirrorCacheLine(int set, int way) {
            super(set, way, true);

            this.predictedRereferenceInterval = predictedRereferenceIntervalMaxValue;
        }
    }

    private class MirrorCache extends Cache<Boolean, MirrorCacheLine> {
        private MirrorCache() {
            super(getCache(), getCache().getName() + ".rereferenceIntervalPredictionEvictionPolicy.mirrorCache", getCache().getGeometry(), new Function2<Integer, Integer, MirrorCacheLine>() {
                public MirrorCacheLine apply(Integer set, Integer way) {
                    return new MirrorCacheLine(set, way);
                }
            });
        }
    }

    public static final EvictionPolicyFactory FACTORY = new EvictionPolicyFactory() {
        public String getName() {
            return "REREFERENCE_INTERVAL_PREDICTION";
        }

        public <StateT extends Serializable, LineT extends CacheLine<StateT>> EvictionPolicy<StateT, LineT> create(EvictableCache<StateT, LineT> cache) {
            return new RereferenceIntervalPredictionEvictionPolicy<StateT, LineT>(cache);
        }
    };
}
