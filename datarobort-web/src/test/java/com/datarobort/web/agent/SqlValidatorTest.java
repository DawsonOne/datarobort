package com.datarobort.web.agent;

import com.datarobort.core.entity.DsTable;
import com.datarobort.core.mapper.DsTableMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P5: the SQL guard — the core security layer. Every attack sample must be
 * rejected; legitimate analytical queries must pass.
 */
class SqlValidatorTest {

    private static final Long DS_ID = 1L;

    private DsTableMapper tableMapper;
    private SqlValidator validator;

    @BeforeEach
    void setUp() {
        tableMapper = mock(DsTableMapper.class);
        // demo_business snapshot: orders / customers / products
        when(tableMapper.selectByDsId(DS_ID)).thenReturn(List.of(
                table("orders"), table("customers"), table("products")));
        validator = new SqlValidator(tableMapper);
        // @Value default is false — make sure the field is in a known state
        ReflectionTestUtils.setField(validator, "whitelistRequired", false);
    }

    private DsTable table(String name) {
        DsTable t = new DsTable();
        t.setTableName(name);
        return t;
    }

    private void requireWhitelist() {
        ReflectionTestUtils.setField(validator, "whitelistRequired", true);
    }

    // ---------- legitimate SQL must pass ----------

    @Test
    void validSelect_passes() {
        assertNull(validator.validate("SELECT * FROM orders", DS_ID));
    }

    @Test
    void validAggregateWithFunctions_passes() {
        assertNull(validator.validate(
                "SELECT DATE_FORMAT(o.create_time, '%Y-%m') AS ym, COUNT(*) AS cnt, SUM(o.amount) AS total "
                        + "FROM orders o WHERE o.status = 'paid' GROUP BY ym ORDER BY cnt DESC LIMIT 12", DS_ID));
    }

    @Test
    void qualifiedTableName_passes() {
        assertNull(validator.validate("SELECT * FROM demo_business.orders", DS_ID));
    }

    @Test
    void backtickQuotedTable_passes() {
        assertNull(validator.validate("SELECT `id`, `name` FROM `customers`", DS_ID));
    }

    @Test
    void joinAcrossWhitelistedTables_passes() {
        assertNull(validator.validate(
                "SELECT o.id, c.name FROM orders o JOIN customers c ON o.customer_id = c.id", DS_ID));
    }

    @Test
    void caseInsensitiveTableName_passes() {
        assertNull(validator.validate("SELECT * FROM ORDERS", DS_ID));
    }

    @Test
    void subqueryInWhere_passes() {
        assertNull(validator.validate(
                "SELECT name FROM customers WHERE id IN (SELECT customer_id FROM orders WHERE amount > 1000)", DS_ID));
    }

    // ---------- attack samples must be rejected ----------

    @Test
    void multiStatement_rejected() {
        assertNotNull(validator.validate("SELECT * FROM orders; DROP TABLE orders", DS_ID));
    }

    @Test
    void writeCte_rejected() {
        assertNotNull(validator.validate(
                "WITH deleted AS (DELETE FROM orders RETURNING *) SELECT * FROM deleted", DS_ID));
    }

    @Test
    void intoOutfile_rejected() {
        assertNotNull(validator.validate(
                "SELECT * FROM orders INTO OUTFILE '/tmp/orders.txt'", DS_ID));
    }

    @Test
    void intoDumpfile_rejected() {
        assertNotNull(validator.validate(
                "SELECT 'x' INTO DUMPFILE '/var/lib/mysql/evil'", DS_ID));
    }

    @Test
    void loadFile_rejected() {
        assertNotNull(validator.validate(
                "SELECT LOAD_FILE('/etc/passwd') FROM orders", DS_ID));
    }

    @Test
    void sleep_rejected() {
        assertNotNull(validator.validate("SELECT SLEEP(10) FROM orders", DS_ID));
    }

    @Test
    void benchmark_rejected() {
        assertNotNull(validator.validate("SELECT BENCHMARK(1000000, MD5('a'))", DS_ID));
    }

    @Test
    void systemTable_rejected() {
        assertNotNull(validator.validate("SELECT User FROM mysql.user", DS_ID));
    }

    @Test
    void outOfWhitelistTable_rejected() {
        assertNotNull(validator.validate("SELECT * FROM secrets", DS_ID));
    }

    @Test
    void systemTableWithWhitelistedAlias_rejected() {
        assertNotNull(validator.validate("SELECT * FROM mysql.user u", DS_ID));
    }

    @Test
    void caseBypassUpperCaseFunction_rejected() {
        assertNotNull(validator.validate("SELECT * FROM orders WHERE 1=1 AND SLEEP(10)", DS_ID));
        assertNotNull(validator.validate("SELECT * FROM orders WHERE 1=1 AND sleep (10)", DS_ID));
    }

    @Test
    void versionedCommentStrippedBeforeParse() {
        // /*!50000 ... */ content is executed by MySQL, so the validator must
        // never see or pass it through. The defense is: strip first, then
        // parse — the smuggled DROP never reaches the executor (SqlExecNode
        // runs the same stripped text). After stripping, the remaining text
        // must be the benign SELECT.
        String stripped = SqlValidator.stripVersionedComments(
                "/*!50000 ; DROP TABLE orders */ SELECT * FROM orders");
        assertNull(validator.validate(stripped, DS_ID));
        assertFalse(stripped.toLowerCase().contains("drop"),
                "stripped text must not contain the smuggled statement");
        // A versioned comment wrapping a real attack keyword must be gone:
        assertFalse(SqlValidator.stripVersionedComments(
                        "SELECT * FROM orders /*!50000 INTO OUTFILE '/tmp/x' */")
                .toLowerCase().contains("outfile"));
        // Plain comments (-- and /* */) stay: they carry no executable content.
        assertEquals("SELECT * FROM orders -- note\n",
                SqlValidator.stripVersionedComments("SELECT * FROM orders -- note\n"));
    }

    @Test
    void inlineComment_withinSelect_passes() {
        // Plain comments (not versioned) do not change semantics — must pass.
        assertNull(validator.validate("SELECT id, name -- comment\nFROM customers", DS_ID));
    }

    @Test
    void getLock_rejected() {
        assertNotNull(validator.validate("SELECT GET_LOCK('k', 10)", DS_ID));
    }

    @Test
    void nonSelectStatement_rejected() {
        assertNotNull(validator.validate("UPDATE orders SET status='x' WHERE id=1", DS_ID));
        assertNotNull(validator.validate("DROP TABLE orders", DS_ID));
        assertNotNull(validator.validate("SHOW TABLES", DS_ID));
    }

    @Test
    void garbledSql_rejected() {
        assertNotNull(validator.validate("SELEC FROM orders WHERE", DS_ID));
    }

    // ---------- whitelist degradation behavior ----------

    @Test
    void emptyWhitelist_degradesToWarning_whenNotRequired() {
        when(tableMapper.selectByDsId(99L)).thenReturn(List.of());
        // dsId=99 has no metadata — with whitelist-required=false it degrades
        assertNull(validator.validate("SELECT * FROM anything", 99L));
    }

    @Test
    void emptyWhitelist_rejects_whenRequired() {
        requireWhitelist();
        when(tableMapper.selectByDsId(99L)).thenReturn(List.of());
        assertNotNull(validator.validate("SELECT * FROM anything", 99L));
    }

    @Test
    void nullDsId_skipsTableCheck() {
        assertNull(validator.validate("SELECT * FROM orders", null));
    }

    @Test
    void blankSql_rejected() {
        assertNotNull(validator.validate("   ", DS_ID));
        assertNotNull(validator.validate(null, DS_ID));
    }

    // ---------- error text must not leak details ----------

    @Test
    void errorMessage_truncated() {
        String err = validator.validate("SELECT * FROM orders WHERE 1=1 OR id=1; SELECT '"
                + "x".repeat(500) + "'", DS_ID);
        assertNotNull(err);
        assertTrue(err.length() <= SqlValidator.MAX_ERROR_LEN + 3, "error must be capped: " + err.length());
    }
}
