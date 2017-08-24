/*
 * Copyright (c) 2011-2017, Peter Abeles. All Rights Reserved.
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

package boofcv.abst.fiducial.calib;

import boofcv.abst.geo.Estimate1ofEpipolar;
import boofcv.abst.geo.calibration.DetectorFiducialCalibration;
import boofcv.alg.distort.DistortImageOps;
import boofcv.alg.distort.PointToPixelTransform_F32;
import boofcv.alg.distort.PointTransformHomography_F32;
import boofcv.alg.distort.PointTransformHomography_F64;
import boofcv.alg.fiducial.calib.RenderSimulatedFisheye;
import boofcv.alg.geo.calibration.CalibrationObservation;
import boofcv.alg.interpolate.InterpolationType;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.core.image.border.BorderType;
import boofcv.factory.geo.FactoryMultiView;
import boofcv.gui.feature.VisualizeFeatures;
import boofcv.gui.image.ShowImages;
import boofcv.io.calibration.CalibrationIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.calib.CameraPinholeRadial;
import boofcv.struct.calib.CameraUniversalOmni;
import boofcv.struct.distort.PixelTransform2_F32;
import boofcv.struct.distort.Point2Transform2_F32;
import boofcv.struct.distort.Point2Transform2_F64;
import boofcv.struct.geo.AssociatedPair;
import boofcv.struct.image.GrayF32;
import georegression.geometry.ConvertRotation3D_F64;
import georegression.struct.EulerType;
import georegression.struct.point.Point2D_F64;
import georegression.struct.se.Se3_F64;
import org.ejml.data.DMatrixRMaj;
import org.ejml.data.FMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.ops.ConvertMatrixData;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Abeles
 */
public abstract class GenericPlanarCalibrationDetectorChecks {

	int width = 300,height= 300;

	// TODO Remove and new new renderer
	GrayF32 original;
	GrayF32 distorted;
	List<CalibrationObservation> solutions = new ArrayList<>();

	List targetLayouts = new ArrayList();

	Point2Transform2_F32 d2o;
	Point2Transform2_F64 o2d;

	double fisheyeMatchTol = 3; // how close a pixel needs to come to be considered a match

	/**
	 * Renders an image of the calibration target.
	 * @param layout (Optional)
	 * @param image Storage for rendered calibration target This should be just the calibration target
	 */
	public abstract void renderTarget( Object layout ,
									   double targetWidth ,
									   GrayF32 image ,
									   List<Point2D_F64> points2D );

	public abstract void renderTarget(GrayF32 original , List<CalibrationObservation> solutions );

	public abstract DetectorFiducialCalibration createDetector();

	@Before
	public void setup() {
		original = new GrayF32(width,height);
		distorted = new GrayF32(width, height);
		renderTarget(original, solutions);
	}

	/**
	 * See if it can detect targets distorted by fisheye lens. Entire target is always seen
	 */
	@Test
	public void fisheye_fullview() {
		double targetWidth = 0.3;

		CameraUniversalOmni model = CalibrationIO.load(getClass().getResource("fisheye.yaml"));
		RenderSimulatedFisheye simulator = new RenderSimulatedFisheye();
		simulator.setCamera(model);

		DetectorFiducialCalibration detector = createDetector();

		List<Point2D_F64> locations2D = new ArrayList<>();
		GrayF32 pattern = new GrayF32(1,1);
		for (int i = 0; i < targetLayouts.size(); i++) {
			renderTarget(targetLayouts.get(i),targetWidth,pattern,locations2D);

			simulator.resetScene();
			Se3_F64 markerToWorld = new Se3_F64();
			simulator.addTarget(markerToWorld,targetWidth,pattern);

			// up close exploding - center
			markerToWorld.T.set(0,0,0.08);
			checkRenderedResults(detector,simulator,locations2D);

			// up close exploding - left
			markerToWorld.T.set(0.1,0,0.08);
			checkRenderedResults(detector,simulator,locations2D);

			markerToWorld.T.set(0.25,0,0.2);
			checkRenderedResults(detector,simulator,locations2D);

			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0,-0.2,0,markerToWorld.getR());
			checkRenderedResults(detector,simulator,locations2D);

			markerToWorld.T.set(0.3,0,0.05);
			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0,-1,0,markerToWorld.getR());
			checkRenderedResults(detector,simulator,locations2D);

			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0,-1,0.5,markerToWorld.getR());
			simulator.render();
			checkRenderedResults(detector,simulator,locations2D);

			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0,-1,0.7,markerToWorld.getR());
			simulator.render();
			checkRenderedResults(detector,simulator,locations2D);
		}
	}

	private void checkRenderedResults(DetectorFiducialCalibration detector,
									  RenderSimulatedFisheye simulator ,
									  List<Point2D_F64> locations2D )
	{
		simulator.render();

		if( !detector.process(simulator.getOutput())) {
			visualize(simulator, locations2D, null);
			fail("Detection failed");
		}

		CalibrationObservation found = detector.getDetectedPoints();

		assertEquals(locations2D.size(),found.size());

		Point2D_F64 truth = new Point2D_F64();

		for (int i = 0; i < locations2D.size(); i++) {
			Point2D_F64 p = locations2D.get(i);
			simulator.computePixel( 0, p.x , p.y , truth);

			// TODO ensure that the order is correct. Kinda a pain since some targets have symmetry...
			double bestDistance = Double.MAX_VALUE;
			for (int j = 0; j < found.size(); j++) {
				double distance = found.get(j).distance(truth);
				if( distance < bestDistance ) {
					bestDistance = distance;
				}
			}
			if( bestDistance > fisheyeMatchTol ) {
				visualize(simulator, locations2D, found);
				fail("Didn't find a match: best distance "+bestDistance);
			}
		}
	}

	private void visualize(RenderSimulatedFisheye simulator, List<Point2D_F64> locations2D, CalibrationObservation found) {
		Point2D_F64 p;GrayF32 output = simulator.getOutput();
		BufferedImage buff = new BufferedImage(output.width,output.height,BufferedImage.TYPE_INT_RGB);
		ConvertBufferedImage.convertTo(simulator.getOutput(),buff,true);

		Graphics2D g2 = buff.createGraphics();
		for (int j = 0; found != null && j < found.size(); j++) {
			Point2D_F64 f = found.get(j);
			VisualizeFeatures.drawPoint(g2,f.x,f.y,4,Color.RED,false);
		}
		Point2D_F64 truth = new Point2D_F64();

		for (int j = 0; j < locations2D.size(); j++) {
			p = locations2D.get(j);
			simulator.computePixel( 0, p.x , p.y , truth);
			VisualizeFeatures.drawPoint(g2,truth.x,truth.y,4,Color.GREEN,false);
		}

		ShowImages.showWindow(buff,"Foo",true);
		BoofMiscOps.sleep(3000);
	}

	/**
	 * Simulated scene using a pinhole camera model with radial distortion. Entire target is visible
	 */
	@Test
	public void pinhole_radial_fullview() {
		double targetWidth = 0.3;


		CameraPinholeRadial model = CalibrationIO.load(getClass().getResource("pinhole_radial.yaml"));
		RenderSimulatedFisheye simulator = new RenderSimulatedFisheye();
		simulator.setCamera(model);

		DetectorFiducialCalibration detector = createDetector();

		List<Point2D_F64> locations2D = new ArrayList<>();
		GrayF32 pattern = new GrayF32(1,1);
		for (int i = 0; i < targetLayouts.size(); i++) {
			renderTarget(targetLayouts.get(i), targetWidth, pattern, locations2D);

			simulator.resetScene();
			Se3_F64 markerToWorld = new Se3_F64();
			simulator.addTarget(markerToWorld, targetWidth, pattern);

			// up close exploding - center
			markerToWorld.T.set(0, 0, 0.5);
			checkRenderedResults(detector, simulator, locations2D);

			// farther away centered
			markerToWorld.T.set(0, 0, 1);
			checkRenderedResults(detector, simulator, locations2D);

			markerToWorld.T.set(-0.33, 0, 1);
			checkRenderedResults(detector, simulator, locations2D);

			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0,-1,0,markerToWorld.getR());
			checkRenderedResults(detector, simulator, locations2D);

			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0,-1,0.8,markerToWorld.getR());
			checkRenderedResults(detector, simulator, locations2D);

			markerToWorld.T.set(-0.33, 0.33, 1);
			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0,-1,0.8,markerToWorld.getR());
			checkRenderedResults(detector, simulator, locations2D);

			markerToWorld.T.set(0, -0.25, 1);
			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0.8,-1,0.8,markerToWorld.getR());
			checkRenderedResults(detector, simulator, locations2D);

			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0.8,-1,1.8,markerToWorld.getR());
			checkRenderedResults(detector, simulator, locations2D);

			markerToWorld.T.set(0, -0.15, 1);
			ConvertRotation3D_F64.eulerToMatrix(EulerType.XYZ,0.2,-1,2.4,markerToWorld.getR());
			checkRenderedResults(detector, simulator, locations2D);
		}
	}
	/**
	 * Nothing was detected.  make sure it doesn't return null.
	 */
	@Test
	public void checkDetectionsNotNull() {
		DetectorFiducialCalibration detector = createDetector();

		detector.process(original.createSameShape());

		assertTrue( detector.getDetectedPoints() != null );
		assertTrue( detector.getDetectedPoints().size() == 0 );
	}

	/**
	 * First call something was detected, second call nothing was detected.  it should return an empty list
	 */
	@Test
	public void checkDetectionsResetOnFailure() {
		DetectorFiducialCalibration detector = createDetector();

		detector.process(original);
		assertTrue( detector.getDetectedPoints().size() > 0 );

		detector.process(original.createSameShape());

		assertTrue( detector.getDetectedPoints() != null );
		assertTrue( detector.getDetectedPoints().size() == 0 );
	}

	/**
	 * Makes sure origin in the target's physical center.  This is done by seeing that most extreme
	 * points are all equally distant.  Can't use the mean since the target might not evenly distributed.
	 *
	 * Should this really be a requirement?  There is some mathematical justification for it and make sense
	 * when using it as a fiducial.
	 */
	@Test
	public void targetIsCentered() {
		List<Point2D_F64> layout = createDetector().getLayout();

		double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE;
		double minY=Double.MAX_VALUE,maxY=-Double.MAX_VALUE;

		for( Point2D_F64 p : layout ) {
			if( p.x < minX )
				minX = p.x;
			if( p.x > maxX )
				maxX = p.x;
			if( p.y < minY )
				minY = p.y;
			if( p.y > maxY )
				maxY = p.y;
		}

		assertEquals(Math.abs(minX), Math.abs(maxX), 1e-8);
		assertEquals(Math.abs(minY), Math.abs(maxY), 1e-8);
	}

	/**
	 * Make sure new instances of calibration points are returned each time
	 */
	@Test
	public void dataNotRecycled() {
		DetectorFiducialCalibration detector = createDetector();

		assertTrue(detector.process(original));
		CalibrationObservation found0 = detector.getDetectedPoints();

		assertTrue(detector.process(original));
		CalibrationObservation found1 = detector.getDetectedPoints();

		assertEquals(found0.size(),found1.size());
		assertTrue(found0 != found1);
		for (int i = 0; i < found0.size(); i++) {
			assertFalse(found1.points.contains(found0.points.get(0)));
		}
	}

	/**
	 * Easy case with no distortion
	 */
	@Test
	public void undistorted() {
		DetectorFiducialCalibration detector = createDetector();

//		display(original);

		assertTrue(detector.process(original));

		CalibrationObservation found = detector.getDetectedPoints();

		checkList(found, false);
	}

	/**
	 * Pinch it a little bit like what is found with perspective distortion
	 */
	@Test
	public void distorted() { // TODO Remove
		DetectorFiducialCalibration detector = createDetector();

		createTransform(width / 5, height / 5, width * 4 / 5, height / 6, width - 1, height - 1, 0, height - 1);

		PixelTransform2_F32 pixelTransform = new PointToPixelTransform_F32(d2o);

		ImageMiscOps.fill(distorted, 0xff);
		DistortImageOps.distortSingle(original, distorted, pixelTransform,
				InterpolationType.BILINEAR, BorderType.EXTENDED);

//		display(distorted);

		assertTrue(detector.process(distorted));

		CalibrationObservation found = detector.getDetectedPoints();
		checkList(found, true);
	}

	private void display( GrayF32 image ) { // TODO remove
		BufferedImage visualized = ConvertBufferedImage.convertTo(image, null, true);
		ShowImages.showWindow(visualized, "Input");

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// TODO remove
	public void checkList(CalibrationObservation found , boolean applyTransform ) {
		List<CalibrationObservation> expectedList = new ArrayList<>();

		if( !applyTransform ) {
			expectedList.addAll(this.solutions);
		} else {
			for (int i = 0; i < solutions.size(); i++) {
				CalibrationObservation orig = solutions.get(i);
				CalibrationObservation mod = new CalibrationObservation();
				for (int j = 0; j < orig.size(); j++) {
					Point2D_F64 p = orig.points.get(i).copy();

					o2d.compute(p.x, p.y, p);
					mod.add(p, orig.get(j).index );
				}
				expectedList.add(mod);
			}
		}

		assertEquals(expectedList.get(0).size(),found.size());

		// the order is important.  check to see that they are close and in the correct order
		boolean anyMatched = false;
		for (int i = 0; i < expectedList.size(); i++) {
			CalibrationObservation expected = expectedList.get(i);
			boolean matched = true;

			for (int j = 0; j < expected.size(); j++) {
				if( found.get(j).index != expected.get(j).index ) {
					matched = false;
					break;
				}

				Point2D_F64 f = found.get(i);
				Point2D_F64 e = expected.get(i);
				if( f.distance(e) >= 3 ) {
					matched = false;
					break;
				}
			}
			if( matched ) {
				anyMatched = true;
				break;
			}
		}
		assertTrue(anyMatched);
	}

	// TODO remove
	public void createTransform( double x0 , double y0 , double x1 , double y1 ,
								 double x2 , double y2 , double x3 , double y3 )
	{
		// Homography estimation algorithm.  Requires a minimum of 4 points
		Estimate1ofEpipolar computeHomography = FactoryMultiView.computeHomography(true);

		// Specify the pixel coordinates from destination to target
		ArrayList<AssociatedPair> associatedPairs = new ArrayList<>();
		associatedPairs.add(new AssociatedPair(x0, y0, 0, 0));
		associatedPairs.add(new AssociatedPair(x1, y1, width-1, 0));
		associatedPairs.add(new AssociatedPair(x2, y2, width-1, height-1));
		associatedPairs.add(new AssociatedPair(x3, y3, 0, height - 1));

		// Compute the homography
		DMatrixRMaj H = new DMatrixRMaj(3, 3);
		computeHomography.process(associatedPairs, H);

		// Create the transform for distorting the image
		FMatrixRMaj H32 = new FMatrixRMaj(3,3);
		ConvertMatrixData.convert(H,H32);
		d2o = new PointTransformHomography_F32(H32);
		CommonOps_DDRM.invert(H);
		o2d = new PointTransformHomography_F64(H);
	}

	/**
	 * Observations points should always be in increasing order
	 */
	@Test
	public void checkPointIndexIncreasingOrder() {
		DetectorFiducialCalibration detector = createDetector();

		assertTrue(detector.process(original));
		CalibrationObservation found = detector.getDetectedPoints();

		assertEquals(detector.getLayout().size(),found.size());

		for (int i = 0; i < found.size(); i++) {
			assertEquals(i, found.get(i).index);
		}
	}
}
