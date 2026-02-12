package org.fitznet.fun.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class providing a shared ObjectMapper instance for JSON serialization/deserialization.
 */
public class JsonUtils {
    /** Shared ObjectMapper instance for JSON processing. */
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
}
