/*
 * Copyright (c) 2020, Peter Abeles. All Rights Reserved.
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

import gnu.trove.impl.Constants;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.ddogleg.struct.DogArray;
import org.jetbrains.annotations.Nullable;

/**
 * LLAH Hash table that has been specialized for Uchiya use case.
 *
 * @author Peter Abeles
 */
public class UchiyaHashTable {
	// Stores features using their hashcode
	TIntObjectMap<HashDocuments> map = new TIntObjectHashMap<>();

	// Used for recycling documents
	DogArray<HashDocuments> storageDocs = new DogArray<>(HashDocuments::new,HashDocuments::reset);

	/**
	 * Adds the feature to the map. If there's a collision it's added as the last element in the list
	 * @param feature Feature to be added
	 */
	public void add( LlahFeature feature ) {
		HashDocuments docs = map.get(feature.hashCode);
		if (docs == null) {
			docs = storageDocs.grow();
			map.put(feature.hashCode, docs);
		}

		int index = docs.documentToIndex.get(feature.documentID);
		DocumentHits doc;
		if (index==-1) {
			docs.documentToIndex.put(feature.documentID, docs.votes.size());
			doc = docs.votes.grow();
			doc.total = 0;
			doc.documentID = feature.documentID;
		} else {
			doc = docs.votes.get(index);
		}

		doc.total++;
	}

	/**
	 * Looks up all the documents associated with this hash code
	 */
	public @Nullable HashDocuments lookup( int hashcode ) {
		return map.get(hashcode);
	}

	/**
	 * Resets to original state
	 */
	public void reset() {
		map.clear();
		storageDocs.reset();
	}

	/**
	 * Contains the votes for all documents associated with a specific hashcode.
	 */
	public static class HashDocuments {
		// votes for a specific document
		public DogArray<DocumentHits> votes = new DogArray<>(DocumentHits::new, DocumentHits::reset);
		// given a document ID return the index of the document in votes.
		// This constructor sets the no entry values to -1.
		public TIntIntMap documentToIndex = new TIntIntHashMap(10, Constants.DEFAULT_LOAD_FACTOR,-1,-1);

		public void reset() {
			votes.reset();
			documentToIndex.clear();
		}
	}

	/**
	 * Contains the number of times a hash code appears for a document
	 */
	public static class DocumentHits {
		// Which document
		public int documentID;
		// number of hits/occurrences of a hash code for this document
		public int total;

		public void reset() {
			documentID = -1;
			total = -1;
		}
	}
}
