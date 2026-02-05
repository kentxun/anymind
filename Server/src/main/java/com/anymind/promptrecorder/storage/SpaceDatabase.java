package com.anymind.promptrecorder.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.springframework.stereotype.Component;

@Component
public class SpaceDatabase {
    private final StoragePaths paths;

    public SpaceDatabase(StoragePaths paths) {
        this.paths = paths;
    }

    public Connection open(String spaceId) throws Exception {
        Path dir = paths.spaceDir(spaceId);
        Files.createDirectories(dir);
        Path dbPath = paths.spaceDb(spaceId);
        String url = "jdbc:sqlite:" + dbPath;
        Connection conn = DriverManager.getConnection(url);
        ensureSchema(conn);
        return conn;
    }

    private void ensureSchema(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            String recordsSql = "CREATE TABLE IF NOT EXISTS records (" +
                "id TEXT PRIMARY KEY, " +
                "content TEXT NOT NULL, " +
                "system_tags_json TEXT NOT NULL, " +
                "user_tags_json TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at_client TEXT NOT NULL, " +
                "deleted INTEGER NOT NULL DEFAULT 0, " +
                "server_rev INTEGER NOT NULL, " +
                "server_updated_at TEXT NOT NULL, " +
                "last_device_id TEXT" +
                ");";
            stmt.execute(recordsSql);
            String changesSql = "CREATE TABLE IF NOT EXISTS changes (" +
                "rev INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "record_id TEXT NOT NULL, " +
                "deleted INTEGER NOT NULL, " +
                "server_updated_at TEXT NOT NULL" +
                ");";
            stmt.execute(changesSql);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_changes_rev ON changes(rev);");
        }
    }
}
