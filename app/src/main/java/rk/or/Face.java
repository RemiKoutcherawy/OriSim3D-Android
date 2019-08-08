package rk.or;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/** 
 * Face to hold points, segments, normal, 
 * struts and triangles  making the face. 
 */
public class Face implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final float EPSILON = 0.01f;
  public List<Point> points;
  public int id;
  public float[] normal = {0,0,1};
  public boolean select, highlight;
  public float offset;
  
  /** A new face, no point, no segments */
  public Face(){
    points = new LinkedList<Point>();
  }
  /** We Override to select face from existing faces */
  public boolean equals(Object obj) {
	  return this.id == ((Face)obj).id;
  }
	/** Compute Face normal */
  public float[] computeFaceNormal() {
    if (points.size() < 3) {
      System.out.println("Pb Face<3pts:"+this);
      normal[0] = 0; normal[1] = 0; normal[2] = 1;    
    }
    for (int i = 0; i < points.size()-2; i++){
      // Take triangles until p2p1 x p1p3 > 0.1
      Point p1 = points.get(i);
      Point p2 = points.get(i+1);
      Point p3 = points.get(i+2);
      float[] u = new float[] {p2.x-p1.x, p2.y-p1.y, p2.z-p1.z};
      float[] v = new float[] {p3.x-p1.x, p3.y-p1.y, p3.z-p1.z};
      normal[0] = u[1]*v[2]-u[2]*v[1];
      normal[1] = u[2]*v[0]-u[0]*v[2];
      normal[2] = u[0]*v[1]-u[1]*v[0];
      if (Math.abs(normal[0])+Math.abs(normal[1])+Math.abs(normal[2]) > EPSILON ){
        break;
      } 
    }
    normalize(normal);
    return normal;
  }
  /** Plane containing p1 p2 p3
   * @return N = p2p1 x p1p3 : n = -N.p1 */
  public static float[] plane(float[] p1, float[] p2, float[] p3){
    float[] r = new float[] {0,0,0, 0};
    float[] u = new float[] {p2[0]-p1[0], p2[1]-p1[1], p2[2]-p1[2], 0};
    float[] v = new float[] {p3[0]-p1[0], p3[1]-p1[1], p3[2]-p1[2], 0};
    r[0] = u[1]*v[2] -u[2]*v[1];
    r[1] = u[2]*v[0] -u[0]*v[2];
    r[2] = u[0]*v[1] -u[1]*v[0];
    r[3] = -(r[0]*p1[0] +r[1]*p1[1] +r[2]*p1[2]);
    return r;
  }
  /** Normalizes a vector
   * v[3] = v[3]/||v[3]|| */
  public static void normalize(float v[]){
    float d = (float) java.lang.Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
    v[0] /= d;
    v[1] /= d;
    v[2] /= d;
  }
  /** Compute Face center in 2D crease pattern */
  public float[] center2D() {
    float[] center = new float[2];
    for (Point p : points){
      center[0] += p.xf;
      center[1] += p.yf;
    }
    center[0] /= points.size();
    center[1] /= points.size();
    return center;
  }
  /** Compute Face center X coord in 3D view */
  public int center3Dx() {
    int center = 0;
    for (Point p : points)
      center += p.xv;
    center /= points.size();
    return center;
  }
  /** Compute Face center Y coord in 3D view */
  public int center3Dy() {
    int center = 0;
    for (Point p : points)
      center += p.yv;
    center /= points.size();
    return center;
  }
  /** - not used
   * Tests if the polygon is CCW
   * @return > 0 if CCW   */
  public static float isCCW(float[] poly2d) {
    int n = poly2d.length/2;
    // Take lowest
    float ymin = poly2d[1];
    int iymin = 0;
    for (int i = 0; i < n; i++){
      if (poly2d[2*i+1] < ymin) {
        ymin = poly2d[2*i+1];
        iymin = i;
      }
    }
    // Take points on either side of lowest
    int next = (iymin == n - 1) ? 0 : iymin+1;
    int previous = (iymin == 0) ? n-1 : iymin-1;
    // If not aligned ccw is of the sign of area
    float ccw = area2(poly2d, previous, iymin, next);
    if (ccw == 0) {
      // If horizontally aligned compare x
      ccw = poly2d[2*next] - poly2d[2*previous];
    }
    return ccw;
  }
  /** - not used
   * From Polygon V of XY coordinates take points of index A, B, C
   * @return cross product Z = CA x CB ( > 0 means CCW)    */
  public static float area2(float[] v, int a, int b, int c) {
    int ax = 2*a, bx = 2*b, cx = 2*c;
    int ay = 2*a+1, by= 2*b+1, cy = 2*c+1;
    return (v[ax]-v[cx])*(v[by]-v[cy]) - (v[ay]-v[cy])*(v[bx]-v[cx]);
  }
  /**
   * Look if projected face contains point x,y in 3D view
   * @return true if face contains (x,y) including border, false otherwise.
   */
  public boolean contains3d(float x, float y) {
    int hits = 0;
    int npts = points.size();
    Point last = points.get(npts - 1);
    float lastx=last.xv, lasty=last.yv;
    float curx, cury;
    // Walk the edges of the polygon
    for (int i = 0; i < npts; lastx = curx, lasty = cury, i++) {
      curx = points.get(i).xv;
      cury = points.get(i).yv;
      if (cury == lasty)
        continue;
      float leftx;
      if (curx < lastx) {
        if (x >= lastx)
          continue;
        leftx = curx;
      } else {
        if (x >= curx)
          continue;
        leftx = lastx;
      }
      float test1, test2;
      if (cury < lasty) {
        if (y < cury || y >= lasty)
          continue;
        if (x < leftx) {
          hits++;
          continue;
        }
        test1 = x - curx;
        test2 = y - cury;
      } else {
        if (y < lasty || y >= cury)
          continue;
        if (x < leftx) {
          hits++;
          continue;
        }
        test1 = x - lastx;
        test2 = y - lasty;
      }
      if (test1 < (test2 / (lasty - cury) * (lastx - curx)))
        hits++;
    }
    return ((hits & 1) != 0);
  }
}
