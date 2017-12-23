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

package boofcv.alg.fiducial.qrcode;

import boofcv.struct.image.ImageGray;
import georegression.struct.point.Point2D_F32;
import org.ddogleg.struct.FastQueue;

/**
 * Searches the image for alignment patterns. First it computes a transform that removes perspective distortion
 * using previously detected position patterns. Then it searches inside the image for the position patterns. If
 * available, adjacent position patterns are used to adjust the search so that locations distant from position
 * patterns can be compensated for.
 *
 * @author Peter Abeles
 */
public class QrCodeAlignmentPatternLocator<T extends ImageGray<T>> {

	// grid for quick look up of alignment patterns to adjust search
	private FastQueue<QrCode.Alignment> lookup = new FastQueue<>(QrCode.Alignment.class,true);

	private QrCodeBinaryGridReader<T> reader;

	// pixel value storage used when localizing
	float arrayX[] = new float[12];
	float arrayY[] = new float[12];

	QrCode qr;

	// more work space
	Point2D_F32 pixel = new Point2D_F32();

	public QrCodeAlignmentPatternLocator( Class<T> imageType ) {

		reader = new QrCodeBinaryGridReader<T>(imageType);
	}

	/**
	 * Uses the previously detected position patterns to seed the search for the alignment patterns
	 */
	public boolean process(T image , QrCode qr ) {
		this.qr = qr;

		reader.setImage(image);
		reader.setMarker(qr);

		initializePatterns(qr);

		// version 1 has no alignment patterns
		if( qr.version <= 1 )
			return true;
		return localizePositionPatterns(QrCode.VERSION_INFO[qr.version].alignment);
	}

	boolean localizePositionPatterns(int[] alignmentLocations ) {
		int size = alignmentLocations.length;

		for (int row = 0; row < size; row++) {
			for (int col = 0; col < size; col++) {
				QrCode.Alignment a = lookup.get(row*size+col);
				if( a == null )
					continue;

				// adjustment from previously found alignment patterns
				double adjY=0,adjX=0;

				if( row > 0) {
					QrCode.Alignment p = lookup.get((row - 1) * size + col);
					if( p != null )
						adjY = p.moduleY+0.5-p.moduleFound.y;
				}
				if( col > 0 ) {
					QrCode.Alignment p = lookup.get(row * size + col -1);
					if( p != null )
						adjX = p.moduleX+0.5-p.moduleFound.x;
				}


				if( !centerOnSquare(a,(float)(a.moduleX+0.5+adjX),(float)(a.moduleY+0.5+adjY))) {
					return false;
				}

				if( !localize(a,(float)a.moduleFound.x,(float)a.moduleFound.y) ) {
					return false;
				}
			}
		}
		return true;
	}

	float samples[] = new float[9];

	/**
	 * If the initial guess is within the inner white circle or black dot this will ensure that it is centered
	 * on the black dot
	 */
	boolean centerOnSquare(QrCode.Alignment pattern , float guessX , float guessY) {
		float step = 1;
		float bestMag = Float.MAX_VALUE;
		float bestX = guessX;
		float bestY = guessY;

		for (int i = 0; i < 10; i++) {
			for (int row = 0; row < 3; row++) {
				float gridy = guessY - 1f + row;
				for (int col = 0; col < 3; col++) {
					float gridx = guessX - 1f + col;

					samples[row*3+col] = reader.read(gridx,gridy);
				}
			}

			float dx = (samples[2]+samples[5]+samples[8])-(samples[0]+samples[3]+samples[6]);
			float dy = (samples[6]+samples[7]+samples[8])-(samples[0]+samples[1]+samples[2]);

			float r = (float)Math.sqrt(dx*dx + dy*dy);

			if( bestMag > r ) {
//				System.out.println("good step at "+i);
				bestMag = r;
				bestX = guessX;
				bestY = guessY;
			} else {
//				System.out.println("bad step at "+i);
				step *= 0.75f;
			}

			guessX = bestX+step*dx/r;
			guessY = bestY+step*dy/r;
		}

		pattern.moduleFound.x = bestX;
		pattern.moduleFound.y = bestY;

		reader.gridToImage((float)pattern.moduleFound.x,(float)pattern.moduleFound.y,pixel);
		pattern.pixel.set(pixel.x,pixel.y);

		return true;
	}

	/**
	 * Localizizes the alignment pattern crudely by searching for the black box in the center by looking
	 * for its edges in the gray scale image
	 *
	 * @return true if success or false if it doesn't resemble an alignment pattern
	 */
	boolean localize(QrCode.Alignment pattern , float guessX , float guessY)
	{
		// sample along the middle. Try to not sample the outside edges which could confuse it
		for (int i = 0; i < arrayY.length; i++) {
			float x = guessX - 1.5f + i*3f/12.0f;
			float y = guessY - 1.5f + i*3f/12.0f;

			arrayX[i] = reader.read(x,guessY);
			arrayY[i] = reader.read(guessX,y);
		}

		// TODO turn this into an exhaustive search of the array for best up and down point?
		int downX = greatestDown(arrayX);
		if( downX == -1) return false;
		int upX = greatestUp(arrayX,downX);
		if( upX == -1) return false;

		int downY = greatestDown(arrayY);
		if( downY == -1 ) return false;
		int upY = greatestUp(arrayY,downY);
		if( upY == -1 ) return false;

		pattern.moduleFound.x = guessX - 1.5f + (downX+upX)*3f/24.0f;
		pattern.moduleFound.y = guessY - 1.5f + (downY+upY)*3f/24.0f;

		reader.gridToImage((float)pattern.moduleFound.x,(float)pattern.moduleFound.y,pattern.pixel);

		return true;
	}

	/**
	 * Searches for the greatest down slope in the list
	 */
	static int greatestDown( float array[] ) {
		int best = -1;
		float bestScore = 0;

		for (int i = 5; i < array.length; i++) {
			float diff = (4.0f/2.0f)*( array[i-5]+array[i]);
			diff -= array[i-4]+array[i-3]+array[i-2]+array[i-1];

			if( diff > bestScore) {
				bestScore = diff;
				best = i-4;
			}
		}
		return best;
	}

	static int greatestUp( float array[] , int start) {
		int best = -1;
		float bestScore = 0;

		for (int i = start; i < array.length; i++) {
			float diff = array[i]-array[i-1];
			if( diff > bestScore) {
				bestScore = diff;
				best = i-1;
			}
		}
		return best;
	}

	/**
	 * Creates a list of alignment patterns to look for and their grid coordinates
	 */
	void initializePatterns(QrCode qr) {
		int qrsize = qr.totalModules();
		int where[] = QrCode.VERSION_INFO[qr.version].alignment;
		qr.alignment.reset();
		lookup.reset();
		for (int row = where.length-1; row >= 0; row--) {
			for (int col = 0; col < where.length; col++) {
				boolean skip = false;
				if( row == 0 & col == 0 )
					skip = true;
				else if( row == where.length-1 & col == 0)
					skip = true;
				else if( row == where.length-1 & col == where.length-1)
					skip = true;

				if( skip ) {
					lookup.add(null);
				} else {
					QrCode.Alignment a = qr.alignment.grow();
					a.moduleX = where[col];
					a.moduleY = qrsize-where[row]-1;
					lookup.add(a);
				}
			}
		}
	}



}
