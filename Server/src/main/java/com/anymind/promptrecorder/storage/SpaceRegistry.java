package com.anymind.promptrecorder.storage;

import com.anymind.promptrecorder.util.TokenGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SpaceRegistry {
    private final StoragePaths paths;

    public SpaceRegistry(StoragePaths paths) {
        this.paths = paths;
        init();
    }

    public SpaceInfo createSpace(String name) {
        String spaceId = TokenGenerator.spaceId();
        String spaceSecret = TokenGenerator.spaceSecret();
        String createdAt = Instant.now().toString();

        String sql = "INSERT INTO spaces (space_id, space_secret, name, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = openRegistry();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, spaceId);
            stmt.setString(2, spaceSecret);
            stmt.setString(3, name);
            stmt.setString(4, createdAt);
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create space", e);
        }

        return new SpaceInfo(spaceId, spaceSecret, createdAt, name);
    }

    public Optional<SpaceInfo> findSpace(String spaceId) {
        String sql = "SELECT space_id, space_secret, name, created_at FROM spaces WHERE space_id = ?";
        try (Connection conn = openRegistry();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, spaceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SpaceInfo(
                    rs.getString("space_id"),
                    rs.getString("space_secret"),
                    rs.getString("created_at"),
                    rs.getString("name")
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to query space", e);
        }
    }

    public boolean validateSecret(String spaceId, String secret) {
        return findSpace(spaceId)
            .map(info -> info.getSpaceSecret().equals(secret))
            .orElse(false);
    }

    private void init() {
        try {
            Path root = paths.root();
            Files.createDirectories(root);
            Path registry = paths.registryDb();
            Files.createDirectories(registry.getParent());

            try (Connection conn = openRegistry();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                String sql = "CREATE TABLE IF NOT EXISTS spaces (" +
                    "space_id TEXT PRIMARY KEY, " +
                    "space_secret TEXT NOT NULL, " +
                    "name TEXT, " +
                    "created_at TEXT NOT NULL" +
                    ");";
                stmt.execute(sql);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init registry", e);
        }
    }

    private Connection openRegistry() throws Exception {
        String url = "jdbc:sqlite:" + paths.registryDb();
        return DriverManager.getConnection(url);
    }

    public static class SpaceInfo {
        private final String spaceId;
        private final String spaceSecret;
        private final String createdAt;
        private final String name;

        public SpaceInfo(String spaceId, String spaceSecret, String createdAt, String name) {
            this.spaceId = spaceId;
            this.spaceSecret = spaceSecret;
            this.createdAt = createdAt;
            this.name = name;
        }

        public String getSpaceId() { return spaceId; }
        public String getSpaceSecret() { return spaceSecret; }
        public String getCreatedAt() { return createdAt; }
        public String getName() { return name; }
    }
}
