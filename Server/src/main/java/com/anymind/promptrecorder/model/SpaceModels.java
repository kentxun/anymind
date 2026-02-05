package com.anymind.promptrecorder.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class SpaceModels {
    private SpaceModels() {}

    public static class SpaceCreateRequest {
        @JsonProperty("name")
        private String name;

        public SpaceCreateRequest() {}

        public SpaceCreateRequest(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class SpaceCreateResponse {
        @JsonProperty("space_id")
        private String spaceId;
        @JsonProperty("space_secret")
        private String spaceSecret;
        @JsonProperty("created_at")
        private String createdAt;

        public SpaceCreateResponse() {}

        public SpaceCreateResponse(String spaceId, String spaceSecret, String createdAt) {
            this.spaceId = spaceId;
            this.spaceSecret = spaceSecret;
            this.createdAt = createdAt;
        }

        public String getSpaceId() {
            return spaceId;
        }

        public String getSpaceSecret() {
            return spaceSecret;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }
}
