package rk.or;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SegmentTest {
    private static final float delta = 0.001f;

    @Test
    public void init() {
        Segment s = new Segment();
        assertNotNull(s);
    }

    @Test
    public void compareTo() {
        Point p1 = new Point().setFrom3D(10, 20, 30);
        Point p2 = new Point().setFrom3D(40, 50, 60);
        Segment s = new Segment().setFrom2Points(p1, p2);
        assertNotNull(s);
        assertEquals(0, s.compareTo(p1, p2), delta);
        assertEquals(51.0f, s.compareTo(p2, p1), delta);
    }
}