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

package boofcv.struct.border;

import boofcv.struct.image.InterleavedS64;

import java.lang.reflect.InvocationTargetException;


/**
 * @author Peter Abeles
 */
public class ImageBorder1D_IL_S64 extends ImageBorder_IL_S64
		implements ImageBorder1D
{
	BorderIndex1D rowWrap;
	BorderIndex1D colWrap;

	public ImageBorder1D_IL_S64(Class<?> type) {
		try {
			this.rowWrap = (BorderIndex1D)type.getConstructor().newInstance();
			this.colWrap = (BorderIndex1D)type.getConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	public ImageBorder1D_IL_S64(BorderIndex1D rowWrap, BorderIndex1D colWrap) {
		this.rowWrap = rowWrap;
		this.colWrap = colWrap;
	}

	@Override
	public BorderIndex1D getRowWrap() {
		return rowWrap;
	}

	@Override
	public BorderIndex1D getColWrap() {
		return colWrap;
	}

	@Override
	public void setImage(InterleavedS64 image) {
		super.setImage(image);
		colWrap.setLength(image.width);
		rowWrap.setLength(image.height);
	}

	@Override
	public ImageBorder1D_IL_S64 copy() {
		return new ImageBorder1D_IL_S64(this.rowWrap.copy() ,this.colWrap.copy());
	}

	@Override
	public void getOutside(int x, int y, long pixel[] ) {
		image.unsafe_get(colWrap.getIndex(x), rowWrap.getIndex(y), pixel);
	}

	@Override
	public void setOutside(int x, int y, long[] pixel) {
		image.unsafe_set(colWrap.getIndex(x), rowWrap.getIndex(y), pixel);
	}
}
