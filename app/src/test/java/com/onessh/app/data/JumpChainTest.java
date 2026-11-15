package com.onessh.app.data;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** JumpChain 纯函数测试：不需要 Android / 真机。 */
public class JumpChainTest {

    @Test
    public void parsePrefersJumpIdsOverJumpId() {
        List<String> chain = JumpChain.parse("[\"a\",\"b\"]", "a");
        assertEquals(Arrays.asList("a", "b"), chain);
    }

    @Test
    public void parseFallsBackToSingleJumpId() {
        assertEquals(Arrays.asList("a"), JumpChain.parse("", "a"));
        assertEquals(Arrays.asList("a"), JumpChain.parse(null, "a"));
        assertEquals(0, JumpChain.parse("", "").size());
    }

    @Test
    public void parseDamagedJsonFallsBack() {
        assertEquals(Arrays.asList("a"), JumpChain.parse("[broken", "a"));
    }

    @Test
    public void toJsonRoundTrip() {
        List<String> chain = Arrays.asList("a", "b", "c");
        assertEquals(chain, JumpChain.parse(JumpChain.toJson(chain), ""));
        assertEquals("", JumpChain.toJson(new ArrayList<>()));
    }

    @Test
    public void addRejectsDuplicateSelfAndOverflow() {
        List<String> chain = new ArrayList<>(Arrays.asList("a", "b"));
        assertFalse(JumpChain.add(chain, "a", "self"));
        assertFalse(JumpChain.add(chain, "self", "self"));
        assertFalse(JumpChain.add(chain, "", "self"));
        assertFalse(JumpChain.add(chain, "  ", "self"));
        assertTrue(JumpChain.add(chain, "c", "self"));
        assertTrue(JumpChain.add(chain, "d", "self"));
        assertTrue(JumpChain.add(chain, "e", "self"));
        assertFalse(JumpChain.add(chain, "f", "self"));
        assertEquals(5, chain.size());
    }

    @Test
    public void moveUpDownAndRemove() {
        List<String> chain = new ArrayList<>(Arrays.asList("a", "b", "c"));
        assertFalse(JumpChain.moveUp(chain, 0));
        assertFalse(JumpChain.moveDown(chain, 2));
        assertTrue(JumpChain.moveDown(chain, 0));
        assertEquals(Arrays.asList("b", "a", "c"), chain);
        assertTrue(JumpChain.moveUp(chain, 2));
        assertEquals(Arrays.asList("b", "c", "a"), chain);
        assertTrue(JumpChain.remove(chain, 1));
        assertEquals(Arrays.asList("b", "a"), chain);
        assertFalse(JumpChain.remove(chain, 9));
    }

    @Test
    public void pruneMissingRemovesDeletedHosts() {
        List<String> chain = new ArrayList<>(Arrays.asList("a", "gone", "b"));
        assertEquals(1, JumpChain.pruneMissing(chain, Arrays.asList("a", "b")));
        assertEquals(Arrays.asList("a", "b"), chain);
    }
}
