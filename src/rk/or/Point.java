package rk.or;

import java.io.Serializable;

/** 
 * Point to hold Points 
 * 3D : x y z
 * Flat CreasePattern : xf, yf 
 * View 2D : xv, yv (unused in android)
 */
public class Point extends Vector3D implements Comparable<Point>, Serializable {
  private static final long serialVersionUID = 1L;
  public float xf,yf; // x y Flat, in unfolded state
  //  public float x,y,z; // x y z 3D defined in super class Vector3D
  public int xv, yv; // x y z 3D projected in 2D
  public int id, type;
  public boolean select, highlight, fixed;
  
  /** Constructs a point with 2D coordinates, z = 0 */
  public Point(float x, float y, int id) {
    super(x, y, 0);
    this.xf = x;   this.yf = y;
    this.x = x;   this.y  = y;    this.z = 0;
    this.id = id;
  }
  /** Constructs a point with 3D coordinates */
  public Point(float x, float y, float z, int id) {
    super(x, y, z);
    this.xf = Float.POSITIVE_INFINITY;
    this.yf = Float.POSITIVE_INFINITY;
    this.x = x;   this.y  = y;    this.z = z;
    this.id = id;
  }
  public Point(Vector3D v, int id) {
    super(v.x, v.y, v.z);
    this.xf = x;   this.yf = y;
    this.id = id;
  }
  /** We Override to select point from existing points */
  public boolean equals(Object p) {
	  return this.id == ((Point)p).id;
  }
	/** Compares this points with arg in 2D */
  public int compareTo(Point p) {
    return (int)compareTo(p.x,p.y,p.z);
  }
  /** Compare this point with x,y in 2D */
  public float compareTo(float x, float y) {
    float dx2 = (float) Math.abs((this.xf - x)*(this.xf - x));
    float dy2 = (float) Math.abs((this.yf - y)*(this.yf - y));
    float d = (float) Math.sqrt(dx2 + dy2);
    if (d > 3.0f) // Points closer than 3 pixels are the same
    	return d;
    return 0;
  }
}
