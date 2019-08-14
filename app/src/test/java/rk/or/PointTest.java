package rk.or;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PointTest {

    @Test
    public void setFrom3D() {
        Point p = new Point();
        p.setFrom3D(1, 2, 3);
        assertEquals(1, p.x, 0.01d);
        assertEquals(2, p.y, 0.01d);
        assertEquals(3, p.z, 0.01d);
    }
}