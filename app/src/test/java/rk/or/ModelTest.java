package rk.or;

import org.junit.Test;

import rk.or.Model;

import static org.junit.Assert.*;

public class ModelTest {

    @Test
    public void init() {
        Model model = new Model();
        model.init(-200, -200, 200, -200, 200, 200, -200, 200);
        assertEquals(4, 2 + 2);
        assertEquals(4, model.points.size());
        assertEquals(4, model.segments.size());
        assertEquals(1, model.faces.size());
    }

}