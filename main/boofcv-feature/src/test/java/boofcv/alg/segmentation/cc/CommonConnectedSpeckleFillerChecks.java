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

import boofcv.BoofTesting;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.struct.image.GrayF32;
import boofcv.testing.BoofStandardJUnit;
import org.junit.jupiter.api.Test;

/**
 * @author Peter Abeles
 */
public abstract class CommonConnectedSpeckleFillerChecks extends BoofStandardJUnit {
	float valueStep = 0.6f;

	GrayF32 input = new GrayF32(1, 1);
	GrayF32 expected = new GrayF32(1, 1);

	// @formatter:off
	String case0 =
			"200001\n" +
			"000220\n" +
			"002222\n" +
			"002220\n" +
			"000020\n" +
			"000000";

	String expected0 =
			"300001\n" +
			"000330\n" +
			"003333\n" +
			"003330\n" +
			"000030\n" +
			"000000";

	// The 1 prevents the top cluster from being pruned as it connects it to the background
	String case1 =
			"222000\n" +
			"001000\n" +
			"000220\n" +
			"002000\n" +
			"010010\n" +
			"000010";

	String expected1 =
			"222000\n" +
			"001000\n" +
			"000330\n" +
			"003000\n" +
			"010010\n" +
			"000010";

	// Checks to see if speckle on the bottom is handled and max region size
	String case2 =
			"222222\n" +
			"022222\n" +
			"002222\n" +
			"002000\n" +
			"010022\n" +
			"202002";

	String expected2 =
			"222222\n" +
			"022222\n" +
			"002222\n" +
			"002000\n" +
			"010033\n" +
			"303003";

	String case3 =
			"101010\n" +
			"202020\n" +
			"303030\n" +
			"212020\n" +
			"001110\n" +
			"000000";
	// @formatter:on

	public abstract ConnectedSpeckleFiller<GrayF32> createAlg();

	@Test void process_case0() {
		// When the fill value is zero it will be all zeros
		set(case0, input);
		ImageMiscOps.fill(expected, 0);
		ConnectedSpeckleFiller<GrayF32> alg = createAlg();
		alg.process(input, 20, 1.0f, 0.0f);
		BoofTesting.assertEquals(expected, input, 1e-4);

		// There will be no change when the fill value is equal to the max value
		set(case0, input);
		expected.setTo(input);
		alg.process(input, 20, 1.0f, valueStep*2);
		BoofTesting.assertEquals(expected, input, 1e-4);

		// More interesting when set to a value that's different
		set(case0, input);
		set(expected0, expected);
		alg.process(input, 20, 1.0f, valueStep*3);
		BoofTesting.assertEquals(expected, input, 1e-4);
	}

	@Test void process_case1() {
		set(case1, input);
		set(expected1, expected);
		ConnectedSpeckleFiller<GrayF32> alg = createAlg();
		alg.process(input, 20, 1.0f, valueStep*3);
		BoofTesting.assertEquals(expected, input, 1e-4);
	}

	@Test void process_case2() {
		set(case2, input);
		set(expected2, expected);
		ConnectedSpeckleFiller<GrayF32> alg = createAlg();
		alg.process(input, 5, 1.0f, valueStep*3);
		BoofTesting.assertEquals(expected, input, 1e-4);
	}

	@Test void process_case3() {
		set(case3, input);
		set(case3, expected);
		ConnectedSpeckleFiller<GrayF32> alg = createAlg();
		alg.process(input, 15, 1.0f, valueStep*4);
		// there should be no change since everything is connect to one segment
		BoofTesting.assertEquals(expected, input, 1e-4);
	}

	private void set( String encoded, GrayF32 image ) {
		String[] lines = encoded.split("\n");

		image.reshape(lines[0].length(), lines.length);

		for (int y = 0; y < lines.length; y++) {
			String line = lines[y];
			for (int x = 0; x < line.length(); x++) {
				image.set(x, y, Integer.parseInt("" + line.charAt(x))*valueStep);
			}
		}
	}
}
