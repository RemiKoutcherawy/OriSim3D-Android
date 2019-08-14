package rk.or;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class CommandsTest {

    // Without ModelView (no Activity)
    @Test
    public void command() {
        Commands c = new Commands(null);
        Model model = new Model();
        model.init(-200, -200, 200, -200, 200, 200, -200, 200);
        assertNotNull(c);
    }
}