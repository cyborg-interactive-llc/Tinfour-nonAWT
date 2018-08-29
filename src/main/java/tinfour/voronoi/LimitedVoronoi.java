/* --------------------------------------------------------------------
 * Copyright 2018 Gary W. Lucas.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0A
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
 * Date Name Description
 * ------   --------- -------------------------------------------------
 * 07/2018  G. Lucas  Initial implementation 
 * 08/2018  G. Lucas  Added vertex based constructor and build options
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.voronoi;

import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import tinfour.common.Circumcircle;
import tinfour.common.IIncrementalTin;
import tinfour.common.IQuadEdge;
import tinfour.common.Vertex;
import tinfour.edge.EdgePool;
import tinfour.edge.QuadEdge;
import tinfour.semivirtual.SemiVirtualIncrementalTin;
import tinfour.standard.IncrementalTin;
import tinfour.utils.TinInstantiationUtility;
import tinfour.utils.VertexColorizerKempe6;

/**
 * Constructs a Voronoi Diagram structure from a populated instance of an
 * IncrementalTin. The resulting structure is "limited" in the sense that it
 * covers only a finite domain on the coordinate plane (unlike a true Voronoi
 * Diagram, which covers an infinite domain).
 * <p>
 * <strong>This class is under development and is subject to changes in its API
 * and behavior.</strong>
 */
public class LimitedVoronoi {

  /**
   * The overall domain of the structure
   */
  final private Rectangle2D bounds;
  double xmin;
  double xmax;
  double ymin;
  double ymax;

  /**
   * The overall bounds of the sample points
   */
  final private Rectangle2D sampleBounds;

  final private EdgePool edgePool;

  final private List<Vertex> circleList = new ArrayList<>();

  final private List<ThiessenPolygon> polygons = new ArrayList<>();

  private double maxRadius = -1;

  private LimitedVoronoi() {
    // a private constructor to deter applications from
    // invoking the default constructor
    sampleBounds = null;
    bounds = null;
    edgePool = null;
  }

  /**
   * Construct a Voronoi Diagram structure based on the input vertex set.
   *
   * @param vertexList a valid list of vertices
   * @param options optional specification for setting build parameters or a
   * null to use defaults.
   *
   */
  public LimitedVoronoi(List<Vertex> vertexList, LimitedVoronoiBuildOptions options) {
    if (vertexList == null) {
      throw new IllegalArgumentException(
              "Null input not allowed for constructor");
    }

    int nVertices = vertexList.size();
    if (nVertices < 3) {
      throw new IllegalArgumentException(
              "Insufficent input size, at least 3 vertices are required");
    }

    sampleBounds = new Rectangle2D.Double(
            vertexList.get(0).getX(),
            vertexList.get(0).getY(),
            0, 0);

    for (Vertex v : vertexList) {
      sampleBounds.add(v.getX(), v.getY());
    }

    // estimate a nominal point spacing based on the domain of the
    // input data set and assuming a rougly uniform density.
    // the value 0.866 is based on the parameters of a regular
    // hexagonal tesselation of a plane
    double area = sampleBounds.getWidth() * sampleBounds.getHeight();
    double nominalPointSpacing = Math.sqrt(area / nVertices / 0.866);
    TinInstantiationUtility maker
            = new TinInstantiationUtility(0.25, vertexList.size());
    IIncrementalTin tin = maker.constructInstance(nominalPointSpacing);
    tin.add(vertexList, null);
    if (!tin.isBootstrapped()) {
      throw new IllegalArgumentException(
              "Input vertex geometry is insufficient "
              + "to establish a Voronoi Diagram");
    }

    this.bounds = new Rectangle2D.Double(
            sampleBounds.getX(),
            sampleBounds.getY(),
            sampleBounds.getWidth(),
            sampleBounds.getHeight());

    edgePool = new EdgePool();

    LimitedVoronoiBuildOptions pOptions = options;
    if (options == null) {
      pOptions = new LimitedVoronoiBuildOptions();
    }

    buildStructure(tin, pOptions);
    if (pOptions.enableAutomaticColorAssignment) {
      VertexColorizerKempe6 kempe6 = new VertexColorizerKempe6();
      kempe6.assignColorsToVertices(tin);
    }
    tin.dispose();
  }

  /**
   * Constructs an instance of a Voronoi Diagram that corresponds to the input
   * Delaunay Triangulation.
   *
   * @param delaunayTriangulation a valid instance of a Delaunay Triangulation
   * implementation.
   */
  public LimitedVoronoi(IIncrementalTin delaunayTriangulation) {
    if (delaunayTriangulation == null) {
      throw new IllegalArgumentException(
              "Null input is not allowed for TIN");
    }
    if (!delaunayTriangulation.isBootstrapped()) {
      throw new IllegalArgumentException(
              "Input TIN is not bootstrapped (populated)");
    }

    sampleBounds = delaunayTriangulation.getBounds();
    this.bounds = new Rectangle2D.Double(
            sampleBounds.getX(),
            sampleBounds.getY(),
            sampleBounds.getWidth(),
            sampleBounds.getHeight());

    edgePool = new EdgePool();
    LimitedVoronoiBuildOptions pOptions = new LimitedVoronoiBuildOptions();
    buildStructure(delaunayTriangulation, pOptions);
  }

  private void buildPart(IQuadEdge e, Vertex[] center, IQuadEdge[] part) {
    // the parts are built so that the part associated with an edge index
    // is a segment from outside the triangle to its circumcircle center
    // in cases where the center is inside the triangle, this is 
    // essentially "outside to inside", though that is often not true.
    IQuadEdge d = e.getDual();
    int eIndex = e.getIndex();
    int dIndex = d.getIndex();
    Vertex v0 = center[dIndex];
    Vertex v1 = center[eIndex];
    if (v0 == null || v1 == null) {
      // this is a ghost triangle.  just ignore it
      return;
    }

//    int outcode0 = v0.getColorIndex();
//    int outcode1 = v1.getColorIndex();
//    if((outcode0 & outcode1)!=0){
//      // the edge is entirely outside the bounded area 
//      // and does not intersect it.  It can be rejected trivially.
//      // Note that this determination will also reject edges that
//      // lie exactly on a boundary.
//    }
//    if ((outcode0|outcode1)==0) {
//      // both vertices are entirely within the bounded area.
//      // the edge can be accepted trivially
//      IQuadEdge n = edgePool.allocateEdge(v0, v1);
//      part[eIndex] = n;
//      part[dIndex] = n.getDual();
//    }
    // the edge intersects at least one and potentially two boundaries.
    IQuadEdge n = liangBarsky(v0, v1);
    if (n != null) {
      part[eIndex] = n;
      part[dIndex] = n.getDual();
    }

  }

  private IQuadEdge liangBarsky(Vertex v0, Vertex v1) {
    double x0 = v0.getX();
    double y0 = v0.getY();
    double x1 = v1.getX();
    double y1 = v1.getY();

    double t0 = 0;
    double t1 = 1;
    int iBorder0 = -1;
    int iBorder1 = -1;
    double xDelta = x1 - x0;
    double yDelta = y1 - y0;
    double p, q, r;

    for (int iBorder = 0; iBorder < 4; iBorder++) {
      switch (iBorder) {
        case 0:
          // bottom
          p = -yDelta;
          q = -(ymin - y0);
          break;
        case 1:
          // right
          p = xDelta;
          q = xmax - x0;
          break;
        case 2:
          // top 
          p = yDelta;
          q = ymax - y0;
          break;
        case 3:
        default:
          // left
          p = -xDelta;
          q = -(xmin - x0);
          break;
      }

      if (p == 0) {
        // if q<0, the line is entirely outside.
        // otherwise, it is ambiguous
        if (q < 0) {
          // line is entirely outside
          return null;
        }
      } else {
        r = q / p;
        if (p < 0) {
          if (r > t1) {
            return null;
          } else if (r > t0) {
            t0 = r;
            iBorder0 = iBorder;
          }
        } else // p>0
         if (r < t0) {
            return null;
          } else if (r < t1) {
            t1 = r;
            iBorder1 = iBorder;
          }

      }
    }

    Vertex p0;
    Vertex p1;

    double x, y, z;
    if (iBorder0 == -1) {
      p0 = v0;
    } else {
      x = x0 + t0 * xDelta;
      y = y0 + t0 * yDelta;
      z = computeZ(iBorder0, x, y);
      p0 = new Vertex(x, y, z, v0.getIndex());
      p0.setSynthetic(true);
    }

    if (iBorder1 == -1) {
      p1 = v1;
    } else {
      x = x0 + t1 * xDelta;
      y = y0 + t1 * yDelta;
      z = computeZ(iBorder1, x, y);
      p1 = new Vertex(x, y, z, v1.getIndex());
      p1.setSynthetic(true);
    }

    return edgePool.allocateEdge(p0, p1);
  }

  @SuppressWarnings("PMD.CollapsibleIfStatements")
  private double computeZ(double x, double y) {
    if (y == ymin) {
      // bottom border range 0 to 1
      if (xmin <= x && x <= xmax) {
        return (x - xmin) / (xmax - xmin);
      }
    } else if (x == xmax) {
      // right border, range 1 to 2
      if (ymin <= y && y <= ymax) {
        return 1 + (y - ymin) / (ymax - ymin);
      }
    } else if (y == ymax) {
      // top border, range 2 to 3
      if (xmin <= x && x <= xmax) {
        return 3 - (x - xmin) / (xmax - xmin);
      }
    } else if (x == xmin) {
      // left border, range 3 to 4
      if (ymin <= y && y <= ymin) {
        return 4 - (y - ymin) / (ymax - ymin);
      }
    }
    return Double.NaN;
  }

  private double computeZ(int iBoarder, double x, double y) {
    switch (iBoarder) {
      case 0:
        return (x - xmin) / (xmax - xmin);
      case 1:
        return 1 + (y - ymin) / (ymax - ymin);
      case 2:
        return 3 - (x - xmin) / (xmax - xmin);
      default:
        return 4 - (y - ymin) / (ymax - ymin);
    }
  }

  /**
   * Build an edge based on the ray outward from the associated circumcenter and
   * perpendicular to the perimeter edge
   *
   * @param e a perimeter edge
   * @param center the array of circumcenters
   * @param part the array to store parts
   */
  private void buildPerimeterRay(IQuadEdge e, Vertex[] center, IQuadEdge[] part) {
    int index = e.getIndex();
    Vertex vCenter = center[index]; // vertex at the circumcenter
    Vertex A = e.getA();
    Vertex B = e.getB();
    double x0 = bounds.getMinX();
    double x1 = bounds.getMaxX();
    double y0 = bounds.getMinY();
    double y1 = bounds.getMaxY();

    // construct and edge from the outside to the inside.
    //   the edge we construct is based on an infinite ray outward
    // from the circumcircle and perpendicular to the perimeter edge.
    // because we will be constructing on the RIGHT side of the 
    // perimeter edge rather than the left, the perpendicular vector
    // for the ray will be (eY, -eX).   Because we cannot handle 
    // infinite rays, we need to clip the ray to form a segment 
    // running from the circumcircle center to the bounds.  it is 
    // possible that the ray could intersect two edges (a horizontal edge
    // and a vertical edge), so we want to find the first intersection
    // the one closest to the center.
    double eX = B.getX() - A.getX();
    double eY = B.getY() - A.getY();
    double u = Math.sqrt(eX * eX + eY * eY);
    double uX = eY / u;
    double uY = -eX / u;
    double cX = vCenter.getX();
    double cY = vCenter.getY();
    double tX = Double.POSITIVE_INFINITY;
    double tY = Double.POSITIVE_INFINITY;
    double x = Double.NaN;
    double y = Double.NaN;
    double z;
    // in the following, we screen out the uX==0 and uY==0 cases
    // because the they only intersect one edge
    if (uX < 0) {
      // off to the left
      tX = (x0 - cX) / uX;
      x = x0;
    } else if (uX > 0) {
      tX = (x1 - cX) / uX;
      x = x1;
    }
    if (uY < 0) {
      tY = (y0 - cY) / uY;
      y = y0;
    } else if (tY > 0) {
      tY = (y1 - cY) / uY;
      y = y1;
    }
    if (tX < tY) {
      // find the y corresponding to x = tX*uX+cX;
      y = tX * uY + cY;
      double s = (y - y0) / (y1 - y0);
      if (uX < 0) {
        // the left side, descending
        z = 4 - s;
      } else {
        // the right edge, ascending
        z = 1 + s;
      }
    } else {
      // find the x correspoinding to y = tY*uY+cY
      x = tY * uX + cX;
      double s = (x - x0) / (x1 - x0);
      if (uY < 0) {
        z = s;
      } else {
        z = 3 - s;
      }
    }

    // the negative vertex index is just a diagnostic/debugging tool.
    Vertex vOut = new Vertex(x, y, z, -vCenter.getIndex());

    QuadEdge n = edgePool.allocateEdge(vOut, vCenter); // from out to in
    part[index] = n;
    part[index ^ 0x01] = n.getDual();

  }

  /**
   * Computes the outcode for a vertex (usually a circumcenter) and stores the
   * result in the color-index field of the vertex. It is assumed that the
   * vertex is "owned" by this instance and that the color index will not be
   * used for other purposes. The layout of the outcodes used here, in Cartesian
   * coordinates is
   * <pre>
   *    top     1001  1000  1010      (top row 1000)
   *            0001  0000  0010
   *    bottom  0101  0100  0110      (bottom row 0100)
   *
   *    left column  0001
   *    right column 0010
   * </pre> For this application, we define a vertex lying on the border as
   * having a non-zero outcode for that border. In the Cohen-Sutherland
   * algorithm, if the AND of the outcodes for the two endpoints of a segment
   * comes up with a non-zero value, the segment is treated as being completely
   * exterior and non-intersecting with the bounded area. Using that logic, this
   * algorithm treats any edge lying along one of the edges for that border as
   * being exterior to bounded area.
   *
   * @param c a valid vertex (usually a circumcircle)
   */
  private void computeAndSetOutcode(Vertex c) {
    double x = c.getX();
    double y = c.getY();
    int code;
    if (x <= xmin) {
      code = 0b0001;
    } else if (x >= xmax) {
      code = 0b0010;
    } else {
      code = 0;
    }
    if (y <= ymin) {
      code |= 0b0100;
    } else if (y >= ymax) {
      code |= 0b1000;
    }
    c.setColorIndex(code);
  }

  private int mindex(IQuadEdge e, IQuadEdge f, IQuadEdge r) {
    int index = e.getIndex();
    if (f.getIndex() < index) {
      index = f.getIndex();
    }
    if (r.getIndex() < index) {
      return r.getIndex();
    } else {
      return index;
    }
  }

  /**
   * Builds the circumcircle for the triangle to the left of the specified edge.
   * The resulting circumcircle-center vertex is stored in the centers[] array
   * which maps the inside-edge-index for each edge of the triangle to the
   * circumcircle-center vertex.
   * <p>
   * This routine also adds the circumcircle vertex to the centersList.
   * <p>
   * Because of the way the edge iterator works, this routine may be called up
   * to three times for the same triangle. The circumcircle calculation will
   * only be performed the first time.
   *
   * @param cCircle a re-usable circumcircle instance for making the calculation
   * and storing the results.
   * @param e an inner-side object of the edge of the triangle of interest
   * @param centers the array for storing triangle centers.
   */
  private void buildCenter(Circumcircle cCircle, IQuadEdge e, Vertex[] centers) {
    int index = e.getIndex();
    if (centers[index] == null) {
      Vertex A = e.getA();
      Vertex B = e.getB();
      IQuadEdge f = e.getForward();
      IQuadEdge r = e.getReverse();
      Vertex C = e.getForward().getB();
      if (C != null) {
        if (!cCircle.compute(A, B, C)) {
          throw new IllegalStateException(
                  "Internal error, triangle does not yield circumcircle");
        }
        double x = cCircle.getX();
        double y = cCircle.getY();
        double z = computeZ(x, y);
        Vertex v = new Vertex(x, y, z, mindex(e, f, r));
        centers[e.getIndex()] = v;
        centers[f.getIndex()] = v;
        centers[r.getIndex()] = v;
        circleList.add(v);
        double radius = cCircle.getRadius();
        if (radius > maxRadius) {
          maxRadius = radius;
        }
        bounds.add(x, y);
      }
    }
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  private void buildStructure(
          IIncrementalTin tin,
          LimitedVoronoiBuildOptions pOptions) {

    if (pOptions.enableAdjustments) {
      if (tin instanceof IncrementalTin) {
        ((IncrementalTin) tin).collaspsePerimeterTriangles(
                pOptions.adjustmentThreshold);
      } else if (tin instanceof SemiVirtualIncrementalTin) {
        ((SemiVirtualIncrementalTin) tin).collaspsePerimeterTriangles(
                pOptions.adjustmentThreshold);
      }
    }

    // The visited array tracks which of the TIN edges were 
    // visited for various processes.  It is used more than once.
    // There should be one part for each non-ghost edge.   The part
    // array is indexed using the tin edge index so that
    //    correspondingPart = part[edge.getIndex()]
    int maxEdgeIndex = tin.getMaximumEdgeAllocationIndex() + 1;
    boolean[] visited = new boolean[maxEdgeIndex];
    Vertex[] centers = new Vertex[maxEdgeIndex];
    QuadEdge[] parts = new QuadEdge[maxEdgeIndex];
    List<IQuadEdge> scratch = new ArrayList<>();
    List<IQuadEdge> perimeter = tin.getPerimeter();
    Circumcircle cCircle = new Circumcircle();
    // build the circumcircle-center vertices 
    // also collect some information about the overall
    // bounds and edge length of the input TIN.
    Iterator<IQuadEdge> edgeIterator = tin.getEdgeIterator();
    double sumEdgeLength = 0;
    int nEdgeLength = 0;
    while (edgeIterator.hasNext()) {
      IQuadEdge e = edgeIterator.next();
      if (e.getA() == null || e.getB() == null) {
        // ghost edge, do not process
        // mark both sides as visited to suppress future checks.
        int index = e.getIndex();
        visited[index] = true;
        visited[index ^ 0x01] = true;
        continue;
      }
      sumEdgeLength += e.getLength();
      nEdgeLength++;
      buildCenter(cCircle, e, centers);
      buildCenter(cCircle, e.getDual(), centers);
    }

    if (pOptions.bounds == null) {
      double avgLen = sumEdgeLength / nEdgeLength;
      xmin = sampleBounds.getMinX() - avgLen / 4;
      xmax = sampleBounds.getMaxX() + avgLen / 4;
      ymin = sampleBounds.getMinY() - avgLen / 4;
      ymax = sampleBounds.getMaxY() + avgLen / 4;
      bounds.setRect(xmin, ymin, xmax - xmin, ymax - ymin);
    } else {
      if (!pOptions.bounds.contains(sampleBounds)) {
        throw new IllegalArgumentException(
                "Optional bounds specification does not entirely contain the sample set");
      }
      xmin = pOptions.bounds.getMinX();
      xmax = pOptions.bounds.getMaxX();
      ymin = pOptions.bounds.getMinY();
      ymax = pOptions.bounds.getMaxY();
      bounds.setRect(xmin, ymin, xmax - xmin, ymax - ymin);
    }

    for (Vertex circumcircle : circleList) {
      computeAndSetOutcode(circumcircle);
    }

    // perimeter edges get special treatment because they give rise
    // to an infinite ray outward from circumcenter
    for (IQuadEdge p : perimeter) {
      visited[p.getIndex()] = true;
      buildPerimeterRay(p, centers, parts);
    }

    edgeIterator = tin.getEdgeIterator();
    while (edgeIterator.hasNext()) {
      IQuadEdge e = edgeIterator.next();
      IQuadEdge d = e.getDual();
      int eIndex = e.getIndex();
      int dIndex = d.getIndex();
      if (visited[eIndex]) {
        continue;
      }
      visited[eIndex] = true;
      visited[dIndex] = true;
      buildPart(e, centers, parts);
    }

    // reset the visited array, set all the ghost edges
    // to visited so that they are not processed below
    Arrays.fill(visited, false);
    for (IQuadEdge e : perimeter) {
      IQuadEdge f = e.getForwardFromDual();
      int index = f.getIndex();
      //int index = e.getForwardFromDual().getIndex();
      visited[index] = true;
      visited[index ^ 0x01] = true;
    }

    // the first polygons we build are those that are anchored by a perimeter
    // vertex.  This is the set of all the open polygons.  Once these
    // are built, all other polygons are closed.
    for (IQuadEdge e : perimeter) {
      int index = e.getIndex();
      Vertex hub = e.getA();
      if (!visited[index]) {
        scratch.clear();
        buildPolygon(e, visited, parts, scratch);
        polygons.add(new ThiessenPolygon(hub, scratch, true));
      }
    }
    edgeIterator = tin.getEdgeIterator();
    while (edgeIterator.hasNext()) {
      IQuadEdge e = edgeIterator.next();
      int index = e.getIndex();
      Vertex hub = e.getA();
      if (hub == null) {
        // a ghost edge.  no polygon possible
        visited[index] = true;
      } else if (!visited[index]) {
        scratch.clear();
        buildPolygon(e, visited, parts, scratch);
        polygons.add(new ThiessenPolygon(hub, scratch, false));
      }

      IQuadEdge d = e.getDual();
      index = d.getIndex();
      hub = d.getA();
      if (hub == null) {
        // a ghost edge, no polygon possible
        visited[index] = true;
      } else if (!visited[index]) {
        scratch.clear();
        buildPolygon(d, visited, parts, scratch);
        polygons.add(new ThiessenPolygon(hub, scratch, false));
      }
    }
  }

  private void buildPolygon(IQuadEdge e,
          boolean[] visited,
          QuadEdge[] parts,
          List<IQuadEdge> scratch) {
    int index = e.getIndex();
    QuadEdge prior = null;
    QuadEdge first = null;
    for (IQuadEdge p : e.pinwheel()) {
      index = p.getIndex();
      visited[index] = true;
      QuadEdge q = parts[p.getIndex()];
      if (q == null) {
        // we've reached a discontinuity in the construction.
        // the discontinuity could be due a clipping border or a perimeter ray.
        // we will leave the prior edge alone and complete the links the
        // next time we encounter a valid edge
        continue;
      }
      if (first == null) {
        first = q;
        prior = q;
        continue; // note: "first" not yet added to scratch
      }
      linkEdges(prior, q, scratch);
      prior = q;
    }

    linkEdges(prior, first, scratch); // "first" will be added here
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  private void linkEdges(QuadEdge xprior, QuadEdge q, List<IQuadEdge> scratch) {
    QuadEdge prior = xprior;
    // connect v0 to v1
    Vertex v0 = prior.getB();
    Vertex v1 = q.getA();
    double z0 = v0.getZ();
    double z1 = v1.getZ();
    if (Double.isNaN(z0)) {
      // v0 should be same object as v1
      // a simple link is all that's required
      scratch.add(q);
      prior.setForward(q);
      return;
    }

    // construct new edges to thread a line for z0 to z1
    // 
    // first, it is possible that z0 and z1 are nearly equal but not quite.
    // this could happen due to round-off in the clipping routine.
    // at this time, I have never observed this special case happening
    // but I am including code to handle it anyway
    double test = Math.abs(z0 - z1);
    if (test < 1.0e-9 || test > 4 - 1.0e-9) {
      // a simple link is all that's required
      // TO DO: do we want to replace one of the vertices so that 
      // they are identical?  But what happens in a reverse order 
      // of traversal?
      scratch.add(q);
      prior.setForward(q);
      return;
    }

    // we need to thread v0 to v1.  If both lie on the same
    // border, then this action requires the construction of a synthetic
    // edge from v0 to v1.  But if the vertices lie on different borders,
    // it will be necessary to construct joining lines that bend 
    // around the corners.
    //     the borders are numbered from 0 to 3 in the order
    // bottom, right, top, left.  The z coordinates indicate which
    // border the vertices lie on, with z being given as a fractional
    // value. 
    //  
    // TO DO: are variable names iLast, iFirst confusing? change them?
    int iLast = (int) z0;
    int iFirst = (int) z1;
    if (iFirst < iLast) {
      // it wraps around the lower-left corner
      iFirst += 4;
    }

    // add corners, if any
    for (int i = iLast + 1; i <= iFirst; i++) {
      double x;
      double y;
      // anding with 0x03 is equivalent to modulus 4
      int iCorner = i & 0x03;
      switch (iCorner) {
        case 0:
          // lower-left corner
          x = xmin;
          y = ymin;
          break;
        case 1:
          x = xmax;
          y = ymin;
          break;
        case 2:
          x = xmax;
          y = ymax;
          break;
        default:
          // iCorner == 3
          x = xmin;
          y = ymax;
          break;
      }

      Vertex v = new Vertex(x, y, Double.NaN, -1);
      v.setSynthetic(true);
      QuadEdge n = edgePool.allocateEdge(v0, v);
      n.setSynthetic(true);
      v0 = v;

      scratch.add(n);
      n.setReverse(prior);
      prior = n;

    }

    QuadEdge n = edgePool.allocateEdge(v0, v1);
    n.setSynthetic(true);
    scratch.add(n);
    scratch.add(q);
    n.setReverse(prior);
    q.setReverse(n);
  }

  /**
   * Prints diagnostic statistics for the Voronoi Diagram object.
   *
   * @param ps a valid print stream instance.
   */
  public void printDiagnostics(PrintStream ps) {
    int nClosed = 0;
    double sumArea = 0;
    for (ThiessenPolygon p : polygons) {
      double a = p.getArea();
      if (!Double.isInfinite(a)) {
        sumArea += a;
        nClosed++;
      }
    }
    int nOpen = polygons.size() - nClosed;
    ps.format("Limited Voronoi Diagram%n");
    ps.format("   Polygons:   %8d%n", polygons.size());
    ps.format("     Open:     %8d%n", nOpen);
    ps.format("     Closed:   %8d%n", nClosed);
    ps.format("     Avg Area: %13.4f%n", sumArea / nClosed);
    ps.format("   Vertices:   %8d%n", circleList.size());
    ps.format("   Edges:      %8d%n", edgePool.size());
    ps.format("   Voronoi Bounds%n");
    ps.format("      x min:  %16.4f%n", bounds.getMinX());
    ps.format("      y min:  %16.4f%n", bounds.getMinY());
    ps.format("      x max:  %16.4f%n", bounds.getMaxX());
    ps.format("      y max:  %16.4f%n", bounds.getMaxY());
    ps.format("   Max Circumcircle Radius:  %6.4f%n", maxRadius);
    ps.format("   Data Sample Bounds%n");
    ps.format("      x min:  %16.4f%n", sampleBounds.getMinX());
    ps.format("      y min:  %16.4f%n", sampleBounds.getMinY());
    ps.format("      x max:  %16.4f%n", sampleBounds.getMaxX());
    ps.format("      y max:  %16.4f%n", sampleBounds.getMaxY());
  }

  /**
   * Gets the bounds of the limited Voronoi Diagram. If the associated Delaunay
   * Triangulation included "skinny" triangles along its perimeter, the Voronoi
   * Diagram's bounds may be substantially larger than those of the original
   * input data set
   *
   * @return a valid rectangle
   */
  public Rectangle2D getBounds() {
    return new Rectangle2D.Double(
            bounds.getX(),
            bounds.getY(),
            bounds.getWidth(),
            bounds.getHeight());
  }

  /**
   * Gets the bounds of the sample data set. These will usually be smaller than
   * the bounds of the overall structure.
   *
   * @return a valid rectangle
   */
  public Rectangle2D getSampleBounds() {
    return new Rectangle2D.Double(
            sampleBounds.getX(),
            sampleBounds.getY(),
            sampleBounds.getWidth(),
            sampleBounds.getHeight());
  }

  /**
   * Gets a list of the edges in the Voronoi Diagram. Applications are
   * <strong>strongly cautioned against modifying these edges.</strong>
   *
   * @return a valid list of edges
   */
  public List<IQuadEdge> getEdges() {
    return edgePool.getEdges();
  }

  /**
   * Gets a list of the vertices that define the Voronoi Diagram. This list is
   * based on the input set, though in some cases coincident or nearly
   * coincident vertices will be combined into a single vertex of type
   * VertexMergerGroup.
   *
   * @return a valid list
   */
  public List<Vertex> getVertices() {
    List<Vertex> vList = new ArrayList<>(polygons.size());
    for (ThiessenPolygon p : polygons) {
      vList.add(p.getVertex());
    }
    return vList;
  }

  /**
   * Gets the vertices that were created to produce the Voronoi Diagram. The
   * output does not include the original vertices from the input source
   *
   * @return a valid list of vertices
   */
  public List<Vertex> getVoronoiVertices() {
    List<Vertex> list = new ArrayList<>(circleList.size());
    list.addAll(circleList);
    return list;
  }

  /**
   * Gets a list of the polygons that comprise the Voronoi Diagram
   *
   * @return a valid list of polygons
   */
  public List<ThiessenPolygon> getPolygons() {
    List<ThiessenPolygon> list = new ArrayList<>(polygons.size());
    list.addAll(polygons);
    return list;
  }

  /**
   * Gets the polygon that contains the specified coordinate point (x,y).
   * <p>
   * <strong>Note: </strong>Although a true Voronoi Diagram covers the entire
   * plane, the Limited Voronoi class is has a finite domain. If the specified
   * coordinates are outside the bounds of this instance, no polygon will be
   * found and a null result will be returned.
   *
   * @param x a valid floating point value
   * @param y a valid floating point value
   * @return the containing polygon or a null if none is found.
   */
  public ThiessenPolygon getContainingPolygon(double x, double y) {
    // The containing polygon is simply the one with the vertex
    // closest to the specified coordinates (x,y).
    ThiessenPolygon minP = null;
    if (bounds.contains(x, y)) {
      double minD = Double.POSITIVE_INFINITY;

      for (ThiessenPolygon p : polygons) {
        Vertex v = p.getVertex();
        double d = v.getDistanceSq(x, y);
        if (d < minD) {
          minD = d;
          minP = p;
        }
      }
    }
    return minP;
  }
}
