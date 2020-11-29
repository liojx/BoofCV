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

package boofcv.alg.segmentation.cc;

import boofcv.struct.image.ImageBase;

/**
 * Connected component based speckle filler
 *
 * @author Peter Abeles
 */
public interface ConnectedSpeckleFiller<T extends ImageBase<T>> {
	/**
	 * Finds non-smooth regions and fills them in with the fill value. Uses 4-connect rule.
	 *
	 * @param image (Input, Output) Image which is searched for speckle noise which is then filled in
	 * @param maximumArea (Input) All regions with this number of pixels or fewer will be filled in.
	 * @param similarTol (Input) Two pixels are connected if their different in value is &le; than this.
	 * @param fillValue (Input) The value that small regions are filled in with.
	 */
	void process( T image , int maximumArea, float similarTol, float fillValue );

	/** Returns the number of filled in regions */
	int getTotalFilled();
}
