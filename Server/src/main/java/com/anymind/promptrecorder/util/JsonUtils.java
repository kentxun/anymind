package com.anymind.promptrecorder.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;

public final class JsonUtils {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<List<String>>() {};

    private JsonUtils() {}

    public static String toJson(List<String> values) {
        List<String> safe = values == null ? Collections.emptyList() : values;
        try {
            return OBJECT_MAPPER.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public static List<String> toList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
