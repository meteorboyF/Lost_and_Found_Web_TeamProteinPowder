package com.teamproteinpowder.lostfound.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifies, at startup, that the student-ID uniqueness guarantee actually
 * exists in the database.
 *
 * Why this is needed: ddl-auto=update creates missing indexes, but when that
 * fails it fails silently. If a database already holds duplicate student IDs —
 * possible if two registrations raced before the constraint existed — Hibernate
 * cannot add the unique index, logs nothing at WARN or ERROR, and the
 * application starts looking perfectly healthy with the guarantee missing.
 * That was reproduced, not assumed.
 *
 * So this check:
 *   - creates the unique index itself when it is missing and safe to add;
 *   - when duplicates block it, logs an ERROR naming every conflicting ID, so
 *     an operator can decide which account keeps each one.
 *
 * It deliberately does not refuse to start or merge accounts. Choosing which of
 * two real people owns a student ID is a human decision, and taking the whole
 * service down over it would be worse than running without the constraint for
 * as long as it takes someone to read the log.
 */
@Component
@Order(5)   // before seeding, so seeded rows are checked against a real constraint
public class SchemaIntegrityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaIntegrityCheck.class);

    static final String INDEX = "uk_user_student_id";

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public SchemaIntegrityCheck(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureUniqueStudentId();
        } catch (Exception e) {
            // A failing integrity check must never take the application down.
            log.error("Could not verify the student ID uniqueness constraint", e);
        }
    }

    void ensureUniqueStudentId() throws Exception {
        if (hasUniqueIndex()) {
            return;
        }

        List<String> duplicates = jdbc.queryForList(
                "SELECT UPPER(student_id) FROM users WHERE student_id IS NOT NULL " +
                "GROUP BY UPPER(student_id) HAVING COUNT(*) > 1",
                String.class);

        if (!duplicates.isEmpty()) {
            log.error("""
                    Student IDs are NOT protected against duplicates: the unique index '{}' \
                    is missing and cannot be created because {} student ID(s) are already \
                    shared by more than one account: {}. Decide which account keeps each ID, \
                    change or remove the others, then restart to apply the constraint.""",
                    INDEX, duplicates.size(), duplicates);
            return;
        }

        jdbc.execute("CREATE UNIQUE INDEX " + INDEX + " ON users (student_id)");
        log.info("Created missing unique index {} on users.student_id", INDEX);
    }

    /**
     * Portable across MySQL and H2 by asking JDBC metadata instead of a
     * vendor-specific information_schema query. Names are compared
     * case-insensitively because the two databases report them differently.
     */
    boolean hasUniqueIndex() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();
            for (String table : new String[] {"users", "USERS"}) {
                try (ResultSet rs = meta.getIndexInfo(c.getCatalog(), null, table, true, false)) {
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name != null && name.toLowerCase(Locale.ROOT).equals(INDEX)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
