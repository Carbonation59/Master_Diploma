package ru.beeline.architecting_graph.service.analyse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.beeline.architecting_graph.repository.neo4j.AnalyseRepository;

@Component
public class AnalyseService {

    @Autowired
    ParseCycleParams parseCycleParams;

    @Autowired
    ParseSinglePointsParams parseSinglePointsParams;

    @Autowired
    ParseGodElementParam parseGodElementParam;

    @Autowired
    ParsePathCapacityParam parsePathCapacityParam;

    @Autowired
    ParseCriticalInfrastructureParam parseCriticalInfrastructureParam;

    @Autowired
    ParsePerimeterViolationParam parsePerimeterViolationParam;

    @Autowired
    AnalyseRepository analyseRepository;

    @Autowired
    ObjectMapper objectMapper;

    public ResponseEntity<String> findCycles(String graphTag, String nodeTypes, String relTypes,
            String nodeIdentifiers) {

        try {
            graphTag = parseCycleParams.parseGraphTag(graphTag, nodeTypes);
            nodeTypes = parseCycleParams.parseNodeTypes(nodeTypes, graphTag);
            relTypes = parseCycleParams.parseRelTypes(relTypes);
            nodeIdentifiers = parseCycleParams.parseNodeIdentifiers(nodeIdentifiers);

            Result result = analyseRepository.findCycles(graphTag, nodeTypes, relTypes, nodeIdentifiers);
            Map<String, Object> graph = new HashMap<>();
            Set<Map<String, Object>> nodes = new HashSet<>();
            Set<Map<String, Object>> edges = new HashSet<>();

            while (result.hasNext()) {
                Record record = result.next();
                Path path = record.get("path").asPath();

                // Добавляем узлы
                for (Node node : path.nodes()) {
                    Map<String, Object> nodeMap = new HashMap<>();
                    nodeMap.put("id", node.id());
                    nodeMap.put("label", node.labels().iterator().next());
                    nodeMap.put("properties", node.asMap());
                    nodes.add(nodeMap);
                }

                // Добавляем связи
                for (Relationship rel : path.relationships()) {
                    Map<String, Object> edgeMap = new HashMap<>();
                    edgeMap.put("id", rel.id());
                    edgeMap.put("from", rel.startNodeId());
                    edgeMap.put("to", rel.endNodeId());
                    edgeMap.put("label", rel.type());
                    edgeMap.put("properties", rel.asMap());
                    edges.add(edgeMap);
                }
            }

            graph.put("nodes", nodes);
            graph.put("edges", edges);

            String jsonResponse = objectMapper.writeValueAsString(graph);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    public ResponseEntity<String> findSinglePoints(String graphTag, String nodeTypes, String relTypes,
            String nodeIdentifiers) {

        try {
            graphTag = parseSinglePointsParams.parseGraphTag(graphTag);
            nodeTypes = parseSinglePointsParams.parseNodeTypes(nodeTypes);
            relTypes = parseSinglePointsParams.parseRelTypes(relTypes);
            nodeIdentifiers = parseSinglePointsParams.parseNodeIdentifiers(nodeIdentifiers, graphTag);

            Result result = analyseRepository.findSinglePoints(graphTag, nodeTypes, relTypes, nodeIdentifiers);

            Map<String, Object> graph = new HashMap<>();
            Set<Map<String, Object>> nodes = new HashSet<>();

            while (result.hasNext()) {
                Record record = result.next();
                Node node = record.get("node").asNode();

                Map<String, Object> nodeMap = new HashMap<>();

                nodeMap.put("id", node.id());
                nodeMap.put("label", node.labels().iterator().next());
                nodeMap.put("properties", node.asMap());

                nodes.add(nodeMap);
            }

            graph.put("nodes", nodes);

            String jsonResponse = objectMapper.writeValueAsString(graph);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    public ResponseEntity<String> findGodElements(String graphTag, String nodeTypes, String relTypes,
            String nodeIdentifiers) {

        try {
            graphTag = parseGodElementParam.parseGraphTag(graphTag);
            nodeTypes = parseGodElementParam.parseNodeTypes(nodeTypes);
            relTypes = parseGodElementParam.parseRelTypes(relTypes);
            nodeIdentifiers = parseGodElementParam.parseNodeIdentifiers(nodeIdentifiers, graphTag);

            Result result = analyseRepository.findGodElements(graphTag, nodeTypes, relTypes, nodeIdentifiers);

            Map<String, Object> graph = new HashMap<>();
            Set<Map<String, Object>> nodes = new HashSet<>();

            while (result.hasNext()) {
                Record record = result.next();
                Node node = record.get("component").asNode();

                Map<String, Object> nodeMap = new HashMap<>();

                nodeMap.put("id", node.id());
                nodeMap.put("label", node.labels().iterator().next());
                nodeMap.put("properties", node.asMap());

                nodes.add(nodeMap);
            }

            graph.put("nodes", nodes);

            String jsonResponse = objectMapper.writeValueAsString(graph);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    public ResponseEntity<String> findPathCapacity(String nodeTypes, String relTypes, String nodeIdentifiers) {

        try {
            nodeTypes = parsePathCapacityParam.parseNodeTypes(nodeTypes);
            relTypes = parsePathCapacityParam.parseRelTypes(relTypes);
            nodeIdentifiers = parsePathCapacityParam.parseNodeIdentifiers(nodeIdentifiers, nodeTypes, relTypes);

            Map<String, Object> graph = new HashMap<>();

            if (nodeIdentifiers.equals("")) {
                graph.put("rps", 0.0);
                graph.put("error_rate", 0.0);
                graph.put("latency", 0.0);
                graph.put("nodes", Collections.emptyList());
                graph.put("edges", Collections.emptyList());

                String jsonResponse = objectMapper.writeValueAsString(graph);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(jsonResponse);
            }

            Result result = analyseRepository.findPathCapacity(nodeTypes, relTypes, nodeIdentifiers);
            Record record = result.next();

            double rps = record.get("rps").asDouble();
            double errorRate = record.get("error_rate").asDouble();
            double latency = record.get("latency").asDouble();
            Path path = record.get("path").asPath();

            graph.put("rps", rps);
            graph.put("error_rate", errorRate);
            graph.put("latency", latency);

            List<Map<String, Object>> nodesList = new ArrayList<>();
            for (Node node : path.nodes()) {
                Map<String, Object> nodeMap = new HashMap<>();
                nodeMap.put("id", node.id());
                nodeMap.put("label", node.labels().iterator().next());
                nodeMap.put("properties", node.asMap());
                nodesList.add(nodeMap);
            }
            graph.put("nodes", nodesList);

            List<Map<String, Object>> edgesList = new ArrayList<>();
            for (Relationship rel : path.relationships()) {
                Map<String, Object> relMap = new HashMap<>();
                relMap.put("id", rel.id());
                relMap.put("from", rel.startNodeId());
                relMap.put("to", rel.endNodeId());
                relMap.put("label", rel.type());
                relMap.put("properties", rel.asMap());
                edgesList.add(relMap);
            }
            graph.put("edges", edgesList);

            String jsonResponse = objectMapper.writeValueAsString(graph);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    public ResponseEntity<String> findCriticalInfrastructure(String nodeIdentifiers) {

        try {
            nodeIdentifiers = parseCriticalInfrastructureParam.parseNodeIdentifiers(nodeIdentifiers);
            Result result = analyseRepository.findCriticalInfrastructure(nodeIdentifiers);

            Map<String, Object> graph = new HashMap<>();
            Set<Map<String, Object>> nodes = new HashSet<>();

            while (result.hasNext()) {
                Record record = result.next();
                Node node = record.get("component").asNode();

                Map<String, Object> nodeMap = new HashMap<>();

                nodeMap.put("id", node.id());
                nodeMap.put("label", node.labels().iterator().next());
                nodeMap.put("properties", node.asMap());

                nodes.add(nodeMap);
            }

            graph.put("nodes", nodes);

            String jsonResponse = objectMapper.writeValueAsString(graph);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    public ResponseEntity<String> findPerimeterViolation(String deploymentNodeIdentifier, String properties) {

        try {
            deploymentNodeIdentifier = parsePerimeterViolationParam.parseDeploymentNodeIdentifier(deploymentNodeIdentifier);
            properties = parsePerimeterViolationParam.parseNodeProperties(properties);

            Result result = analyseRepository.findPerimeterViolation(deploymentNodeIdentifier, properties);

            Map<String, Object> graph = new HashMap<>();
            Set<Map<String, Object>> nodes = new HashSet<>();

            while (result.hasNext()) {
                Record record = result.next();
                Node node = record.get("component").asNode();

                Map<String, Object> nodeMap = new HashMap<>();

                nodeMap.put("id", node.id());
                nodeMap.put("label", node.labels().iterator().next());
                nodeMap.put("properties", node.asMap());

                nodes.add(nodeMap);
            }

            graph.put("nodes", nodes);

            String jsonResponse = objectMapper.writeValueAsString(graph);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }
}
