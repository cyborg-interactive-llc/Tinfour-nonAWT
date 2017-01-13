/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * 07/2015  G. Lucas     Created
 *
 * Notes:
 * The layout of this class is intended to accomplish the following:
 *  a) conserve memory in applications with a very large number of edges
 *  b) support the use of an edge pool by providing an application ID field.
 *
 * The memory layout is based on the following ideas
 *  a) In most JVM's,  the size in memory of an object must be a multiple
 *     of eight.  So an object containing one byte would still have to
 *     be eight bytes large in memory.
 *  b) In addition to the memory required by the object itself, all objects
 *     require a certain amount of memory to control the allocation of
 *     objects on the heap. This value is out-of-the-control of any
 *     code and is JVM-implementation-dependent.  Eight bytes appear to be
 *     the minimum.
 *  c) integers are 4 bytes. doubles 8. floats 4.
 *  d) although it is implementation dependent, many Java JVM's can represent
 *     object references in 4 bytes provided the memory size of the JVM is
 *     less than 32 gigabytes.  Although the normal range of a 4 byte memory
 *     address would be 4 gigabytes, some of the major JVM's (Hotspot for
 *     Windows) take advantage of the fact that objects are aligned in memory
 *     on 8-byte boundaries to "compress"  references by dividing them by 8
 *     when storing them in an object and multiplying them by eight when
 *     accessing them.
 *       so assume references are 4 bytes.
 *  e) all objects include a reference to their own class definition.
 *
 *  So this class contains the following fundamental member elements
 *  all of which are 4 bytes in JVMs where references are compressed:
 *
 *      reference to the class        (4)
 *      reference to the dual         (4)
 *      reference to the vertex       (4)
 *      reference to the forward edge (4)
 *      reference to the reverse edge (4)
 *           total                    20
 *
 *  Since the size of the class must be a multiple of 8, that leaves 4
 *  bytes which will be allocated in memory no matter what.  So we
 *  put that to use by defining an integer application data element
 *
 *      integer index                (4)
 *          total                     24
 *
 * The index element of this class is used to assign a unique integer value
 * to each edge created in the TIN-building process or other applications.
 * In the case of the EdgePool, it is used to manage allocation of edges.
 * If instances are not managed by an EdgePool, the index value is free
 * for use in other interpretations.
 *
 * CONSTRAINTS
 *   In QuadEdgePartner, the index value is used as a way of indicating
 * whether the edge is a constrained edge according to the definition
 * of a Constrained Delaunay Triangulation.   To conserve memory and
 * keep the size of the class small, the low order two bytes are
 * allocated to indicating the "constraint index". The use of a constraint
 * index is also intended to support operations in which the constraint index
 * of an edge can be traced back to the constraint that defined it.
 * So, typically, the constraint index is an index back to the list of
 * constraints that was added to the incremental-TIN implementation.
 * If the lower two bytes of the QuadEdgePartner's index element are clear,
 * so that (index&0xffff) == 0, the edge is considered not constrained.  When
 * the constraint-index is set using the setConstraint() method, the code adds
 * one to the value stored. The getConstraintIndex() method masks out the
 * index field and subtracts a value of 1 from the result before returning it.
 * The consequence of this design choice is that the maximum value that can
 * be stored in the low-order two bytes of the index element is (2^16-1)-1,
 * or 65534...
 *
 * Special considerations for setForward() and setReverse()
 *   Even though this class does implement the IQuadEdge interface, the
 * setForward() and setReverse() methods do not accept IQuadEdge as an
 * interface.  In an earlier version, there was an experiment that used
 * IQuadEdge for the forward and reverse member elements, however the overhead
 * due to Java type casting resulted in a 20 percent degradation in performance.
 * -----------------------------------------------------------------------
 */
package tinfour.common;

/**
 * A representation of an edge with forward and reverse links on one
 * side and counterpart links attached to its dual (other side).
 * <p>This concept is based on the structure popularized by
 * <cite>Guibas, L. and Stolfi, J. (1985) "Primitives for the
 * manipulation of subdivisions and the computation of Voronoi diagrams"
 * ACM Transactions on Graphics, 4(2), 1985, p. 75-123.</cite>
 */
public class QuadEdge implements IQuadEdge {

  /**
   * The maximum value of a constraint index based in space
   * allocated for its storage. This is a value of (2^20-1).
   * In practice this value is larger than the available
   * memory on many contemporary computers would allow.
  */
  public static final int CONSTRAINT_INDEX_MAX = 1048575;

  /**
   * A mask that can be anded with the QuadEdgePartner's
   * index field to extract the constraint index,
   * equivalent to the 20 low-order bits.
   */
  public static final int CONSTRAINT_INDEX_MASK = 0x000fffff;

  /**
   * A bit indicating that an edge is constrained. This bit just happens
   * to be the sign bit, a feature that is exploited by the isConstrained()
   * method.
   */
  public static final int CONSTRAINT_FLAG = (1<<31);

  /**
   * A bit indicating that an edge is part of a constrained area.
   */
  public static final int CONSTRAINT_AREA_FLAG = (1<<30);

  /**
   * A bit indicating that the constrained area is to the base side
   * of the edge.  This bit is only meaningful when CONSTRAINT_AREA_FLAG is set.
   * If CONSTRAINT_AREA_FLAG is set, then this bit tells which side the
   * constraint area lies on: if the bit is set, it's on the base side
   * and if the bit is clear, it's on the dual side.
   */
  public static final int CONSTRAINT_AREA_BASE_FLAG = (1<<29);

  /**
   * An arbitrary index value. For IncrementalTin, the index
   * is used to manage the edge pool.
   */
  int index;

  /**
   * The dual of this edge (always valid, never null.
   */
  QuadEdge dual;
  /**
   * The initial vertex of this edge, the second vertex of
   * the dual.
   */
  Vertex v;
  /**
   * The forward link of this edge.
   */
  QuadEdge f;
  /**
   * The reverse link of this edge.
   */
  QuadEdge r;

  /**
   * Constructs the edge and its dual.
   */
  QuadEdge() {
    dual = new QuadEdgePartner(this);
  }

  /**
   * Construct the edge setting its dual with the specfied reference.
   * @param partner a valid element.
   */
  QuadEdge(final QuadEdge partner) {
    dual = partner;
  }

  /**
   * Construct the edge and its dual assigning the pair the specified index.
   * @param index an arbitrary integer value.
   */
  public QuadEdge(final int index) {
    dual = new QuadEdgePartner(this);
    this.index = index;
  }

  /**
   * Sets the vertices for this edge (and its dual).
   * @param a the initial vertex, must be a valid reference.
   * @param b the second vertex, may be a valid reference or a
   * null for a ghost edge.
   */
  public void setVertices(final Vertex a, final Vertex b) {
    this.v = a;
    this.dual.v = b;
  }

  /**
   * Gets the initial vertex for this edge.
   * @return a valid reference.
   */
  @Override
  public final Vertex getA() {
    return v;
  }

  /**
   * Sets the initial vertex for this edge.
   * @param a a valid reference.
   */
  public final void setA(final Vertex a) {
    this.v = a;
  }

  /**
   * Gets the second vertex for this edge.
   * @return a valid reference or a null for a ghost edge.
   */
  @Override
  public final Vertex getB() {
    return dual.v;
  }

  /**
   * Sets the second (B) vertex for this edge (also the A reference of
   * the dual edge).
   * @param b a valid reference or a null for a ghost edge.
   */
  public final void setB(final Vertex b) {
    dual.v = b;
  }

  /**
   * Gets the forward reference of the edge.
   * @return a valid reference.
   */
  @Override
  public final QuadEdge getForward() {
    return f;
  }

  /**
   * Gets the reverse reference of the edge.
   * @return a valid reference.
   */
  @Override
  public final QuadEdge getReverse() {
    return r;
  }

  /**
   * Gets the forward reference of the dual.
   * @return a valid reference
   */
  @Override
  public final QuadEdge getForwardFromDual() {
    return dual.f;
  }

  /**
   * Gets the reverse link of the dual.
   * @return a valid reference
   */
  @Override
  public final QuadEdge getReverseFromDual() {
    return dual.r;
  }

  /**
   * Gets the dual of the reverse link.
   * @return a valid reference
   */
  public final QuadEdge getDualFromReverse(){
    return r.dual;
  }

  /**
   * Sets the forward reference for this edge.
   * @param e a valid reference
   */
  public final void setForward(final QuadEdge e) {
    this.f = e;
    e.r = this;
    // forwardCheck(this, e);
  }

  /**
   * Sets the reverse reference for this edge.
   * @param e a valid reference
   */
  public final void setReverse(final QuadEdge e) {
    this.r = e;
    e.f = this;
    // forwardCheck(e, this);
  }

  /**
   * Sets the forward link to the dual of this edge.
   * @param e a valid reference
   */
  public final void setDualForward(final QuadEdge e) {
    dual.f = e;
    e.r = dual;
    // forwardCheck(dual, e);
  }

  /**
   * Sets the reverse link of the dual to this edge.
   * @param e a valid reference
   */
  public final void setDualReverse(final QuadEdge e) {
    dual.r = e;
    e.f = dual;
    // forwardCheck(e, dual);
  }

  /**
   * Gets the dual edge to this instance.
   * @return a valid edge.
   */
  @Override
  public final QuadEdge getDual() {
    return dual;
  }

  /**
   * Gets the index value for this edge.
   * @return an integer value
   */
  @Override
  public int getIndex() {
    return index;
  }

  /**
   * Sets the index value for this edge. In the IncrementalTin application,
   * this index is used for managing the EdgePool.
   * @param index an integer value
   */
  public void setIndex(final int index) {
    this.index = index;
  }


  /**
   * Gets the reference to the side-zero edge of the pair.
   * @return a link to the side-zero edge of the pair.
   */
  @Override
  public QuadEdge getBaseReference() {
    return this;
  }

  /**
   * Gets the index of the constraint associated with this edge.
   * Constraint index values must be in the range 0 to Integer.MAX_VALUE,
   * with negative numbers being reserved for internal use by the
   * Tinfour library,
   *
   * @return if constrained, a positive integer; otherwise, a negative value.
   */
  @Override
  public int getConstraintIndex() {
    return dual.getConstraintIndex();
  }


  @Override
  public void setConstraintIndex(int constraintIndex) {
    dual.setConstraintIndex(constraintIndex);
  }

  /**
   * Gets the index of the constrain associated with
   *
   * @return true if the edge is constrained; otherwise, false.
   */
  @Override
  public boolean isConstrained() {
    return dual.isConstrained();
  }

  @Override
  public void setConstrained(int constraintIndex){
    dual.setConstrained(constraintIndex);
  }

  /**
   * Sets all vertices and link references to null (the link to a dual
   * is not affected).
   */
  public void clear() {
    // note that the index of the partner is set to -1,
    // but the index of the base, which is used for management purposes
    // is left alone.
    this.v = null;
    this.f = null;
    this.r = null;
    dual.v = null;
    dual.f = null;
    dual.r = null;
    dual.index = 0;
  }

  /**
   * Gets a name string for the edge by prepending the index value
   * with a + or - string depending on its side (+ for side zero, - for side 1).
   * @return a valid string.
   */
  String getName() {
    char c;
    if (getSide() == 0) {
      c = '+';
    } else {
      c = '-';
    }
    return Integer.toString(getIndex()) + c;
  }

  @Override
  public String toString() {
    Vertex a = v;
    Vertex b = dual.v;
    if (a == null && b == null) {
      return String.format("%9d/%d  -- Undefined", getIndex(), getSide());
    }
    String s = String.format("%9s  %9s <-- (%9s,%9s) --> %9s%s",
      getName(),
      (r == null ? "null" : r.getName()),
      (a == null ? "gv" : a.getLabel()),
      (b == null ? "gv" : b.getLabel()),
      (f == null ? "null" : f.getName()),
      (this.isConstrained()?"    constrained":"")
    );
    return s;
  }


  /**
   * Gets the length of the edge.
   * @return a positive floating point value
   */
  @Override
  public double getLength() {
    if (v == null || dual.v == null) {
      return Double.NaN;
    }
    double dx = v.x - dual.v.x;
    double dy = v.y - dual.v.y;
    return Math.sqrt(dx * dx + dy * dy);
  }

  /**
   * Indicates which side of an edge a particular QuadEdge instance is
   * attached to. The side value is a strictly arbitrary index used for
   * algorithms that need to be able to assign a unique index to
   * both sides of an edge.
   *
   * @return a value of 0 or 1.
   */
  @Override
  public int getSide() {
    return 0;
  }

  /**
   * An implementation of the equals method which check for a matching
   * reference.
   * @param o a valid reference or a null
   * @return true if the specified reference matches this.
   */
  @Override
  public boolean equals(Object o) {
    if (o instanceof QuadEdge) {
      return this == o;
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 11 * hash + this.index;
    return hash;
  }

  @Override
  public boolean isConstrainedAreaMember() {
    return dual.isConstrainedAreaMember();
  }

  @Override
  public boolean isConstrainedAreaEdge() {
    return dual.isConstrainedAreaEdge();
  }

  @Override
  public void setConstrainedAreaMemberFlag() {
    dual.index |= CONSTRAINT_AREA_FLAG | CONSTRAINT_AREA_BASE_FLAG;
  }


  public boolean isConstraintAreaOnThisSide(){
      return (dual.index&CONSTRAINT_AREA_BASE_FLAG)!=0;
  }

  @Override
  public Iterable<IQuadEdge>pinwheel(){
    return new QuadEdgePinwheel(this);
  }
}
