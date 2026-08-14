package com.datarobort.web.agent;

import com.datarobort.core.entity.DsTable;
import com.datarobort.core.mapper.DsTableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only SQL guard. Validates a generated SQL statement before it reaches
 * the executor. Layered checks:
 * <ol>
 *   <li>MySQL versioned comments ({@code /*! ... *!/}) are stripped — they can
 *       smuggle statements past a plain parser;</li>
 *   <li>raw keyword check rejects file-export side effects
 *       ({@code INTO OUTFILE / DUMPFILE / FILE});</li>
 *   <li>single-statement parse: multi-statement input fails here;</li>
 *   <li>must be a {@code SELECT};</li>
 *   <li>WITH clauses are rejected — writeable CTEs (DELETE/UPDATE/INSERT
 *       ... RETURNING) can hide writes inside a SELECT;</li>
 *   <li>dangerous function blacklist (SLEEP, BENCHMARK, LOAD_FILE, ...) —
 *       a blacklist keeps common analytical functions like DATE_FORMAT working;</li>
 *   <li>table-name whitelist from the datasource metadata snapshot
 *       (ds_table). When the snapshot is empty (legacy datasource) the check
 *       degrades to a warning unless {@code datarobort.security.sql-whitelist-required}.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlValidator {

    /** MySQL versioned comments: /*!50000 ... *&#47; — strip their content. */
    private static final Pattern VERSIONED_COMMENT = Pattern.compile("/\\*!\\d*.*?\\*/", Pattern.DOTALL);
    /** File-export side effects on MySQL. */
    private static final Pattern INTO_OUTFILE = Pattern.compile(
            "\\bINTO\\s+(OUTFILE|DUMPFILE|FILE)\\s+['\"]", Pattern.CASE_INSENSITIVE);
    /** Functions that block the request or touch the filesystem. */
    private static final Pattern DANGEROUS_FUNCTION = Pattern.compile(
            "\\b(SLEEP|BENCHMARK|LOAD_FILE|GET_LOCK|RELEASE_LOCK|EXTRACTVALUE|UPDATEXML|"
                    + "MASTER_POS_WAIT|PG_SLEEP|PG_READ_FILE|PG_READ_BINARY_FILE)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    /** Cap error text so table/schema details never leak into SSE responses. */
    static final int MAX_ERROR_LEN = 200;

    private final DsTableMapper tableMapper;

    @Value("${datarobort.security.sql-whitelist-required:false}")
    private boolean whitelistRequired;

    /**
     * Strips MySQL versioned comments ({@code /*! ... *&#47;}) so the executor
     * runs the same text the validator parsed. MySQL executes the content of
     * versioned comments, so leaving them in the raw SQL would let a smuggled
     * statement run even though the validator never saw it.
     */
    public static String stripVersionedComments(String sql) {
        if (sql == null) {
            return null;
        }
        return VERSIONED_COMMENT.matcher(sql).replaceAll(" ");
    }

    /**
     * @param sql  generated SQL
     * @param dsId the business datasource whose ds_table snapshot forms the whitelist
     * @return null when valid, otherwise a short reason (≤ 200 chars)
     */
    public String validate(String sql, Long dsId) {
        if (sql == null || sql.isBlank()) {
            return "SQL 为空";
        }

        String stripped = stripVersionedComments(sql);

        if (INTO_OUTFILE.matcher(stripped).find()) {
            return "禁止 INTO OUTFILE/DUMPFILE 导出文件";
        }

        final Select select;
        try {
            // parseStatements counts statements: `SELECT 1; DROP TABLE x` yields
            // two — parse() alone silently returns just the first one.
            java.util.List<net.sf.jsqlparser.statement.Statement> stmts =
                    CCJSqlParserUtil.parseStatements(stripped);
            if (stmts.size() != 1) {
                return "禁止多语句（只允许单条 SELECT）";
            }
            net.sf.jsqlparser.statement.Statement stmt = stmts.get(0);
            if (!(stmt instanceof Select s)) {
                return "只允许 SELECT 语句，当前为: " + stmt.getClass().getSimpleName();
            }
            select = s;
        } catch (Exception e) {
            return "SQL 语法错误: " + truncate(e.getMessage());
        }

        // WITH can wrap writeable CTEs: WITH d AS (DELETE FROM t RETURNING *) SELECT * FROM d
        if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
            return "禁止 WITH 语句（可能包含写入操作）";
        }

        Matcher m = DANGEROUS_FUNCTION.matcher(stripped);
        if (m.find()) {
            return "禁止危险函数: " + m.group(1).toUpperCase(Locale.ROOT);
        }

        String tableError = checkTables(select, dsId);
        if (tableError != null) {
            return tableError;
        }

        return null;
    }

    private String checkTables(Select select, Long dsId) {
        if (dsId == null) {
            return null;
        }
        Set<String> allowed = new HashSet<>();
        for (DsTable t : tableMapper.selectByDsId(dsId)) {
            if (t.getTableName() != null) {
                allowed.add(t.getTableName().toLowerCase(Locale.ROOT));
            }
        }
        if (allowed.isEmpty()) {
            // No metadata snapshot for this datasource (legacy) — degrade to
            // the other checks instead of blocking everything.
            if (whitelistRequired) {
                return "数据源元数据缺失，无法校验表名";
            }
            log.warn("no ds_table metadata for datasource {}, table whitelist skipped", dsId);
            return null;
        }

        TablesNamesFinder finder = new TablesNamesFinder();
        Set<String> found = finder.getTables((net.sf.jsqlparser.statement.Statement) select);
        for (String name : found) {
            String simple = lastSegment(name);
            if (!allowed.contains(simple)) {
                return "表不在白名单中: " + simple;
            }
        }
        return null;
    }

    /** Strip quotes and schema prefix: `demo_business`.`orders` -> orders. */
    private String lastSegment(String name) {
        String n = name;
        int dot = n.lastIndexOf('.');
        if (dot >= 0) {
            n = n.substring(dot + 1);
        }
        return n.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
    }

    static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN) + "...";
    }
}
