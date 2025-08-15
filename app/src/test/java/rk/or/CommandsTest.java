package rk.or;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class CommandsTest {

    // Without Activity
    @Test
    public void command() {
        Model model = new Model();
        model.init(-200, -200, 200, -200, 200, 200, -200, 200);
        assertNotNull(model);
        Commands c = new Commands();
        assertNotNull(c);
    }
}