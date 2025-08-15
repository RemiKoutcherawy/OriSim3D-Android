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
    public float[] normal = {0, 0, 1};
    public float offset;

    /**
     * A new face, no point, no segments
     */
    public Face() {
        points = new LinkedList<>();
    }

    /**
     * Normalizes a vector
     * v[3] = v[3]/||v[3]||
     */
    public static void normalize(float[] v) {
        float d = (float) java.lang.Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        v[0] /= d;
        v[1] /= d;
        v[2] /= d;
    }

    /**
     * We Override to select face from existing faces
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof Face))
            return false;
        return this.id == ((Face) obj).id;
    }

    /**
     * Compute Face normal
     */
    public float[] computeFaceNormal() {
        if (points.size() < 3) {
            System.out.println("Pb Face<3pts:" + this);
            normal[0] = 0;
            normal[1] = 0;
            normal[2] = 1;
        }
        for (int i = 0; i < points.size() - 2; i++) {
            // Take triangles until p2p1 x p1p3 > 0.1
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);
            Point p3 = points.get(i + 2);
            float[] u = new float[]{p2.x - p1.x, p2.y - p1.y, p2.z - p1.z};
            float[] v = new float[]{p3.x - p1.x, p3.y - p1.y, p3.z - p1.z};
            normal[0] = u[1] * v[2] - u[2] * v[1];
            normal[1] = u[2] * v[0] - u[0] * v[2];
            normal[2] = u[0] * v[1] - u[1] * v[0];
            if (Math.abs(normal[0]) + Math.abs(normal[1]) + Math.abs(normal[2]) > EPSILON) {
                break;
            }
        }
        normalize(normal);
        return normal;
    }

}
