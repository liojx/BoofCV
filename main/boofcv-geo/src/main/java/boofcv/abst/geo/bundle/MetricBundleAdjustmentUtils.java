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

package boofcv.abst.geo.bundle;

import boofcv.abst.geo.TriangulateNViewsMetricH;
import boofcv.factory.geo.FactoryMultiView;
import boofcv.misc.ConfigConverge;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;

/**
 * Contains everything you need to do metric bundle adjustment in one location
 *
 * @author Peter Abeles
 */
public class MetricBundleAdjustmentUtils {
	/** Configures convergence criteria for SBA */
	public final @Getter ConfigConverge configConverge = new ConfigConverge(1e-5, 1e-5, 30);
	/** Toggles on and off scaling parameters */
	public @Getter @Setter boolean configScale = false;

	/** Optional second pass where outliers observations. Fraction specifies that the best X fraction are kept. */
	public double keepFraction = 1.0;

	/** The estimated scene structure. This the final estimated scene state */
	public final @Getter SceneStructureMetric structure = new SceneStructureMetric(true);
	public final @Getter SceneObservations observations = new SceneObservations();
	public @Getter @Setter BundleAdjustment<SceneStructureMetric> sba = FactoryMultiView.bundleSparseMetric(null);
	public @Getter @Setter TriangulateNViewsMetricH triangulator = FactoryMultiView.triangulateNViewMetricH(null);
	public @Getter ScaleSceneStructure scaler = new ScaleSceneStructure();

	/**
	 * Uses the already configured structure and observations to perform bundle adjustment
	 *
	 * @return true if successful
	 */
	public boolean process( @Nullable PrintStream verbose ) {
		if (configConverge.maxIterations == 0)
			return true;
		if (configScale)
			scaler.applyScale(structure, observations);
		sba.configure(configConverge.ftol, configConverge.gtol, configConverge.maxIterations);

		sba.setParameters(structure, observations);
		if (verbose != null)
			verbose.println("SBA BEFORE        average error=" + (Math.sqrt(sba.getFitScore())/observations.getObservationCount()));
		if (!sba.optimize(structure))
			return false;
		if (verbose != null)
			verbose.println("SBA AFTER         average error=" + (Math.sqrt(sba.getFitScore())/observations.getObservationCount()));

		if (keepFraction < 1.0) {
			// don't prune views since they might be required
			prune(keepFraction, -1, 1);
			sba.setParameters(structure, observations);
			if (verbose != null)
				verbose.println("SBA PRUNED-BEFORE average error=" + (Math.sqrt(sba.getFitScore())/observations.getObservationCount()));
			if (!sba.optimize(structure))
				return false;
			if (verbose != null)
				verbose.println("SBA PRUNED-AFTER  average error=" + (Math.sqrt(sba.getFitScore())/observations.getObservationCount()));
		}

		if (configScale)
			scaler.undoScale(structure, observations);
		return true;
	}

	/**
	 * Prunes outliers and views/points with too few points/observations
	 */
	public void prune( double keepFraction, int pruneViews, int prunePoints ) {
		prunePoints = Math.max(1, prunePoints);

		PruneStructureFromSceneMetric pruner = new PruneStructureFromSceneMetric(structure, observations);
		pruner.pruneObservationsByErrorRank(keepFraction);
		if (pruneViews > 0) {
			if (pruner.pruneViews(pruneViews))
				pruner.pruneUnusedMotions();
		}
		pruner.prunePoints(prunePoints);
	}

	/**
	 * Prints the number of different data structures in the scene
	 */
	public void printCounts( PrintStream out ) {
		out.println("Points=" + structure.points.size);
		out.println("Views=" + structure.views.size);
		out.println("Cameras=" + structure.cameras.size);
		for (int viewIdx = 0; viewIdx < observations.views.size; viewIdx++) {
			out.println("view[" + viewIdx + "].observations.size=" + observations.views.get(viewIdx).size());
		}
	}
}
