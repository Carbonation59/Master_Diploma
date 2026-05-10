/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class StructurizrClient {
    RestTemplate restTemplate;
    private final String graphvizUrl;

    public StructurizrClient(@Value("${spring.services.graphviz.url}") String graphvizUrl,
                             RestTemplate restTemplate) {
        this.graphvizUrl = graphvizUrl;
        this.restTemplate = restTemplate;
    }

    public String changeJson(String json) throws Exception {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(json, headers);

            HttpEntity<String> response = restTemplate.exchange(graphvizUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error(e.getMessage(), e.getStackTrace());
            throw new Exception("Failed to call Graphviz API: " + e.getMessage(), e);
        }
    }
}
