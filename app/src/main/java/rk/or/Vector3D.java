package rk.or;

import java.io.Serializable;

/** 
 * A simple vector to hold 3 coordinates
 * x, y, z float values
 */
public class Vector3D implements Serializable {
  private static final long serialVersionUID = 1L;
  public float x, y, z;

  /** New vector with coordinates x,y,z */ 
  public Vector3D(float x, float y, float z) {
    this.x = x;  this.y = y;  this.z = z;
  }
  /** New vector with coordinates of v */
  public Vector3D(Vector3D v) {
    this.x = v.x;    this.y = v.y;    this.z = v.z;
  }
  /** Sets this vector with coordinates x,y,z */
	public void set(float x, float y, float z) {
    this.x = x;  this.y = y;  this.z = z;
  }
  /** Sets this vector with coordinates v */
  public void set(Vector3D v) {
    this.x = v.x;    this.y = v.y;    this.z = v.z;
  }
  /** Sets this vector with coordinates of p */
  public void moveTo(Vector3D p) {
    x = p.x; y = p.y; z = p.z;
  }
  /** Return a new Vector this * t */
  public Vector3D scale(float t) {
    return (new Vector3D(x*t, y*t, z*t));
  }
  /** Return this * t */
  public Vector3D scaleThis(float t) {
    x*=t; y*=t; z*=t;
    return this;
  }
  /** New Vector from this to B */
  public final Vector3D to(Vector3D B) {
    return (new Vector3D(B.x-x, B.y-y, B.z-z));
  }
  /** New Vector this + A */
  public Vector3D add(Vector3D A) {
    return (new Vector3D(A.x+x, A.y+y, A.z+z));
  }
  /** This Vector this + A */
  public Vector3D addToThis(Vector3D A) {
  	x+=A.x; y+=A.y; z+=A.z;
    return this;
  }
  /** This Vector this - A */
  public Vector3D subToThis(Vector3D A) {
  	x-=A.x; y-=A.y; z-=A.z;
    return this;
  }
  /** Dot this with B */
  public final float dot(Vector3D B) {
    return (x*B.x+y*B.y+z*B.z);
  }
  /** Dot this with Bx,By,Bz */
  public final float dot(float Bx, float By, float Bz) {
    return (x*Bx+y*By+z*Bz);
  }
  /** Dot A.B  */
  public static final float dot(Vector3D A, Vector3D B) {
    return (A.x*B.x+A.y*B.y+A.z*B.z);
  }
  /** New Vector Cross this with B */
  public final Vector3D cross(Vector3D B) {
    return new Vector3D(y*B.z-z*B.y, z*B.x-x*B.z, x*B.y-y*B.x);
  }
  /** New Vector Cross this with Bx, By, Bz */
  public final Vector3D cross(float Bx, float By, float Bz) {
    return new Vector3D(y*Bz-z*By, z*Bx-x*Bz, x*By-y*Bx);
  }
  /** New Vector Cross AxB */
  public final static Vector3D cross(Vector3D A, Vector3D B) {
    return new Vector3D(A.y*B.z-A.z*B.y, A.z*B.x-A.x*B.z, A.x*B.y-A.y*B.x);
  }
  /** New Vector Cross AxB */
  public final static float ScalarTriple(Vector3D A, Vector3D B, Vector3D C) {
    return (A.y*B.z-A.z*B.y)*C.x + (A.z*B.x-A.x*B.z)*C.y + (A.x*B.y-A.y*B.x)*C.z;
  }
  /** Sqrt(this.this) */
  public final float length() {
    return (float) Math.sqrt(x*x+y*y+z*z);
  }
  /** Squared Length = dot(this.this) */
  public final float lengthSquared() {
    return x*x+y*y+z*z;
  }
  /** Squared(A.A) */
  public final static float length(Vector3D A) {
    return (float) Math.sqrt(A.x*A.x+A.y*A.y+A.z*A.z);
  }
  /** This/Squared(this.this) */
  public final void normalize() {
    float t = x * x + y * y + z * z;
    if (t != 0 && t != 1)
      t = (float) (1.0 / Math.sqrt(t));
    x *= t;
    y *= t;
    z *= t;
  }
  /** New Vector A/sqrt(A.A) */
  public final static Vector3D normalize(Vector3D A) {
    float t = A.x * A.x + A.y * A.y + A.z * A.z;
    if (t != 0 && t != 1)
      t = (float)(1.0 / Math.sqrt(t));
    return new Vector3D(A.x * t, A.y * t, A.z * t);
  }
  
  /** Compares two points in 3D
   * @return Negative if this down or left of P, 
   * Positive if this up or right of P, 0 equals */
  public float compareTo(Vector3D p) {
    return compareTo(p.x ,p.y, p.z);
  }
  /** Return 0 if Point is near x,y,z */
  public float compareTo(float x, float y, float z) {
    float dx2 = (float) Math.abs((this.x - x)*(this.x - x));
    float dy2 = (float) Math.abs((this.y - y)*(this.y - y));
    float dz2 = (float) Math.abs((this.z - z)*(this.z - z));
    float d = (float) Math.sqrt(dx2 + dy2 + dz2);
    if (d > 3) // Points closer than 3 pixels are considered the same 
    	return d;
    else return 0.0f;
  }

}
