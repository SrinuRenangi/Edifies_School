package com.edifies.attendance.modules.backup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DatabaseBackupService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:postgres}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${attendance.backup.pg-bin-dir:C:\\Program Files\\PostgreSQL\\17\\bin}")
    private String pgBinDir;

    private static final List<String> MANAGED_TABLES = List.of(
            "admin_users",
            "employees",
            "attendance_records",
            "idempotency_keys",
            "attendance_photos",
            "attendance_corrections",
            "photo_retention_policies",
            "photo_cleanup_history",
            "kiosk_devices",
            "kiosk_pairing_codes",
            "kiosk_device_audit_logs"
    );

    public DatabaseBackupService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static class DumpResult {
        private final Path dumpPath;
        private final long sizeBytes;
        private final String sha256;
        private final Map<String, Long> recordCounts;
        private final boolean usedNativePgDump;

        public DumpResult(Path dumpPath, long sizeBytes, String sha256, Map<String, Long> recordCounts, boolean usedNativePgDump) {
            this.dumpPath = dumpPath;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.recordCounts = recordCounts;
            this.usedNativePgDump = usedNativePgDump;
        }

        public Path getDumpPath() { return dumpPath; }
        public long getSizeBytes() { return sizeBytes; }
        public String getSha256() { return sha256; }
        public Map<String, Long> getRecordCounts() { return recordCounts; }
        public boolean isUsedNativePgDump() { return usedNativePgDump; }
    }

    public DumpResult createDatabaseDump(Path outputSqlFile) throws Exception {
        log.info("Starting database backup for datasource: {}", datasourceUrl);
        Map<String, Long> recordCounts = collectRecordCounts();

        // 1. Try native pg_dump first
        Path pgDumpExec = findPgDumpExecutable();
        boolean success = false;
        boolean usedNative = false;

        if (pgDumpExec != null && Files.exists(pgDumpExec)) {
            try {
                executeNativePgDump(pgDumpExec, outputSqlFile);
                success = true;
                usedNative = true;
                log.info("Native pg_dump completed successfully. File size: {} bytes", Files.size(outputSqlFile));
            } catch (Exception e) {
                log.warn("Native pg_dump failed ({}), falling back to JDBC logical snapshot.", e.getMessage());
            }
        } else {
            log.info("Native pg_dump not found in '{}' or PATH; using reliable JDBC snapshot fallback.", pgBinDir);
        }

        // 2. Fallback to JDBC snapshot if native dump was not possible or failed
        if (!success) {
            executeJdbcFallbackDump(outputSqlFile);
            usedNative = false;
            log.info("JDBC logical database snapshot completed successfully. File size: {} bytes", Files.size(outputSqlFile));
        }

        long sizeBytes = Files.size(outputSqlFile);
        if (sizeBytes == 0) {
            throw new IllegalStateException("Database backup failed: Output SQL dump is 0 bytes.");
        }

        String sha256 = computeFileSha256(outputSqlFile);
        return new DumpResult(outputSqlFile, sizeBytes, sha256, recordCounts, usedNative);
    }

    public Map<String, Long> collectRecordCounts() {
        return collectRecordCountsFromConnection(null);
    }

    public Map<String, Long> collectRecordCountsFromConnection(Connection externalConn) {
        Map<String, Long> counts = new LinkedHashMap<>();
        boolean closeConn = false;
        Connection conn = externalConn;
        try {
            if (conn == null) {
                conn = dataSource.getConnection();
                closeConn = true;
            }
            DatabaseMetaData meta = conn.getMetaData();
            for (String table : MANAGED_TABLES) {
                try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
                    if (rs.next()) {
                        try (Statement stmt = conn.createStatement();
                             ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                            if (countRs.next()) {
                                counts.put(table, countRs.getLong(1));
                            }
                        }
                    } else {
                        // Case insensitive check
                        try (Statement stmt = conn.createStatement();
                             ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
                            if (countRs.next()) {
                                counts.put(table, countRs.getLong(1));
                            }
                        } catch (Exception ignored) {
                            counts.put(table, 0L);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not query count for table {}: {}", table, e.getMessage());
                    counts.put(table, 0L);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to query database metadata for record counts: {}", e.getMessage());
        } finally {
            if (closeConn && conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
        return counts;
    }

    private void executeNativePgDump(Path pgDumpExec, Path outputSqlFile) throws Exception {
        ParsedJdbcUrl parsed = parseJdbcUrl(datasourceUrl);

        List<String> command = new ArrayList<>();
        command.add(pgDumpExec.toString());
        command.add("-h");
        command.add(parsed.host);
        command.add("-p");
        command.add(String.valueOf(parsed.port));
        command.add("-U");
        command.add(datasourceUsername);
        command.add("-d");
        command.add(parsed.database);
        command.add("--no-owner");
        command.add("--no-privileges");
        command.add("-f");
        command.add(outputSqlFile.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        Map<String, String> env = pb.environment();
        if (datasourcePassword != null && !datasourcePassword.isBlank()) {
            env.put("PGPASSWORD", datasourcePassword);
        } else {
            env.put("PGPASSWORD", "");
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder processOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                processOutput.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("pg_dump exited with error code " + exitCode + ": " + processOutput);
        }
    }

    private void executeJdbcFallbackDump(Path outputSqlFile) throws Exception {
        try (Connection conn = dataSource.getConnection();
             BufferedWriter writer = Files.newBufferedWriter(outputSqlFile)) {

            writer.write("-- Staff Attendance Management System Database Snapshot\n");
            writer.write("-- Generated via JDBC Fallback Snapshot Engine\n");
            writer.write("-- Date: " + new java.util.Date() + "\n\n");
            writer.write("SET statement_timeout = 0;\n");
            writer.write("SET client_encoding = 'UTF8';\n\n");

            for (String table : MANAGED_TABLES) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM " + table)) {

                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    writer.write("-- Data for table: " + table + "\n");
                    while (rs.next()) {
                        StringBuilder insert = new StringBuilder("INSERT INTO ").append(table).append(" (");
                        for (int i = 1; i <= colCount; i++) {
                            insert.append(meta.getColumnName(i));
                            if (i < colCount) insert.append(", ");
                        }
                        insert.append(") VALUES (");

                        for (int i = 1; i <= colCount; i++) {
                            Object val = rs.getObject(i);
                            if (val == null) {
                                insert.append("NULL");
                            } else if (val instanceof Number || val instanceof Boolean) {
                                insert.append(val);
                            } else {
                                String strVal = val.toString().replace("'", "''");
                                insert.append("'").append(strVal).append("'");
                            }
                            if (i < colCount) insert.append(", ");
                        }
                        insert.append(") ON CONFLICT DO NOTHING;\n");
                        writer.write(insert.toString());
                    }
                    writer.write("\n");
                } catch (SQLException e) {
                    log.debug("Skipping non-existent table in fallback dump: {}", table);
                }
            }
        }
    }

    public Path findPgDumpExecutable() {
        if (pgBinDir != null && !pgBinDir.isBlank()) {
            Path custom = Paths.get(pgBinDir, isWindows() ? "pg_dump.exe" : "pg_dump");
            if (Files.exists(custom)) return custom;
        }

        // Common Windows paths
        String[] defaultPaths = new String[]{
                "C:\\Program Files\\PostgreSQL\\17\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\16\\bin\\pg_dump.exe",
                "C:\\Program Files\\PostgreSQL\\14\\bin\\pg_dump.exe"
        };
        for (String p : defaultPaths) {
            Path path = Paths.get(p);
            if (Files.exists(path)) return path;
        }

        // Check system PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String segment : pathEnv.split(File.pathSeparator)) {
                Path candidate = Paths.get(segment, isWindows() ? "pg_dump.exe" : "pg_dump");
                if (Files.exists(candidate)) return candidate;
            }
        }
        return null;
    }

    public Path findPsqlExecutable() {
        if (pgBinDir != null && !pgBinDir.isBlank()) {
            Path custom = Paths.get(pgBinDir, isWindows() ? "psql.exe" : "psql");
            if (Files.exists(custom)) return custom;
        }

        String[] defaultPaths = new String[]{
                "C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe",
                "C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe",
                "C:\\Program Files\\PostgreSQL\\14\\bin\\psql.exe"
        };
        for (String p : defaultPaths) {
            Path path = Paths.get(p);
            if (Files.exists(path)) return path;
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String segment : pathEnv.split(File.pathSeparator)) {
                Path candidate = Paths.get(segment, isWindows() ? "psql.exe" : "psql");
                if (Files.exists(candidate)) return candidate;
            }
        }
        return null;
    }

    public static String computeFileSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public ParsedJdbcUrl parseJdbcUrl(String url) {
        // e.g. jdbc:postgresql://localhost:5435/attendance
        Pattern pattern = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String host = matcher.group(1);
            int port = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 5432;
            String database = matcher.group(3);
            return new ParsedJdbcUrl(host, port, database);
        }
        return new ParsedJdbcUrl("localhost", 5435, "attendance");
    }

    public static class ParsedJdbcUrl {
        public final String host;
        public final int port;
        public final String database;

        public ParsedJdbcUrl(String host, int port, String database) {
            this.host = host;
            this.port = port;
            this.database = database;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
