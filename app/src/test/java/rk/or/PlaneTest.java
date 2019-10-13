package rk.or;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PlaneTest {

    @Test
    public void init() {
        Plane pl = new Plane();
        assertNotNull(pl);
    }

    @Test
    public void across() {
        Point p1 = new Point().setFrom3D(10, 0, 0); // On x axis
        Point p2 = new Point().setFrom3D(30, 0, 0); // On x axis
        Plane pl = new Plane().across(p1, p2); // Plane across 10,0,0 and 20,0,0 aligned on y z
        assertNotNull(pl);

        Point p3 = new Point().setFrom3D(20, 0, 0);
        int c = pl.classifyPointToPlane(p3);
        assertEquals(0, c);
    }
}