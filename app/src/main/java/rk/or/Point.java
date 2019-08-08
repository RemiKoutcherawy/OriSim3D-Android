package rk.or;

import java.io.Serializable;

/** 
 * Point to hold Points 
 * 3D : x y z
 * Flat CreasePattern : xf, yf 
 * View 2D : xv, yv (unused in android)
 */
public class Point implements Comparable<Point>, Serializable {
  private static final long serialVersionUID = 1L;
  public float xf,yf; // x y Flat, in unfolded state
  public float x,y,z; // x y z 3D
  public int xv, yv; // x y z 3D projected in 2D
  public int id, type;
  public boolean select, highlight, fixed;
  
  /** Constructs a point */
  public Point () {}
  
  /** Set from 3D coordinates */
  public Point setFrom3D(float x, float y, float z) {
    this.x = x;  
    this.y = y;  
    this.z = z;
		return this;
  }
  /** Set from 3D coordinates and Id */
  public Point setFrom3DId(float x, float y, float z, int id) {
    this.x = x;  
    this.y = y;  
    this.z = z;
    this.xf = Float.POSITIVE_INFINITY;
    this.yf = Float.POSITIVE_INFINITY;
    this.id = id;
		return this;
  }
  /** Set from 2D coordinates, z = 0 and Id */
  public Point setFrom2DId(float x, float y, int id) {
    this.x = x;  this.y = y;  this.z = 0;
    this.xf = x;   
    this.yf = y;
    this.x = x;   
    this.y = y;    
    this.z = 0;
    this.id = id;
		return this;
  }
  public Point setFromPointId(Point v, int id) {
    this.x = v.x;  this.y = v.y;  this.z = v.z;
    this.xf = x;   this.yf = y;
    this.id = id;
		return this;
  }
  /** New Vector this + A */
  public Point add(Point A) {
    return new Point().setFrom3DId(A.x+x, A.y+y, A.z+z, 0);
  }
  /** This Vector this + A */
  public Point addToThis(Point A) {
  	x+=A.x; y+=A.y; z+=A.z;
    return this;
  }
  /** Return this * t */
  public Point scaleThis(float t) {
    x*=t; y*=t; z*=t;
    return this;
  }
  /** Dot this with B */
  public final float dot(Point B) {
    return (x*B.x+y*B.y+z*B.z);
  }
  /** New Vector from this to B */
  public final Point to(Point B) {
    return (new Point().setFrom3D(B.x-x, B.y-y, B.z-z));
  }
  /** Sqrt(this.this) */
  public final float length() {
    return (float) Math.sqrt(x*x+y*y+z*z);
  }
  /** Override to select point from existing points */
  public boolean equals(Object p) {
	  return this.id == ((Point)p).id;
  }
  /** Compares two points in 3D
   * @return 0 if closer than 3 pixels */
  public float compareTo(float x, float y, float z) {
    float dx2 = (float) Math.abs((this.x - x)*(this.x - x));
    float dy2 = (float) Math.abs((this.y - y)*(this.y - y));
    float dz2 = (float) Math.abs((this.z - z)*(this.z - z));
    float d = (float) Math.sqrt(dx2 + dy2 + dz2);
    if (d > 3) // Points closer than 3 pixels are considered the same 
    	return d;
    else return 0.0f;
  }
	/** Compares this points with arg in 2D */
  public int compareTo(Point p) {
    return (int) compareTo(p.x,p.y,p.z);
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
