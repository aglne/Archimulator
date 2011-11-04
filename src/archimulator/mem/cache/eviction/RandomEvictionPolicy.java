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
package archimulator.mem.cache.eviction;

import archimulator.mem.cache.*;

import java.io.Serializable;
import java.util.Random;

public class RandomEvictionPolicy<StateT extends Serializable, LineT extends CacheLine<StateT>> extends EvictionPolicy<StateT, LineT> {
    private Random random;

    public RandomEvictionPolicy(EvictableCache<StateT, LineT> cache) {
        super(cache);

        this.random = new Random(13);
    }

    @Override
    public CacheMiss<StateT, LineT> handleReplacement(CacheReference reference) {
        return new CacheMiss<StateT, LineT>(this.getCache(), reference, this.random.nextInt(this.getCache().getAssociativity()));
    }

    @Override
    public void handlePromotionOnHit(CacheHit<StateT, LineT> hit) {
    }

    @Override
    public void handleInsertionOnMiss(CacheMiss<StateT, LineT> miss) {
    }

    public static final EvictionPolicyFactory FACTORY = new EvictionPolicyFactory() {
        public String getName() {
            return "RANDOM";
        }

        public <StateT extends Serializable, LineT extends CacheLine<StateT>> EvictionPolicy<StateT, LineT> create(EvictableCache<StateT, LineT> cache) {
            return new RandomEvictionPolicy<StateT, LineT>(cache);
        }
    };
}
