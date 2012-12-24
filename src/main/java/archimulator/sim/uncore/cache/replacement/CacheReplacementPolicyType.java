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
package archimulator.sim.uncore.cache.replacement;

/**
 * Cache replacement policy type.
 *
 * @author Min Cai
 */
public enum CacheReplacementPolicyType {
    /**
     * Least recently used (LRU).
     */
    LRU,

    /**
     * Least frequently used (LFU).
     */
    LFU,

    /**
     * Random.
     */
    RANDOM,

    /**
     * Helper thread aware least recently used (LRU).
     */
    HELPER_THREAD_AWARE_LRU,

    /**
     * Helper thread interval aware least recently used (LRU).
     */
    HELPER_THREAD_INTERVAL_AWARE_LRU,

    /**
     * Helper thread request breakdown enhanced least recently used (LRU).
     */
    HELPER_THREAD_AWARE_BREAKDOWN_LRU,

    /**
     * Reuse distance prediction.
     */
    REUSE_DISTANCE_PREDICTION,

    /**
     * Rereference interval prediction.
     */
    REREFERENCE_INTERVAL_PREDICTION
}
