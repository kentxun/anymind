package com.anymind.promptrecorder.storage;

import com.anymind.promptrecorder.model.SyncModels.ChangeRequest;
import com.anymind.promptrecorder.model.SyncModels.PullChange;
import com.anymind.promptrecorder.model.SyncModels.PullRequest;
import com.anymind.promptrecorder.model.SyncModels.PullResponse;
import com.anymind.promptrecorder.model.SyncModels.PushRequest;
import com.anymind.promptrecorder.model.SyncModels.PushResponse;
import com.anymind.promptrecorder.model.SyncModels.PushResult;
import com.anymind.promptrecorder.util.JsonUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SyncService {
    private final SpaceDatabase spaceDatabase;

    public SyncService(SpaceDatabase spaceDatabase) {
        this.spaceDatabase = spaceDatabase;
    }

    public PushResponse push(PushRequest request) {
        List<PushResult> results = new ArrayList<>();
        long maxRev = 0;
        String now = Instant.now().toString();

        try (Connection conn = spaceDatabase.open(request.getSpaceId())) {
            conn.setAutoCommit(false);
            try {
                if (request.getChanges() != null) {
                    for (ChangeRequest change : request.getChanges()) {
                        Long existingRev = findExistingRev(conn, change.getId());
                        boolean conflict = change.getBaseRev() != null
                            && existingRev != null
                            && existingRev > change.getBaseRev();

                        long rev = insertChange(conn, change.getId(), change.isDeleted(), now);
                        if (existingRev == null) {
                            insertRecord(conn, change, request.getDeviceId(), rev, now);
                        } else {
                            updateRecord(conn, change, request.getDeviceId(), rev, now);
                        }
                        results.add(new PushResult(change.getId(), rev, now, conflict));
                        maxRev = Math.max(maxRev, rev);
                    }
                }
                maxRev = Math.max(maxRev, queryMaxRev(conn));
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Push failed", e);
        }

        return new PushResponse(results, maxRev);
    }

    public PullResponse pull(PullRequest request) {
        long since = request.getSinceRev() == null ? 0 : request.getSinceRev();
        int limit = request.getLimit() == null ? 200 : request.getLimit();
        List<PullChange> changes = new ArrayList<>();
        long maxRev = 0;

        String sql = "SELECT c.rev, r.id, r.content, r.system_tags_json, r.user_tags_json, " +
            "r.created_at, r.updated_at_client, r.deleted, r.server_rev, r.server_updated_at " +
            "FROM changes c " +
            "JOIN records r ON r.id = c.record_id " +
            "WHERE c.rev > ? " +
            "ORDER BY c.rev ASC " +
            "LIMIT ?;";

        try (Connection conn = spaceDatabase.open(request.getSpaceId());
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, since);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String content = rs.getString("content");
                    List<String> systemTags = JsonUtils.toList(rs.getString("system_tags_json"));
                    List<String> userTags = JsonUtils.toList(rs.getString("user_tags_json"));
                    String createdAt = rs.getString("created_at");
                    String updatedAt = rs.getString("updated_at_client");
                    boolean deleted = rs.getInt("deleted") != 0;
                    long serverRev = rs.getLong("server_rev");
                    String serverUpdatedAt = rs.getString("server_updated_at");
                    changes.add(new PullChange(
                        id,
                        content,
                        systemTags,
                        userTags,
                        createdAt,
                        updatedAt,
                        deleted,
                        serverRev,
                        serverUpdatedAt
                    ));
                }
            }
            maxRev = queryMaxRev(conn);
        } catch (Exception e) {
            throw new IllegalStateException("Pull failed", e);
        }

        return new PullResponse(changes, maxRev);
    }

    private Long findExistingRev(Connection conn, String id) throws Exception {
        String sql = "SELECT server_rev FROM records WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getLong("server_rev");
            }
        }
    }

    private long insertChange(Connection conn, String recordId, boolean deleted, String now) throws Exception {
        String sql = "INSERT INTO changes (record_id, deleted, server_updated_at) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, recordId);
            stmt.setInt(2, deleted ? 1 : 0);
            stmt.setString(3, now);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new IllegalStateException("Failed to create change record");
    }

    private void insertRecord(Connection conn, ChangeRequest change, String deviceId, long rev, String now) throws Exception {
        String sql = "INSERT INTO records " +
            "(id, content, system_tags_json, user_tags_json, created_at, updated_at_client, deleted, server_rev, server_updated_at, last_device_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, change.getId());
            stmt.setString(2, change.getContent());
            stmt.setString(3, JsonUtils.toJson(change.getSystemTags()));
            stmt.setString(4, JsonUtils.toJson(change.getUserTags()));
            stmt.setString(5, change.getCreatedAt());
            stmt.setString(6, change.getUpdatedAt());
            stmt.setInt(7, change.isDeleted() ? 1 : 0);
            stmt.setLong(8, rev);
            stmt.setString(9, now);
            stmt.setString(10, deviceId);
            stmt.executeUpdate();
        }
    }

    private void updateRecord(Connection conn, ChangeRequest change, String deviceId, long rev, String now) throws Exception {
        String sql = "UPDATE records SET " +
            "content = ?, " +
            "system_tags_json = ?, " +
            "user_tags_json = ?, " +
            "updated_at_client = ?, " +
            "deleted = ?, " +
            "server_rev = ?, " +
            "server_updated_at = ?, " +
            "last_device_id = ? " +
            "WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, change.getContent());
            stmt.setString(2, JsonUtils.toJson(change.getSystemTags()));
            stmt.setString(3, JsonUtils.toJson(change.getUserTags()));
            stmt.setString(4, change.getUpdatedAt());
            stmt.setInt(5, change.isDeleted() ? 1 : 0);
            stmt.setLong(6, rev);
            stmt.setString(7, now);
            stmt.setString(8, deviceId);
            stmt.setString(9, change.getId());
            stmt.executeUpdate();
        }
    }

    private long queryMaxRev(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT IFNULL(MAX(rev), 0) AS max_rev FROM changes")) {
            if (rs.next()) {
                return rs.getLong("max_rev");
            }
            return 0;
        }
    }
}
