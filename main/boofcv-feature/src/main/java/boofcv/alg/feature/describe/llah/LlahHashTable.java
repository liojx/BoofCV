/*
 * Copyright (c) 2011-2020, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.feature.describe.llah;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Objects;

import static boofcv.misc.BoofMiscOps.checkTrue;

/**
 * Hash table that stores LLAH features.
 *
 * @author Peter Abeles
 */
public class LlahHashTable {

	// Stores features using their hashcode
	TIntObjectHashMap<LlahFeature> map = new TIntObjectHashMap<>();

	/**
	 * Adds the feature to the map. If there's a collision it's added as the last element in the list
	 *
	 * @param feature Feature to be added
	 */
	public void add( LlahFeature feature ) {
		checkTrue(null==feature.next);
		LlahFeature f = map.get(feature.hashCode);
		if (f != null) {
			while (f.next != null) {
				f = f.next;
			}
			f.next = feature;
		} else {
			map.put(feature.hashCode, feature);
		}
		feature.next = null; // just to be safe
	}

	/**
	 * Removes the feature from the hashtable
	 *
	 * @return true if it was removed or false if it failed
	 */
	public boolean remove( LlahFeature feature ) {
		LlahFeature f = map.get(feature.hashCode);
		f = Objects.requireNonNull(f);

		// The feature to remove is the first feature in the linked list
		if (f == feature) {
			// If it's also the last in the list, remove this hash entirely
			if (f.next == null) {
				map.remove(f.hashCode);
				return true;
			}
			// Otherwise the next feature is now the first
			map.put(f.hashCode, f.next);
			return true;
		}
		// Otherwise search through list and remove the passed in feature
		LlahFeature prev = f;
		while (f != null) {
			if (f == feature) {
				prev.next = f.next;
				return true;
			}
			f = f.next;
		}
		return false;
	}

	/**
	 * Looks up a feature which has the same hash and matching invariants
	 *
	 * @param hashCode Feature's hashcode
	 * @return The found matching feature or null if there is no match
	 */
	public LlahFeature lookup( int hashCode ) {
		return map.get(hashCode);
	}

	/**
	 * Resets to original state
	 */
	public void reset() {
		map.clear();
	}
}
