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
package archimulator.sim.uncore.cache;

import java.io.Serializable;

public class CacheHit<StateT extends Serializable> extends CacheAccess<StateT> {
    public CacheHit(EvictableCache<StateT> cache, CacheReference reference, int way) {
        super(cache, reference, way);
    }

    @Override
    public boolean isHitInCache() {
        return true;
    }

    @Override
    public boolean isEviction() {
        return false;
    }

    @Override
    public CacheAccess<StateT> commit() {
        this.getCache().getEvictionPolicy().handlePromotionOnHit(this);
        return super.commit();
    }
}
