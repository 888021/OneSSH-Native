package com.onessh.app.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Backup 纯函数测试：不需要 Android / 真机。 */
public class BackupTest {

    @Test
    public void wrapUnwrapRoundTrip() {
        String store = "{\"hosts\":[{\"id\":\"h1\",\"name\":\"web\"}],\"groups\":[]}";
        String wrapped = Backup.wrap(store);
        assertEquals(store, Backup.unwrap(wrapped));
    }

    @Test
    public void unwrapHandlesBracesInsideStrings() {
        String store = "{\"hosts\":[{\"note\":\"a {b} c\"}]}";
        assertEquals(store, Backup.unwrap(Backup.wrap(store)));
    }

    @Test
    public void unwrapRejectsGarbage() {
        assertNull(Backup.unwrap(null));
        assertNull(Backup.unwrap(""));
        assertNull(Backup.unwrap("not json"));
        assertNull(Backup.unwrap("{\"format\":\"onessh-backup\",\"version\":1}"));
        assertNull(Backup.wrap(""));
        assertNull(Backup.wrap(null));
    }

    @Test
    public void unwrapAcceptsRawStoreJson() {
        String store = "{\"hosts\":[]}";
        assertEquals(store, Backup.unwrap(store));
    }
}
