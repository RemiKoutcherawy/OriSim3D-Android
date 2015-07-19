package rk.or;

import java.util.LinkedList;
import java.util.List;
import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** 
 * Model to hold Points, Segments, Faces
 */
public class Model implements Serializable {
  private static final long serialVersionUID = 1L;
  public List<Point> points;   
  public List<Face> faces;
  public List<Segment> segments;
  public int idPoint, idSegment, idFace;
  private float currentScale = 1.0f;

  /** Constructs zero length arrays for points, faces, segments */
  public Model() {
    points = new LinkedList<Point>();
    faces = new LinkedList<Face>();
    segments = new LinkedList<Segment>();
    idPoint = idSegment = idFace = 0;
  }

  /** Initializes this orModel with a Square 200x200 CCW */
  public void init(float x0, float y0, float x1, float y1,
                   float x2, float y2, float x3, float y3) {
    points.clear(); faces.clear(); segments.clear();
    idPoint = idSegment = idFace = 0;
    currentScale = 1.0f;
    Point p1 = addPoint(x0, y0);    Point p2 = addPoint(x1, y1);
    Point p3 = addPoint(x2, y2);    Point p4 = addPoint(x3, y3);
    addSegment(p1, p2, Segment.EDGE);    addSegment(p2, p3, Segment.EDGE);
    addSegment(p3, p4, Segment.EDGE);    addSegment(p4, p1, Segment.EDGE);
    Face f = new Face();
    f.points.add(p1);     f.points.add(p2); 
    f.points.add(p3);     f.points.add(p4);
    addFace(f);
  }
  /** Adds a point to this Model or return the point at x,y */
  private Point addPoint(float x, float y) {
    // Create a new Point
    Point p = new Point(x, y, idPoint++);
    points.add(p);
    return p;
  }
  /** Adds a point to this Model or return the point at x,y,z  */
  private Point addPoint(Point pt) {
    // Search existing points
    for (Point p : points){
      if (p.compareTo(pt.x, pt.y, pt.z) == 0){
        if (p.compareTo(pt.xf, pt.yf) == 0){
          return p;
        }
      }
    }
    // Add new Point
    pt.id = idPoint++;
    points.add(pt); 
    return pt;
  }
  /** Adds a segment to this model */
  private Segment addSegment(Point p1, Point p2, int type) {
    Segment s = new Segment(p1, p2, type, idSegment++);
    segments.add(s);
    return s;
  }
  /** Adds a face */
  private void addFace(Face f) {
    faces.add(f);
    f.id = idFace++;
  }
  
  /** Splits Segment by a ratio k in  ]0 1[ counting from p1 */
  public void splitSegment(Segment s, float k){
    // Create new Point
    Point p = new Point(s.p1.x+k*(s.p2.x-s.p1.x),
        s.p1.y+k*(s.p2.y-s.p1.y),
        s.p1.z+k*(s.p2.z-s.p1.z), -1);
    splitSegmentOnPoint(s, p);
  }
  /** Split all or given faces Across two points */
  public void splitAcross(Point p1, Point p2, List<Face> list) {
    Plane pl = new Plane().across(p1, p2);
    splitFacesByPlane(pl,list);
  }
  /** Split all or given faces By two points */
  public void splitBy(Point p1, Point p2, List<Face> list) {
    Plane pl = new Plane().by(p1, p2);
    splitFacesByPlane(pl,list);
  }
  /** Split faces by a plane Perpendicular to a Segment passing by a Point "p" */
  public void splitOrtho(Segment s, Point p, List<Face> list) {
    Plane pl = new Plane().ortho(s, p);
    splitFacesByPlane(pl,list);
  }
  /** Split faces by a plane between two segments given by 3 points */
  public void splitLineToLine(Point p0, Point p1, Point p2, List<Face> list) {
    // Project p0 on p1 p2
    float p0p1 = (float) Math.sqrt((p1.x-p0.x)*(p1.x-p0.x)
        + (p1.y-p0.y)*(p1.y-p0.y)
        + (p1.z-p0.z)*(p1.z-p0.z));
    float p1p2 = (float) Math.sqrt((p1.x-p2.x)*(p1.x-p2.x)
        + (p1.y-p2.y)*(p1.y-p2.y)
        + (p1.z-p2.z)*(p1.z-p2.z));
    float k = p0p1 / p1p2;
    float x = p1.x+k*(p2.x-p1.x);
    float y = p1.y+k*(p2.y-p1.y);
    float z = p1.z+k*(p2.z-p1.z);
    // e is on p1p2 symmetric of p0
    Point e = new Point(x, y, z, -1);
    // Define Plane
    Plane pl = new Plane().across(p0, e);
    splitFacesByPlane(pl,list);
  }
  /** Split listed faces by a plane between two segments */
	public void splitLineToLine(Segment s1, Segment s2, List<Face> list) {
	  Segment s = s1.closestLine(s2);
	  if (s.lg3d() < 10) {
		  // Segments cross themselves at c Center
	  	Point c = s.p1;
	  	Point a = s1.p1.to(c).length() > s1.p2.to(c).length() ? s1.p1 : s1.p2;
	  	Point b = s2.p1.to(c).length() > s2.p2.to(c).length() ? s2.p1 : s2.p2;
			splitLineToLine(a, c, b, list);
	  } else {
	  	// Segments do not cross ... Very strange 
	  	Plane pl = new Plane().across(s.p1, s.p2);
	  	splitFacesByPlane(pl, list);
	  }
  }
	/** Split segments crossing on common point */
	public void splitSegmentCrossing(Segment s1, Segment s2) {
		Segment c = s1.closest(s2);
		// Segments cross themselves
		if (Math.abs(c.p1.compareTo(c.p2)) < 1) { // c.p1 near c.p2
			// Create new Point for the first segment
			Point p1 = new Point(c.p1.x, c.p1.y, c.p1.z, -1);
			splitSegmentOnPoint(s1, p1);
			// Create new Point for the second segment
			Point p2 = new Point(c.p1.x, c.p1.y, c.p1.z, -1);
			splitSegmentOnPoint(s2, p2);	  
		}	  
	}
	/** Split a segment on a point, add this point to the model, and faces containing this segment */
	private void splitSegmentOnPoint(Segment s1, Point p) {
	  // Add Point to s1
	  align2dFrom3d(p, s1);
	  // Add point P to first face.
	  Face l = searchFace(s1, null);
	  if (l != null && !l.points.contains(p)) {
	    // Add after P2 or P1 for the left face (CCW)
	    List<Point> pts = l.points;
	    for (int i = 0; i <pts.size(); i++) {
	      if (pts.get(i) == s1.p1 
	      		&& pts.get(i == pts.size()-1 ? 0 : i+1) == s1.p2){
	        pts.add(i + 1, p);
	        break;
	      }
	      if (pts.get(i) == s1.p2 
	      		&& pts.get(i == pts.size()-1 ? 0 : i+1) == s1.p1){
	        pts.add(i + 1, p);
	        break;
	      }
	    }
	  }
	  // Add point P to second face.
	  Face r = searchFace(s1, l);
	  if (r != null && !r.points.contains(p)) {
	    List<Point> pts = r.points;
	    // Add after P2 or P1 for the right face (CCW)
	    for (int i = 0; i < pts.size(); i++) {
	      if (pts.get(i) == s1.p1 && pts.get(i == pts.size()-1 ? 0 : i+1) == s1.p2){
	        pts.add(i + 1, p);
	        break;
	      }
	      if (pts.get(i) == s1.p2 && pts.get(i == pts.size()-1 ? 0 : i+1) == s1.p1) {
	        pts.add(i + 1, p);
	        break;
	      }
	    }
	  }
	  // Add this as a new point to the model
	  addPoint(p);
	  // Now we can shorten s to p
	  splitSegment(s1, p);
  }
  /** Splits Segment on a point */
  private void splitSegment(Segment s, Point p){
    if (s.p1.compareTo(p) == 0 || s.p2.compareTo(p) == 0)
      return;
    // Create new Segment 
    addSegment(p, s.p2, s.type);
    // Shorten s1
    s.p2 = p;
    s.lg2d();
  }
  /** Split listed faces by a plane */
	private void splitFacesByPlane(Plane pl, List<Face> list) {
	  // All potential faces
		list = list == null ? faces : list.size() == 0 ? faces : list;
    List<Face> listToSplit = new LinkedList<Face>();
    for (Face f : list){
      for (int i = 0; i < f.points.size()-1; i++){
        if (pl.intersect(f.points.get(i), f.points.get(i+1)) != null 
        		&& !listToSplit.contains(f)) {
        	listToSplit.add(f);
          break;
        }
      }
    }
    // Split all selected and intersecting faces
    for (Face f : listToSplit) {
      splitFaceByPlane(f, pl);
    }
  }
	
	/** Rotate around axis Segment with angle list of Points */
  public void rotate(Segment s, float angle, List<Point> list) {
    angle *= Math.PI / 180.0;
    float ax = s.p1.x, ay = s.p1.y, az = s.p1.z;
    float nx = s.p2.x-ax, ny = s.p2.y-ay, nz = s.p2.z-az; 
    float n = (float) (1.0 / Math.sqrt(nx*nx+ny*ny+nz*nz));
    nx *= n; ny *= n; nz *= n;
    float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
    float c1 = 1.0f-cos;
    float c11 = c1*nx*nx + cos, c12 = c1*nx*ny - nz*sin, c13 = c1*nx*nz + ny*sin;
    float c21 = c1*ny*nx + nz*sin, c22 = c1*ny*ny + cos, c23 = c1*ny*nz - nx*sin;
    float c31 = c1*nz*nx - ny*sin, c32 = c1*nz*ny + nx*sin, c33 = c1*nz*nz + cos;
    for (Point p : list) {
      float ux = p.x-ax, uy = p.y-ay, uz = p.z-az;
      p.x = ax+c11*ux+c12*uy+c13*uz;
      p.y = ay+c21*ux+c22*uy+c23*uz;
      p.z = az+c31*ux+c32*uy+c33*uz;
    }
  }
  /** Turn model around 1:X axis 2:Y axis 3:Z axis */
  public void turn(float angle, int axe) {
    angle *= Math.PI/180.0;
    float ax = 0, ay = 0, az = 0;
    float nx = 0.0f, ny = 0.0f, nz = 0.0f; 
    if (axe == 1) nx = 1.0f;
    else if (axe == 2) ny = 1.0f;
    else if (axe == 3) nz = 1.0f;
    float n = (float) (1.0 / Math.sqrt(nx*nx+ny*ny+nz*nz));
    nx *= n; ny *= n; nz *= n;
    float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
    float c1 = 1.0f-cos;
    float c11 = c1*nx*nx + cos, c12 = c1*nx*ny - nz*sin, c13 = c1*nx*nz + ny*sin;
    float c21 = c1*ny*nx + nz*sin, c22 = c1*ny*ny + cos, c23 = c1*ny*nz - nx*sin;
    float c31 = c1*nz*nx - ny*sin, c32 = c1*nz*ny + nx*sin, c33 = c1*nz*nz + cos;
    for (Point p : points) {
      float ux = p.x-ax, uy = p.y-ay, uz = p.z-az;
      p.x = ax+c11*ux+c12*uy+c13*uz;
      p.y = ay+c21*ux+c22*uy+c23*uz;
      p.z = az+c31*ux+c32*uy+c33*uz;
    }
  }
  /** Adjust list of Points */
  public float adjust(List<Point> list) {
    float dmax = 100;
    for (int i = 0 ; dmax > 0.001f && i < 10 ; i++){
      dmax = 0;
      for(Point p : list){
        float d = adjust(p, null);
        if (d > dmax)
          dmax = d;
      }
    }
    return dmax;
  }
  /** Adjust one of Point with list of segments */
  public float adjustSegments(Point p, List<Segment> segs) {
  	float dmax = 100;
  	for (int i = 0 ; dmax > 0.001f && i < 10 ; i++){
  		dmax = 0;
  		float d = adjust(p, segs);
  		if (d > dmax)
  			dmax = d;
  	}
  	return dmax;
  }
  /** Adjust one Point on its (eventually given) segments */
  private float adjust(Point p, List<Segment> segments) {
    // Take all segments containing point p or given list
    List<Segment> segs = segments == null ? searchSegments(p) : segments;
    float size = segs.size();
    float dmax = 100;
    // Kaczmarz
    // Iterate while length difference between 2d and 3d is > 1e-3
    for (int i = 0 ; dmax > 0.001f && i < 20 ; i++){
      dmax = 0;
      // Iterate over all segments
      // Pm is the medium point
      Vector3D pm = new Vector3D(0,0,0);
      for (Segment s : segs) {
        float lg3d = s.lg3d() / currentScale;
        float d = (s.lg2d - s.lg3d);
        if (Math.abs(d) > dmax)
          dmax = Math.abs(d);
        // Move B Bnew=A+AB*r With r=l2d/l3d
        // AB * r is the extension based on length3d to match length2d
        float r = (s.lg2d / lg3d);  
        if (s.p2 == p) {
          // move p2
          pm.x += s.p1.x+(s.p2.x-s.p1.x)*r;
          pm.y += s.p1.y+(s.p2.y-s.p1.y)*r;
          pm.z += s.p1.z+(s.p2.z-s.p1.z)*r;
        } else if (s.p1 == p) {
          // move p1
          pm.x += s.p2.x+(s.p1.x-s.p2.x)*r;
          pm.y += s.p2.y+(s.p1.y-s.p2.y)*r;
          pm.z += s.p2.z+(s.p1.z-s.p2.z)*r;
        }
      }
      // Average position taking all segments
      if (size != 0) {
      	p.x = pm.x/size;
      	p.y = pm.y/size;
      	p.z = pm.z/size;
      }
    }
    return dmax;
  }
  
  /** Select (highlight) points */
  public void selectPts(List<Point> pts) {
    for (Point p:pts)
    	p.select ^= true; // xor
  }
  /** Select (highlight) segments */
	public void selectSegs(List<Segment> segs) {
    for (Segment s:segs)
    	s.select ^= true; // xor	  
  }

  /** Move list of points by dx,dy,dz */
  public void move(float dx, float dy, float dz, List<Point> pts) {
  	pts = pts == null? points : pts.size() == 0 ? points : pts;
  	for (Point p:pts){
  		p.x += dx; 
  		p.y += dy; 
  		p.z += dz;
  	}
  }
  /** Move on a point P0 all following points, k from 0 to 1 for animation */
  public void moveOn(Point p0, float k1, float k2, List<Point> pts) {
    for (Point p:pts){
      p.x = p0.x*k1 + p.x*k2; 
      p.y = p0.y*k1 + p.y*k2; 
      p.z = p0.z*k1 + p.z*k2;
    }
  }
  /** Move on a line S0 all following points, k from 0 to 1 for animation */
	public void moveOnLine(Segment s, float k1, float k2, List<Point> pts) {
    for (Point p:pts) {
    	// Point n = s0.closestLine(new Segment(p,p)).p1;
    	// First case if there is a segment joining pts[0] and s
    	Point pc = null, pd = null;
    	for (Segment si : segments) {
    		if (si.equals(p, s.p1)) {
    			pc = si.p2; pd = s.p2;
    			break;
    		} else if (si.equals(p, s.p2)) {
    			pc = si.p2; pd = s.p1;
    			break;
    		} else if (si.equals(s.p1, p)) {
    			pc = si.p1; pd = s.p2;
    			break;
    		} else if (si.equals(s.p2, p)) {
    			pc = si.p1; pd = s.p1;
    			break;
    		}
    	}
    	if (pc != null) {
        // Turn p on pc pd (keep distance from Pc to P
        float pcp = (float) Math.sqrt((pc.x-p.x)*(pc.x-p.x)
            + (pc.y-p.y)*(pc.y-p.y)
            + (pc.z-p.z)*(pc.z-p.z));
        float pcpd = (float) Math.sqrt((pc.x-pd.x)*(pc.x-pd.x)
            + (pc.y-pd.y)*(pc.y-pd.y)
            + (pc.z-pd.z)*(pc.z-pd.z));
        float k = pcp / pcpd;
        p.x = (pc.x+k*(pd.x-pc.x))*k1 + p.x*k2;
        p.y = (pc.y+k*(pd.y-pc.y))*k1 + p.y*k2;
        p.z = (pc.z+k*(pd.z-pc.z))*k1 + p.z*k2;
        return;
    	}
    	// Second case 
    	else {
    		// Project point
    		Point pp = s.closestLine(new Segment(p,p)).p1;
    		// Move point p on projected pp
        p.x = (p.x+(pp.x-p.x))*k1 + p.x*k2;
        p.y = (p.y+(pp.y-p.y))*k1 + p.y*k2;
        p.z = (p.z+(pp.z-p.z))*k1 + p.z*k2;
    	}
    }	  
  }
  /** Move given or all points to z = 0 */
	public void flat(List<Point> pts) {
  	List<Point> lp = pts.size() == 0 ? points : pts;
    for (Point p:lp)
    	p.z = 0;
  }
  /** Offset by dz all following faces according to Z */
  public void offset(float dz, List<Face> lf) {
    for (Face f : lf){
      f.offset = dz * (f.normal[2] >= 0 ? 1: -1);
    }
  }
  /** Offset all faces either behind zero plane or above zero plane */
  public void offsetDecal(float dcl, List<Face> list) {
  	list = list.size() == 0 ? faces : list;
  	float max = dcl < 0 ? -1000 : 1000;
  	float o = 0;
  	for (Face f : list) {
    	f.computeFaceNormal();
  		o = f.offset * (f.normal[2] >= 0 ? 1 : -1);
  		if (dcl < 0 && o > max ) max = o;
  		if (dcl > 0 && o < max ) max = o;
  	}
  	for (Face f : list) {
  		f.offset -= (max-dcl) * (f.normal[2] >= 0 ? 1 : -1);
  	}
  }
  /** Add offset dz to all following faces according to Z */
  public void offsetAdd(float dz, List<Face> list) {
  	List<Face> lf = list.size() == 0 ? faces : list;
    for (Face f : lf){
      f.offset += dz * (f.normal[2] >= 0 ? 1 : -1);
    }
  }
  /** Multiply offset by k for all faces or only listed */
  public void offsetMul(float k, List<Face> list) {
  	List<Face> lf = list.size() == 0 ? faces : list;
  	for (Face f : lf){
      f.offset *= k ;
    }
  }
  /** Divide offset around average offset, to fold between */
  public void offsetBetween(List<Face> list) {
  	float average = 0;
  	int n = 0;
  	for (Face f : list){
      average += f.offset * (f.normal[2] >= 0 ? 1 : -1);
      n++;
    }
  	average /= n;
    for (Face f : faces){
      f.offset -= average * (f.normal[2] >= 0 ? 1 : -1);
    }
  	for (Face f : list){
      f.offset /= 2;
    }
  }
  /** Split face f by plane pl and add Points to joint faces (public for test) */
  private void splitFaceByPlane(Face f1, Plane pl) {
    Vector3D i = null;
    Point p1 = null, p2 = null;
    List<Point> frontSide = new LinkedList<Point>(); // Front side
    List<Point> backSide = new LinkedList<Point>(); // Back side
    
  	// Begin with last point
  	Point a = f1.points.get(f1.points.size()-1);
  	int aSide = pl.classifyPointToPlane(a);
  	for (int n = 0; n < f1.points.size(); n++) {
  	// 9 cases to deal with : behind -1, on 0, in front +1
  	// Segment from previous 'a'  to current 'b' 
  	// output to Front points 'fb' and  Back points 'bp'
    //  	  a  b Inter front back  
  	// c1) -1  1 i     i b   i
  	// c2)  0  1 a     b     .  
  	// c3)  1  1 .     b     .
  	// c4)  1 -1 i     i     i b
  	// c5)  0 -1 a     .     a b
  	// c6) -1 -1 .           b
  	// c7)  1  0 b     b     .  
  	// c8)  0  0 a b   b     .   
  	// c9) -1  0 b     b     b  
  		Point b = f1.points.get(n);
  		int bSide = pl.classifyPointToPlane(b);
  		if (bSide == 1) { // b in front
  			if (aSide == -1) { // a behind
  				// c1) b in front, a behind => edge cross 
  				i = pl.intersect(b, a);
  				// Create intersection point 'p', add to joint face, split segment
  				Point p = addPointToJointFace(f1, i, a, b);
  				// Add 'p' to front and back sides
  				frontSide.add(p);
  				backSide.add(p);
  				// Keep new point 'p' for the new segment
  				if (p1 == null) p1 = p;
  				else if (p2 == null) p2 = p;
  				// Check
  				else System.out.println("Three intersections:"+p1+" "+p2+" "+p);
  				// Check
  				if (pl.classifyPointToPlane(p) != 0)
  					System.out.println("Intersection not in plane ! p:"+p);;
  			} else if (aSide == 0) {  			
  				// c2) 'b' in front, 'a' on 
  				// Keep last point 'a' for the new segment 
  				// leaving thickness
  				if (p1 == null) p1 = a;
  				else if (p2 == null) p2 = a;
  				else System.out.println("Three intersections:"+p1+" "+p2+" "+a);
  			} 
  			// c3) 'b' in front 'a' in front
  			// In all three cases add 'b' to front side
				frontSide.add(b);
  		} else if (bSide == -1) { // b behind
  			if (aSide == 1) {  // a in front
  				// c4) edge cross add intersection to both sides
  				i = pl.intersect(b, a);
  				// Create intersection point 'p', add to joint face, split segment
  				Point p = addPointToJointFace(f1, i, a, b);
  				// Add 'p' to front and back sides
  				frontSide.add(p); 
  				backSide.add(p);
  				// Keep new point p for the new segment
  				if (p1 == null) p1 = p;
  				else if (p2 == null) p2 = p;
  				// Check
  				else System.out.println("Three intersections:"+p1+" "+p2+" "+p);
  			} else if (aSide == 0) {
  				// c5) 'a' on 'b' behind
  				// Keep point 'a' for the new segment 
  				// leaving thickness
  				if (p1 == null) p1 = a;
  				else if (p2 == null) p2 = a;
  				// Check
  				else System.out.println("Three intersections:"+p1+" "+p2+" "+a);
  				// Add 'a' to back side when [a,b] goes from 'on' to 'behind'
  				backSide.add(a); 
  			} 
  			// c6) 'a' behind 'b' behind
  			// In all 3 cases add current point 'b' to back side
				backSide.add(b); 
  		} else { 
  			// bSide == 0 'b' is 'on'
  			// c7) 'a' front 'b' on c8) 'a' on 'b' on
  			// Add 'b' to back side only if 'a' was in back face
  			if (aSide == -1) {
  				// c9 'a' behind 'b' on
    			backSide.add(b);
  			}
  			// In all 3 cases, add 'b' to front side
  			frontSide.add(b);
  		}
  		// Next edge
  		a = b;
  		aSide = bSide;
  	}
//    System.out.println("Point :"+points);
//    System.out.println("Segments :"+segments);
//    System.out.println("Faces :"+faces);
//    System.out.println("p1:"+p1+" p2:"+p2);

  	// Only if two different intersections has been found
  	if (p1 != null && p2 != null) {
//    	System.out.println("Found\n frontSide:"+frontSide+ "\n backSide:"+backSide+"\n p1:"+p1+ " p2:"+p2+"\n");
    	// New back Face
    	Face f2 = new Face();
    	f2.offset = f1.offset;
    	f2.points = backSide;
    	addFace(f2);
    	// New segment
    	addSegment(p1, p2, Segment.PLAIN);
    	// Updated front Face
    	f1.points.clear();
    	f1.points.addAll(frontSide);
  	} 
  }
  /** Look if the point A is already in the face, if not add it and return true */
  private Point addPointToJointFace(Face f, Vector3D i, Point a, Point b) {
  	// If the point is already in the model no need to do anything
  	for (Point p : f.points) {
  		if (p.compareTo(i.x, i.y, i.z) == 0.0f) {
  			System.out.println("PB increase plane thickness p:"+p+" near:"+i);
  			return p;
  		}
  	}

  	// Point i, not found, create, use to split segment, and add to the joint face
  	// Create new Point
  	Point p = new Point(i.x, i.y, i.z, -1);
  	// Get the segment to split
  	Segment s = searchSegment(a, b);
  	// Set 2D coordinates from 3D
  	align2dFrom3d(p, s);
  	// Add this as a new Point to the model
  	p = addPoint(p);
  	// Search joint face containing s.p1 and s.p2
  	Face jf = searchFace(s, f);
  	// If there is a joint face and without the new point 'p' between a, b
  	if (jf != null && !jf.points.contains(p)) {
  		// Add after a or b for the left face
  		if (jf.points.indexOf(a) == jf.points.indexOf(b)-1)
  			jf.points.add(jf.points.indexOf(b), p); // before b
  		else if (jf.points.indexOf(a) == jf.points.indexOf(b)+1)
  			jf.points.add(jf.points.indexOf(a), p); // before a
  		else if (jf.points.indexOf(a) == 0) // starting with a ending with b
  			jf.points.add(0, p); // before a
  		else if (jf.points.indexOf(b) == 0) // starting with b ending with a
  			jf.points.add(0, p); // before b
  		else {
  			System.out.println("Face contains points a,b but not the segment [a,b] jf:"+jf+" a:"+a+" b:"+b);
  			System.out.println("jf.points.contains(a):"+jf.points.contains(a));
  			System.out.println("jf.points.indexOf(a):"+jf.points.indexOf(a));
  		}
  	}
  	// Now we can shorten s to p
  	splitSegment(s, p);
  	return p;
  }
  /** Align Point PB on segment s in 2D from coordinates in 3D */
  private void align2dFrom3d(Point pb, Segment s) {
    // Align point B in 2D from 3D
    float lg3d = (float) Math.sqrt((s.p1.x-pb.x)*(s.p1.x-pb.x)
        +(s.p1.y-pb.y)*(s.p1.y-pb.y)
        +(s.p1.z-pb.z)*(s.p1.z-pb.z));
    float t = lg3d / s.lg3d(); // no currentScale
    pb.xf = s.p1.xf + t*(s.p2.xf - s.p1.xf);
    pb.yf = s.p1.yf + t*(s.p2.yf - s.p1.yf);
  }
  /** Compute angle between faces of given segment */
  public float computeAngle(Segment s) {
    Point a = s.p1, b = s.p2;
    // Find faces left and right
    Face left = faceLeft(a, b);
    Face right = faceRight(a, b);
    // Compute angle in Degrees at this segment 
    if (s.type == Segment.EDGE) 
      return 0;
    if (right == null || left ==null) {
      System.out.println("PB no right or left face for "+this+" left "+left+" right "+right);
      return 0;
    }
    float[] nL = left.computeFaceNormal();
    float[] nR = right.computeFaceNormal();
    // Cross product nL nR 
    float cx=nL[1]*nR[2]-nL[2]*nR[1], cy=nL[2]*nR[0]-nL[0]*nR[2], cz=nL[0]*nR[1]-nL[1]*nR[0];
    // Segment vector
    float vx=s.p2.x-s.p1.x, vy=s.p2.y-s.p1.y, vz=s.p2.z-s.p1.z;
    // Scalar product between segment and cross product, normed
    float sin = (cx*vx+cy*vy+cz*vz) / (float) Math.sqrt(vx*vx+vy*vy+vz*vz);
    // Scalar between normals
    float cos = nL[0]*nR[0] +nL[1]*nR[1] +nL[2]*nR[2];
    if (cos > 1.0f )
      cos = 1.0f;
    if (cos < -1.0f )
      cos = -1.0f;
    s.angle = (float)(Math.acos(cos) / Math.PI*180.0);
    if (Float.isNaN(s.angle)){
      s.angle = 0.0f;
    }
    if (sin < 0) 
      s.angle = -s.angle;
    // To follow the convention folding in front is positive
    s.angle = -s.angle;
    return s.angle;
  }
  /** Find face on the right */
  public Face faceRight(Point a, Point b) {
    int ia, ib;
    Face right = null;
    for (Face f : faces){
      // Both points are in face
      if( ((ia = f.points.indexOf(a)) >= 0)
       && ((ib = f.points.indexOf(b)) >= 0) ){
        // a is after b, the face is on the right
        if (ia == ib+1 || (ib == f.points.size()-1 && ia == 0)){
          right = f;
          break;
        }
      }
    }
    return right;
  }
  /** Find face on the left */
  public Face faceLeft(Point a, Point b) {
    int ia, ib;
    Face left = null;
    for (Face f : faces){
      // Both points are in face
      if( ((ia = f.points.indexOf(a)) >= 0)
       && ((ib = f.points.indexOf(b)) >= 0) ){
        // b is after a, the face is on the left
        if (ib == ia+1 || (ia == f.points.size()-1 && ib == 0)){
          left = f;
          break;
        }
      }
    }
    return left;
  }
  /** Search face containing a and b but which is not f0 */ 
  private Face searchFace(Segment s, Face f0) {
    Point a = s.p1, b = s.p2;
    for (Face f : faces){
      if (!f.equals(f0)
          && (f.points.contains(a))
          && (f.points.contains(b))){
        return f;
      }
    }
    return null;
  }
  /** Search segment containing a and b */ 
  private Segment searchSegment(Point a, Point b) {
    for (Segment s : segments) {
      if (s.equals(a, b) || s.equals(b, a))
        return s;
    }
    return null;
  }
  /** Search segments containing a  */ 
  private List<Segment> searchSegments(Point a) {
    List<Segment> l = new LinkedList<Segment>();
    for (Segment s : segments) {
      if (s.p1 == a || s.p2 == a ) // Pointer 
        l.add(s);
    }
    return l;
  }
  /** 2D Boundary [xmin, ymin, xmax, ymax]*/
  public float[] get2DBounds() {
    float xmax = -100.0f, xmin = 100.0f;
    float ymax = -100.0f, ymin = 100.0f;
    for (Point p : points) {
      float x = p.xf, y = p.yf;
      if (x > xmax) xmax = x;
      if (x < xmin) xmin = x;
      if (y > ymax) ymax = y;
      if (y < ymin) ymin = y;
    }
    return new float[]{ xmin, ymin, xmax, ymax };
  }
  /** Fit the model to -200 +200 */
  public void zoomFit() {
    float[] b = get3DBounds();
    float w = 400;
    float scale = w  / Math.max(b[2]-b[0], b[3]-b[1]);
    float cx = -(b[0]+b[2])/2, cy = -(b[1]+b[3])/2;
    move(cx, cy, 0, null);
    scaleModel(scale);
  }
  /** Scale model */
  public void scaleModel(float scale) {
    for (Point p : points) {
      p.x *= scale;
      p.y *= scale;
      p.z *= scale;
    }
    currentScale  *= scale;
  }
  /** 3D Boundary View [xmin, ymin, xmax, ymax] */
  public float[] get3DBounds() {
    float xmax = -200.0f, xmin = 200.0f;
    float ymax = -200.0f, ymin = 200.0f;
    for (Point p : points) {
      float x = p.x, y = p.y;
      if (x > xmax) xmax = x;
      if (x < xmin) xmin = x;
      if (y > ymax) ymax = y;
      if (y < ymin) ymin = y;
    }
    return new float[]{xmin, ymin, xmax, ymax};
  }
  /** Serial encoder */
  public byte[] getSerialized() {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    ObjectOutputStream oos;
    try {
      oos = new ObjectOutputStream(bs);
      oos.writeObject(this);
      oos.close();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    return bs.toByteArray();
  }
  // --------------------- DEBUG ------------
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("Points["+points.size()+"] : ");
    for (Point p : points) {
      sb.append(p).append("\n");
    }
    sb.append("Segments["+segments.size()+"] : ");
    for (Segment s : segments) {
      sb.append(s).append("\n");;
    }
    sb.append("Faces["+faces.size()+"] : ");
    for (Face f : faces) {
      sb.append(f).append("\n");;
    }
    return sb.toString();
  }
}
