/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.beeline.architecting_graph.utils.JwtUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

@Slf4j
@Service
public class AuthSSOClient {

    RestTemplate restTemplate;

    private final String serverUrl;

    public AuthSSOClient(@Value("${integration.authsso-server-url}") String serverUrl,
                         RestTemplate restTemplate) {
        this.serverUrl = serverUrl;
        this.restTemplate = restTemplate;
    }

    private static String accessToken;
    private static ZonedDateTime expiresAt;

    public String getToken() {

        if (accessToken == null || expiresAt.isBefore(ZonedDateTime.now(ZoneId.of("UTC")))) {
            accessToken = obtainAccessToken();
            expiresAt =  Instant.ofEpochSecond((Integer) JwtUtils.encodeJWT(accessToken).get("exp")).atZone(ZoneId.of("UTC"));

        }
        return accessToken;
    }

    public String obtainAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<String> response = restTemplate.postForEntity(serverUrl, null, String.class);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            return responseMap.get("access_token").toString();
        } catch (Exception e) {
            throw new RuntimeException("Error while parsing response", e);
        }
    }
}