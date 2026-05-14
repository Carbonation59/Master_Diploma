/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.getElements;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import ru.beeline.architecting_graph.service.graph.Neo4jSessionManager;

import java.util.HashMap;
import java.util.Map;

@Component
public class ElementService {

    @Autowired
    private Neo4jSessionManager neo4jSessionManager;

    private Object convertValue(Value value) {
        switch (value.type().name()) {
            case "NODE":
                Map<String, Object> nodeMap = new HashMap<>();
                nodeMap.put("labels", value.asNode().labels());
                nodeMap.put("properties", value.asNode().asMap());
                return nodeMap;

            case "RELATIONSHIP":
                Map<String, Object> relMap = new HashMap<>();
                relMap.put("type", value.asRelationship().type());
                relMap.put("properties", value.asRelationship().asMap());
                return relMap;

            default:
                return value.asObject();
        }
    }

    public ResponseEntity<String> processingQuery(String query) {
            Result result = neo4jSessionManager.getSession().run(query);
            ObjectMapper mapper = new ObjectMapper();
            StringBuilder returnElements = new StringBuilder("[");
            while (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                Map<String, Object> row = new HashMap<>();
                record.fields().forEach(field -> {
                    String key = field.key();
                    Value value = field.value();
                    Object convertedValue = convertValue(value);
                    row.put(key, convertedValue);
                });
                try {
                    String jsonRow = mapper.writeValueAsString(row);
                    returnElements.append(jsonRow);
                    if (result.hasNext()) {
                        returnElements.append(",");
                    }
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
            returnElements.append("]");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(returnElements.toString());
    }
}
