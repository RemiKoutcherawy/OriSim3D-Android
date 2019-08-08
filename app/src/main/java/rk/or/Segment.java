package rk.or;

import java.io.Serializable;

/** 
 * Segment to hold Segments
 * Two points p1 p2 
 */
public class Segment implements Comparable<Segment>, Serializable {
  private static final long serialVersionUID = 1L;
  private static final double EPSILON = 0.1;
  public Point p1, p2;
  public int id, type;
  public boolean select, highlight;
  public float lg2d, lg3d, angle;
  public static final int PLAIN=0, EDGE=1, MOUNTAIN=2, VALLEY=3, TEMPORARY=-1;

  /** Construct a segment */
  public Segment() {}
  
  /** Set from 2 points */
  public Segment setFrom2PointsTypeId(Point p1, Point p2, int type, int id) {
    this.p1 = p1;
    this.p2 = p2;
    this.type = type;
    this.lg2d =  (float) Math.sqrt((p1.xf-p2.xf)*(p1.xf-p2.xf)+ (p1.yf-p2.yf)*(p1.yf-p2.yf));
    this.id = id;
		return this;
  }
  /** Set segment with two new points (use only in Vector computations) */
  public Segment setFrom2Points(Point a, Point b){
  	this.p1 = new Point().setFromPointId(a, -1);
  	this.p2 = new Point().setFromPointId(b, -1);
  	this.type =TEMPORARY;
  	this.id = -1;
		return this;
  }
	/** Compares this segment with o other segment */
  public int compareTo(Segment o) {
    return (int) compareTo(o.p1, o.p2);
  }
  /** Override to select segment from existing segments */
  public boolean equals(Point a, Point b) {
	  return p1.id == a.id && p2.id == b.id;
  }
  /** Compare this segment to two points a, b */
  public float compareTo(Point a, Point b) {
    float d1 = p1.compareTo(a);
    float d2 = p2.compareTo(b);
    float d = 0.0f;
    if (Math.abs(d1) > 3)
      d = d1;
    else if (Math.abs(d2) > 3)
      d = d2 ;
    return d;
  }
	/** Reverse order of the 2 points of this segment */
  public void reverse() {
    Point p = p1;
    p1 = p2;
    p2 = p;
  }
  /** 3D Length in space */
  public float lg3d() {
    lg3d = (float) Math.sqrt((p1.x-p2.x)*(p1.x-p2.x)
        + (p1.y-p2.y)*(p1.y-p2.y)
        + (p1.z-p2.z)*(p1.z-p2.z));
    return lg3d;
  }
  /** 2D Length in flat view */
  public float lg2d() {
    lg2d = (float) Math.sqrt((p1.xf-p2.xf)*(p1.xf-p2.xf)
        + (p1.yf-p2.yf)*(p1.yf-p2.yf));
    return lg2d;
  }
  /** Closest points from this to s returned as a new segment */
  public Segment closest(Segment s){
		// On this segment we have : S1(t1)=p1+t1*(p2-p1)       = p1+t1*v1   = p
		// On v argument we have   : S2(t2)=v.p1+t2*(v.p2-v.p1) = s.p2+t2*v2 = q
  	// Vector pq perpendicular to both lines : pq(t1,t2).v1=0  pq(t1,t2).v2=0
  	// Cramer system : 
  	// (v1.v1)*t1 - (v1.v2)*t2 = -v1.r <=> a*t1 -b*t2 = -c
  	// (v1.v2)*t1 - (v2.v2)*t2 = -v2.r <=> b*t1 -e*t2 = -f
  	// Solved to t1=(bf-ce)/(ae-bb) t2=(af-bc)/(ae-bb)
  	float t1, t2;
  	Point v1 = new Point().setFrom3D(p2.x-p1.x, p2.y-p1.y, p2.z-p1.z); // this direction
  	Point v2 = new Point().setFrom3D(s.p2.x-s.p1.x, s.p2.y-s.p1.y, s.p2.z-s.p1.z); // s direction
  	Point r = new Point().setFrom3D(p1.x-s.p1.x, p1.y-s.p1.y, p1.z-s.p1.z); // s.p1 to this.p1
  	float a  = v1.dot(v1); // squared length of this
  	float e  = v2.dot(v2); // squared length of s
  	float f  = v2.dot(r);  // 
  	// Check degeneration of segments into points
  	if (a <= EPSILON && e <= EPSILON){
  		// Both degenerate into points
  		t1 = t2 = 0.0f;
  		return new Segment().setFrom2PointsTypeId(p1, s.p1,Segment.TEMPORARY, -1);
  	}
  	if (a <= EPSILON){
  		// This segment degenerate into point
  		t1 = 0.0f;
  		t2 = f / e; // t1=0 => t2 = (b*t1+f)/e = f/e
  		t2 = t2 < 0 ? 0: t2 > 1 ? 1 : t2;
  	} else {
  		float c  = v1.dot(r);
  		if (e <= EPSILON){
  			// Second segment degenerate into point
  			t2 = 0.0f;
  			t1 = -c / a; // t2=0 => t1 = (b*t2-c)/a = -c/a
    		t1 = t1 < 0 ? 0: t1 > 1 ? 1 : t1;
  		} else {
  			// General case
  			float b  = v1.dot(v2); // Delayed computation of b
  			float denom = a*e-b*b; // Denominator of cramer system
  			// Segments not parallel, compute closest and clamp
  			if (denom != 0.0f) {
  				t1 = (b*f-c*e) / denom;
      		t1 = t1 < 0 ? 0: t1 > 1 ? 1 : t1;
  			} else {
  				// Arbitrary point, here 0 => p1
  				t1 = 0;
  			}
  			// Compute closest on L2 using
				t2 = (b * t1 + f) / e;
				// if t2 in [0,1] done, else clamp t2 and recompute t1
				if (t2 < 0.0f) {
					t2 = 0.0f;
					t1 = -c/a;
	    		t1 = t1 < 0 ? 0: t1 > 1 ? 1 : t1;
				} else if (t2 > 1.0f){
					t2 = 1.0f;
					t1 = (b-c)/a;
	    		t1 = t1 < 0 ? 0: t1 > 1 ? 1 : t1;
				}
  		}
  	}
  	Point c1 = p1.add(v1.scaleThis(t1)); // c1 = p1+t1*(p2-p1) 
  	Point c2 = s.p1.add(v2.scaleThis(t2)); // c2 = p1+t2*(p2-p1)
		return new Segment().setFrom2Points(c1, c2);
  }
  /** Closest points from this(line) to s(line) returned as a new segment */
  public Segment closestLine(Segment s){
		// On this segment we have : S1(t1)=p1+t1*(p2-p1)       = p1+t1*v1   = p
		// On v argument we have   : S2(t2)=v.p1+t2*(v.p2-v.p1) = s.p2+t2*v2 = q
  	// Vector pq perpendicular to both lines : pq(t1,t2).v1=0  pq(t1,t2).v2=0
  	// Cramer system : 
  	// (v1.v1)*t1 - (v1.v2)*t2 = -v1.r <=> a*t1 -b*t2 = -c
  	// (v1.v2)*t1 - (v2.v2)*t2 = -v2.r <=> b*t1 -e*t2 = -f
  	// Solved to t1=(bf-ce)/(ae-bb) t2=(af-bc)/(ae-bb)
  	float t1, t2;
  	Point v1 = new Point().setFrom3D(p2.x-p1.x, p2.y-p1.y, p2.z-p1.z); // this direction
  	Point v2 = new Point().setFrom3D(s.p2.x-s.p1.x, s.p2.y-s.p1.y, s.p2.z-s.p1.z); // s direction
  	Point r = new Point().setFrom3D(p1.x-s.p1.x, p1.y-s.p1.y, p1.z-s.p1.z); // s.p1 to this.p1
  	float a  = v1.dot(v1); // squared length of this
  	float e  = v2.dot(v2); // squared length of s
  	float f  = v2.dot(r);  // 
  	// Check degeneration of segments into points
  	if (a <= EPSILON && e <= EPSILON){
  		// Both degenerate into points
  		t1 = t2 = 0.0f;
  		return new Segment().setFrom2PointsTypeId(p1, s.p1,Segment.TEMPORARY, -1);
  	}
  	if (a <= EPSILON){
  		// This segment degenerate into point
  		t1 = 0.0f;
  		t2 = f / e; // t1=0 => t2 = (b*t1+f)/e = f/e
  	} else {
  		float c  = v1.dot(r);
  		if (e <= EPSILON){
  			// Second segment degenerate into point
  			t2 = 0.0f;
  			t1 = -c / a; // t2=0 => t1 = (b*t2-c)/a = -c/a
  		} else {
  			// General case
  			float b  = v1.dot(v2); // Delayed computation of b
  			float denom = a*e-b*b; // Denominator of cramer system
  			// Segments not parallel, compute closest
  			if (denom != 0.0f) {
  				t1 = (b*f-c*e) / denom;
  			} else {
  				// Arbitrary point, here 0 => p1
  				t1 = 0;
  			}
  			// Compute closest on L2 using
				t2 = (b * t1 + f) / e;
  		}
  	}
  	Point c1 = p1.add(v1.scaleThis(t1)); // c1 = p1+t1*(p2-p1) 
  	Point c2 = s.p1.add(v2.scaleThis(t2)); // c2 = p1+t2*(p2-p1)
		return new Segment().setFrom2Points(c1, c2);
  }
}
