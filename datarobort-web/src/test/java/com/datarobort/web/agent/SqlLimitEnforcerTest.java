package com.datarobort.web.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P5: LIMIT enforcement — closes the "LIMIT 10000000 bypasses append-LIMIT" hole.
 */
class SqlLimitEnforcerTest {

    @Test
    void noLimit_appendsMaxRows() {
        String out = SqlLimitEnforcer.enforceLimit("SELECT * FROM orders", 500);
        assertTrue(out.toLowerCase().contains("limit 500"), "must append LIMIT 500: " + out);
    }

    @Test
    void limitWithinCap_unchanged() {
        assertEquals("SELECT * FROM orders LIMIT 20",
                SqlLimitEnforcer.enforceLimit("SELECT * FROM orders LIMIT 20", 500));
    }

    @Test
    void hugeLimit_rewrittenToCap() {
        String out = SqlLimitEnforcer.enforceLimit("SELECT * FROM orders LIMIT 10000000", 500);
        assertTrue(out.toLowerCase().contains("limit 500"), "must clamp to 500: " + out);
        assertFalse(out.toLowerCase().contains("10000000"), "original limit must be gone: " + out);
    }

    @Test
    void offset_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlLimitEnforcer.enforceLimit("SELECT * FROM orders LIMIT 10 OFFSET 20", 500));
    }

    @Test
    void expressionRowCount_clamped() {
        String out = SqlLimitEnforcer.enforceLimit("SELECT * FROM orders LIMIT ?", 500);
        assertTrue(out.toLowerCase().contains("limit 500"), "expression LIMIT must be clamped: " + out);
    }

    @Test
    void unionSelect_alsoEnforced() {
        String out = SqlLimitEnforcer.enforceLimit(
                "SELECT id FROM orders UNION SELECT id FROM customers", 500);
        assertTrue(out.toLowerCase().contains("limit 500"), "set operations must get a LIMIT too: " + out);
    }

    @Test
    void invalidSql_returnedUnchanged() {
        String bad = "SELEC FROM orders WHERE";
        assertEquals(bad, SqlLimitEnforcer.enforceLimit(bad, 500));
    }

    @Test
    void nonSelect_returnedUnchanged() {
        String update = "UPDATE orders SET status='x'";
        assertEquals(update, SqlLimitEnforcer.enforceLimit(update, 500));
    }

    @Test
    void upperCaseLimit_rewritten() {
        String out = SqlLimitEnforcer.enforceLimit("SELECT * FROM orders LIMIT 100000", 500);
        assertTrue(out.toLowerCase().contains("limit 500"));
    }
}
