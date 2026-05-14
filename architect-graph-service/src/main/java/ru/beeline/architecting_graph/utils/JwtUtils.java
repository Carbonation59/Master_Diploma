/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;

import java.util.Base64;
import java.util.Map;


@Slf4j
public class JwtUtils {
    public static Map<String, Object> encodeJWT(String token) {
        try {
            String[] parts = token.split("\\.");
            Base64.Decoder decoder = Base64.getUrlDecoder();

            String payload = new String(decoder.decode(parts[1]));
            String jsonObject = JSONObject.escape(payload).replace("\\", "");

            ObjectMapper mapper = new ObjectMapper();

            // convert JSON string to Map
            Map<String, Object> map = mapper.readValue(jsonObject, Map.class);

            return map;

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return null;
    }

    public static boolean isIpAddress(String input) {
        String ipPattern =
                "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.|$)){4}$";
        return input != null && input.matches(ipPattern);
    }
}