package com.datarobort.web.agent;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Select;

/**
 * Forces a maximum row limit on a generated SELECT before execution.
 * Closes the "LIMIT 10000000 bypasses the executor's append-LIMIT" hole:
 * <ul>
 *   <li>no LIMIT → append {@code LIMIT maxRows};</li>
 *   <li>LIMIT n with n &gt; maxRows → rewritten to maxRows;</li>
 *   <li>OFFSET (pagination) → rejected, not supported by the analysis pipeline;</li>
 *   <li>unparseable SQL → returned unchanged (the validator / database reports it).</li>
 * </ul>
 *
 * <p>jsqlparser 5.x: LIMIT lives on the Select base class, so both plain
 * SELECTs and set operations (UNION ...) are covered.
 */
public final class SqlLimitEnforcer {

    private SqlLimitEnforcer() {
    }

    /** @return SQL guaranteed to carry a LIMIT ≤ maxRows, or the input unchanged. */
    public static String enforceLimit(String sql, int maxRows) {
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (Exception e) {
            return sql; // invalid SQL: validator / DB reports it
        }
        if (!(stmt instanceof Select select)) {
            return sql;
        }

        Limit limit = select.getLimit();
        // jsqlparser 5.x: OFFSET is a top-level field on Select, not on Limit
        if ((limit != null && limit.getOffset() != null) || select.getOffset() != null) {
            // Pagination is not supported by the pipeline
            throw new IllegalArgumentException("不支持分页查询（OFFSET）");
        }
        if (limit == null) {
            Limit fresh = new Limit();
            fresh.setRowCount(new LongValue(maxRows));
            select.setLimit(fresh);
            return select.toString();
        }
        Expression rc = limit.getRowCount();
        if (rc instanceof LongValue lv) {
            if (lv.getValue() > maxRows) {
                limit.setRowCount(new LongValue(maxRows));
            }
        } else {
            // expression row count (e.g. LIMIT ?) — clamp conservatively
            limit.setRowCount(new LongValue(maxRows));
        }
        return select.toString();
    }
}
