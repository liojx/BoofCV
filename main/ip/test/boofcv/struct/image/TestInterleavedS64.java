/*
 * Copyright (c) 2011-2016, Peter Abeles. All Rights Reserved.
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

package boofcv.struct.image;

/**
 * @author Peter Abeles
 */
public class TestInterleavedS64 extends StandardImageInterleavedTests<InterleavedS64> {

	@Override
	public InterleavedS64 createImage(int width, int height, int numBands) {
		return new InterleavedS64(width, height, numBands);
	}

	@Override
	public InterleavedS64 createImage() {
		return new InterleavedS64();
	}

	@Override
	public Number randomNumber() {
		return (long)(rand.nextInt(3000)-1500);
	}

	@Override
	public Number getNumber( Number value) {
		return value.longValue();
	}
}
