/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ---------------------------------------------------------------------
 */

/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 06/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.test.viewer.backplane;

import java.awt.geom.Point2D;

/**
 * Represents the results from a query operation
 */
public class MvQueryResult {

  final Point2D compositePoint;
  final Point2D modelPoint;
  final String text;

  /**
   * Standard constructor
   * @param compositePoint the query point in the composite coordinate system
   * @param modelPoint the query point in the model coordinate system
   * @param text the text result of the query
   */
  MvQueryResult(Point2D compositePoint, Point2D modelPoint, String text) {
    this.compositePoint = compositePoint;
    this.modelPoint = modelPoint;
    this.text = text;
  }

  /**
   * Get the text result of the query
   * @return a valid string
   */
  public String getText() {
    return text;
  }

  /**
   * Get the query point in the composite coordinate system
   * @return a valid point
   */
  public Point2D getCompositePoint() {
    return new Point2D.Double(compositePoint.getX(), compositePoint.getY());
  }

  /**
   * Get the query point in the model coordinate system
   * @return a valid point
   */
  public Point2D getModelPoint() {
    return new Point2D.Double(modelPoint.getX(), modelPoint.getY());
  }

}
