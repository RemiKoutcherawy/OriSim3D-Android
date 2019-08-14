package rk.or;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class FaceTest {

    @Test
    public void init() {
        Face f = new Face();
        assertEquals(0, f.points.size());
        assertEquals(3, f.normal.length);
        assertFalse(f.select);
        assertFalse(f.highlight);
        assertEquals(0, f.offset, 0.01f);
    }

    @Test
    public void computeFaceNormal() {
        Point p1 = new Point().setFrom3D(0, 0, 0);
        Point p2 = new Point().setFrom3D(30, 0, 0);
        Point p3 = new Point().setFrom3D(0, 0, 40);
        Face f = new Face();
        f.points.add(p1);
        f.points.add(p2);
        f.points.add(p3);
        f.computeFaceNormal();
        assertEquals(0, f.normal[0], 0.01f);
        assertEquals(-1, f.normal[1], 0.01f);
        assertEquals(0, f.normal[2], 0.01f);
    }
}