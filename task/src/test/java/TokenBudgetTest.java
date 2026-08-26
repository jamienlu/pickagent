import com.pickagent.w1d1.TokenBudget;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jamieLu
 * @create 2026-08-24
 */
@Tag("integration")
public class TokenBudgetTest {

    @Test
    public void normal() {
        TokenBudget tokenBudget = new TokenBudget(1000, 500, 10);
        assertTrue(tokenBudget.isWithinBudget());
        assertEquals(490, tokenBudget.remainingBudget());
    }

    @Test
    public void over() {
        TokenBudget tokenBudget = new TokenBudget(500, 500, 10);
        assertFalse(tokenBudget.isWithinBudget());
        assertThrows(IllegalStateException.class, tokenBudget::remainingBudget);
    }

    @Test
    public void error() {
        assertThrows(IllegalArgumentException.class,() -> new TokenBudget(-500, 200, 10));
    }
    @Test
    public void all() {
        TokenBudget tokenBudget = new TokenBudget(500, 490, 10);
        assertTrue(tokenBudget.isWithinBudget());
        assertEquals(0, tokenBudget.remainingBudget());
    }
    @Test
    public void input0() {
        TokenBudget tokenBudget = new TokenBudget(500, 0, 10);
        assertEquals(490, tokenBudget.remainingBudget());
    }
}
