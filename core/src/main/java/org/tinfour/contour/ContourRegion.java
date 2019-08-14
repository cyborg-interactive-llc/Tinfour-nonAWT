/* --------------------------------------------------------------------
 * Copyright 2019 Gary W. Lucas.
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
 * 07/2019  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.contour;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides a elements and access methods for a region created
 * through a contour-building process.
 */
public class ContourRegion {

  List<ContourRegionMember> memberList = new ArrayList<>();
  int regionIndex;
  double z;
  double area;
  double absArea;
  ContourRegion parent;
  List<ContourRegion> children = new ArrayList<>();

  /**
   * Standard constructor
   */
  ContourRegion() {

  }

  /**
   * Construct a region based on a single, closed-loop contour.
   * @param contour a valid instance describing a single, closed-loop contour.
   */
  ContourRegion(Contour contour) {
    assert contour.closedLoop : "Single contour constructor requires closed loop";
    if (contour.closedLoop) {
      double a = 0;
      memberList.add(new ContourRegionMember(contour, true));
      double x0 = contour.xy[contour.n - 2];
      double y0 = contour.xy[contour.n - 1];
      for (int i = 0; i < contour.n; i += 2) {
        double x1 = contour.xy[i];
        double y1 = contour.xy[i + 1];
        a += x0 * y1 - x1 * y0;
        x0 = x1;
        y0 = y1;
      }
      area = a / 2;
      absArea = Math.abs(area);
      if (area < 0) {
        regionIndex = contour.rightIndex;
      } else {
        regionIndex = contour.leftIndex;
      }
      z = contour.z;
    }
  }

  
  /**
   * Get the XY coordinates for the contour region
   * @return a safe copy of the geometry of the contour region.
   */
  public double[] getXY() {
    Contour contour = memberList.get(0).contour;
    return contour.getCoordinates();
  }

  void addChild(ContourRegion region) {
    children.add(region);
    region.parent = this;
  }

  /**
   * Indicates whether the specified point is inside the region
   * @param x the Cartesian coordinate for the point of interest
   * @param y the Cartesian coordinate for the point of interest
   * @return true if the point is inside the contour; otherwise, fakse
   */
  public boolean isPointInsideRegion(double x, double y) {
    Contour c = memberList.get(0).contour;
    double[] xy = c.xy;
    int rCross = 0;
    int lCross = 0;
    int n = c.size();
    double x0 = xy[n * 2 - 2];
    double y0 = xy[n * 2 - 1];
    for (int i = 0; i < n; i++) {

      double x1 = xy[i * 2];
      double y1 = xy[i * 2 + 1];

      double yDelta = y0 - y1;
      if (y1 > y != y0 > y) {
        double xTest = (x1 * y0 - x0 * y1 + y * (x0 - x1)) / yDelta;
        if (xTest > x) {
          rCross++;
        }
      }
      if (y1 < y != y0 < y) {
        double xTest = (x1 * y0 - x0 * y1 + y * (x0 - x1)) / yDelta;
        if (xTest < x) {
          lCross++;
        }
      }
      x0 = x1;
      y0 = y1;
    }

    // (rCross%2) != (lCross%2)
    if (((rCross ^ lCross) & 0x01) == 1) {
      return false; // on border
    } else if ((rCross & 0x01) == 1) {
      return true; // unambiguously inside
    }
    return false; // unambiguously outside
  }
  
  /**
   * Gets the absolute value of the area of the region.
   * @return a positive value, potentially zero if the region is
   * incompletely specified.
   */
  public double getAbsoluteArea(){
    return absArea;
  }

  /**
   * Gets the signed area of the region. If the points that specify the
   * region are given in a counter-clockwise order, the region will have
   * a positive area. If the points are given in a clockwise order,
   * the region will have a negative area.
   * @return 
   */
  public double getSignedArea(){
    return area;
  }
  
  /**
   * Gets the index of the region. The indexing scheme is based on
   * the original values of the zContour array used when the contour
   * regions were built. The minimim proper region index is zero.
   * <p>
   * At this time, regions are not constructed for areas of null
   * data. In future implementations, null-data regions will be 
   * indicated by a region index of -1.
   * @return a positive integer value, or -1 for null-data regions.
   */
  public int getRegionIndex(){
    return regionIndex;
  }
  
  /**
   * Gets a Path2D suitable for rendering purposes.
   *
   * @param transform a valid AffineTransform, typically specified to map the
   * Cartesian coordinates of the contour to pixel coordinate.
   * @return a valid instance
   */
  public Path2D getPath2D(AffineTransform transform) {
    double[] xy = getXY();
    int n = xy.length;
    Path2D path = new Path2D.Double();
    if (n >= 4) {
      double[] c = new double[n];
      transform.transform(xy, 0, c, 0, n / 2);

      path.moveTo(c[0], c[1]);
      for (int i = 1; i < n / 2; i++) {
        path.lineTo(c[i * 2], c[i * 2 + 1]);
      }
      path.closePath();

    }
    return path;
  }

  
  
  
  
  @Override
  public String toString() {
    return String.format("%4d %12.2f  %s %d",
            regionIndex, area, parent == null ? "root " : "child", 
            children.size());
  }
}
