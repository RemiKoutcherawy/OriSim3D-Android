package rk.or;

/** 
 * Plane defined by an origin point R and a normal vector N
 * a point P is in plane iff RP.N = 0 that is if OP.N = d with d = OR.N 
 */
public class Plane {
  Point n;
  float d;
  final static float THICKNESS = 10f;
  
  /** Define a plane across 2 points */
  public Plane across(Point p1, Point p2){
    Point o = new Point().setFrom3D((p1.x+p2.x)/2, (p1.y+p2.y)/2, (p1.z+p2.z)/2);
    n = new Point().setFrom3D(p2.x-p1.x, p2.y-p1.y, p2.z-p1.z);
    d = o.dot(n);
    return this;
  }
  /** Define a plane by 2 points along Z */
  public Plane by(Point p1, Point p2) {
    Point o = new Point().setFrom3D(p1.x, p1.y, p1.z);
    n = new Point().setFrom3D(-(p2.y-p1.y), (p2.x-p1.x), 0);
    d = o.dot(n);
    return this;
  }
  /** Plane orthogonal to Segment and passing by Point */
  public  Plane ortho(Segment s, Point p){
    n = new Point().setFrom3D(s.p2.x-s.p1.x, s.p2.y-s.p1.y, s.p2.z-s.p1.z);
    d = p.dot(n);
    return this;
  }
  /** Intersection of This plane with Segment */
  public Point intersect(Segment s){
    // (A+tAB).N=d <=> t=(d-A.N)/(AB.N) then Q=A+tAB 0<t<1
    Point ab = new Point().setFrom3D(s.p2.x-s.p1.x, s.p2.y-s.p1.y, s.p2.z-s.p1.z);
    float abn = ab.dot(n);
    if (abn == 0)
      return null;
    float t = (d-s.p1.dot(n)) / abn;
    if (t >=0 && t <= 1.0)
      return ab.scaleThis(t).addToThis(s.p1);
    return null;
  }
  /** Intersection of This plane with segment defined by two points */
  public Point intersect(Point a, Point b) {
    // (A+tAB).N=d <=> t=(d-A.N)/(AB.N) then Q=A+tAB 0<t<1
    Point ab = new Point().setFrom3D(b.x-a.x, b.y-a.y, b.z-a.z);
    float abn = ab.dot(n);
    // segment parallel to plane
    if (abn == 0) 
    	return null;
    // segment crossing
    float t = (d-a.dot(n)) / abn;
    if (t >= 0 && t <= 1.0) 
      return ab.scaleThis(t).addToThis(a);
    return null;
  }
  /** Classify point to thick plane 1 in front 0 on -1 behind */
  public int classifyPointToPlane(Point i) {
    // (A+tAB).N = d <=> d<e front, d>e behind, else on plane   
  	float dist = d - this.n.dot(i);
  	if (dist > THICKNESS)
  		return 1;
  	if (dist < -THICKNESS)
  		return -1;
  	return 0;
  }
  /** Return Pl normal:n distance:d */
	@Override
  public String toString() {
	  return "Pl n:"+n+" d:"+d;
  }
  
}
