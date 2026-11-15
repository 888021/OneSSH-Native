package com.onessh.app.ssh;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** ReconnectPolicy 纯函数测试：不需要 Android / 真机。 */
public class ReconnectPolicyTest {

    @Test
    public void delaysBackOff() {
        assertEquals(3000L, ReconnectPolicy.delayForAttempt(1));
        assertEquals(6000L, ReconnectPolicy.delayForAttempt(2));
        assertEquals(12000L, ReconnectPolicy.delayForAttempt(3));
        assertEquals(-1L, ReconnectPolicy.delayForAttempt(0));
        assertEquals(-1L, ReconnectPolicy.delayForAttempt(4));
    }

    @Test
    public void shouldRetryOnlyWhenUnexpectedAndNotExhausted() {
        assertTrue(ReconnectPolicy.shouldRetry(false, 0));
        assertTrue(ReconnectPolicy.shouldRetry(false, 2));
        assertFalse(ReconnectPolicy.shouldRetry(false, 3));
        assertFalse(ReconnectPolicy.shouldRetry(true, 0));
    }
}
