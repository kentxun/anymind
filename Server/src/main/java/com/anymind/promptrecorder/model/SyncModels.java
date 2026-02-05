package com.anymind.promptrecorder.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class SyncModels {
    private SyncModels() {}

    public static class ChangeRequest {
        @JsonProperty("id")
        private String id;
        @JsonProperty("content")
        private String content;
        @JsonProperty("system_tags")
        private List<String> systemTags;
        @JsonProperty("user_tags")
        private List<String> userTags;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("updated_at")
        private String updatedAt;
        @JsonProperty("deleted")
        private boolean deleted;
        @JsonProperty("base_rev")
        private Long baseRev;

        public ChangeRequest() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getSystemTags() { return systemTags; }
        public void setSystemTags(List<String> systemTags) { this.systemTags = systemTags; }
        public List<String> getUserTags() { return userTags; }
        public void setUserTags(List<String> userTags) { this.userTags = userTags; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
        public boolean isDeleted() { return deleted; }
        public void setDeleted(boolean deleted) { this.deleted = deleted; }
        public Long getBaseRev() { return baseRev; }
        public void setBaseRev(Long baseRev) { this.baseRev = baseRev; }
    }

    public static class PushRequest {
        @JsonProperty("space_id")
        private String spaceId;
        @JsonProperty("space_secret")
        private String spaceSecret;
        @JsonProperty("device_id")
        private String deviceId;
        @JsonProperty("changes")
        private List<ChangeRequest> changes;

        public PushRequest() {}

        public String getSpaceId() { return spaceId; }
        public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
        public String getSpaceSecret() { return spaceSecret; }
        public void setSpaceSecret(String spaceSecret) { this.spaceSecret = spaceSecret; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public List<ChangeRequest> getChanges() { return changes; }
        public void setChanges(List<ChangeRequest> changes) { this.changes = changes; }
    }

    public static class PushResult {
        @JsonProperty("id")
        private String id;
        @JsonProperty("server_rev")
        private long serverRev;
        @JsonProperty("server_updated_at")
        private String serverUpdatedAt;
        @JsonProperty("conflict")
        private boolean conflict;

        public PushResult() {}

        public PushResult(String id, long serverRev, String serverUpdatedAt, boolean conflict) {
            this.id = id;
            this.serverRev = serverRev;
            this.serverUpdatedAt = serverUpdatedAt;
            this.conflict = conflict;
        }

        public String getId() {
            return id;
        }

        public long getServerRev() {
            return serverRev;
        }

        public String getServerUpdatedAt() {
            return serverUpdatedAt;
        }

        public boolean isConflict() {
            return conflict;
        }
    }

    public static class PushResponse {
        @JsonProperty("results")
        private List<PushResult> results;
        @JsonProperty("server_rev_max")
        private long serverRevMax;

        public PushResponse() {}

        public PushResponse(List<PushResult> results, long serverRevMax) {
            this.results = results;
            this.serverRevMax = serverRevMax;
        }

        public List<PushResult> getResults() {
            return results;
        }

        public long getServerRevMax() {
            return serverRevMax;
        }
    }

    public static class PullRequest {
        @JsonProperty("space_id")
        private String spaceId;
        @JsonProperty("space_secret")
        private String spaceSecret;
        @JsonProperty("since_rev")
        private Long sinceRev;
        @JsonProperty("limit")
        private Integer limit;

        public PullRequest() {}

        public String getSpaceId() { return spaceId; }
        public void setSpaceId(String spaceId) { this.spaceId = spaceId; }
        public String getSpaceSecret() { return spaceSecret; }
        public void setSpaceSecret(String spaceSecret) { this.spaceSecret = spaceSecret; }
        public Long getSinceRev() { return sinceRev; }
        public void setSinceRev(Long sinceRev) { this.sinceRev = sinceRev; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }

    public static class PullChange {
        @JsonProperty("id")
        private String id;
        @JsonProperty("content")
        private String content;
        @JsonProperty("system_tags")
        private List<String> systemTags;
        @JsonProperty("user_tags")
        private List<String> userTags;
        @JsonProperty("created_at")
        private String createdAt;
        @JsonProperty("updated_at")
        private String updatedAt;
        @JsonProperty("deleted")
        private boolean deleted;
        @JsonProperty("server_rev")
        private long serverRev;
        @JsonProperty("server_updated_at")
        private String serverUpdatedAt;

        public PullChange() {}

        public PullChange(String id, String content, List<String> systemTags, List<String> userTags,
                          String createdAt, String updatedAt, boolean deleted, long serverRev, String serverUpdatedAt) {
            this.id = id;
            this.content = content;
            this.systemTags = systemTags;
            this.userTags = userTags;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.deleted = deleted;
            this.serverRev = serverRev;
            this.serverUpdatedAt = serverUpdatedAt;
        }

        public String getId() {
            return id;
        }

        public String getContent() {
            return content;
        }

        public List<String> getSystemTags() {
            return systemTags;
        }

        public List<String> getUserTags() {
            return userTags;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public long getServerRev() {
            return serverRev;
        }

        public String getServerUpdatedAt() {
            return serverUpdatedAt;
        }
    }

    public static class PullResponse {
        @JsonProperty("changes")
        private List<PullChange> changes;
        @JsonProperty("server_rev_max")
        private long serverRevMax;

        public PullResponse() {}

        public PullResponse(List<PullChange> changes, long serverRevMax) {
            this.changes = changes;
            this.serverRevMax = serverRevMax;
        }

        public List<PullChange> getChanges() {
            return changes;
        }

        public long getServerRevMax() {
            return serverRevMax;
        }
    }
}
