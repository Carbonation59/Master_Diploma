package ru.beeline.architecting_graph.service.analyse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

            Map<String, Map<String, Object>> uniqueCycles = new LinkedHashMap<>();

            while (result.hasNext()) {
                Record record = result.next();
                Path path = record.get("path").asPath();

                List<Long> nodeIds = new ArrayList<>();
                for (Node node : path.nodes()) {
                    nodeIds.add(node.id());
                }

                if (nodeIds.size() >= 2 && nodeIds.get(0).equals(nodeIds.get(nodeIds.size() - 1))) {
                    nodeIds.remove(nodeIds.size() - 1);
                }

                List<Long> sortedIds = new ArrayList<>(nodeIds);
                Collections.sort(sortedIds);
                String key = sortedIds.stream().map(String::valueOf).collect(Collectors.joining(","));

                if (uniqueCycles.containsKey(key)) {
                    continue;
                }

                List<Map<String, Object>> pathNodes = new ArrayList<>();
                Map<Long, String> idToName = new HashMap<>();
                for (Node node : path.nodes()) {
                    Map<String, Object> nodeMap = new LinkedHashMap<>();
                    nodeMap.put("id", node.id());
                    String label = node.labels().iterator().next();
                    nodeMap.put("label", label);
                    Map<String, Object> props = node.asMap();
                    nodeMap.put("properties", props);
                    pathNodes.add(nodeMap);
                    // Сохраняем имя для связей
                    idToName.put(node.id(), (String) props.getOrDefault("name", "null"));
                }

                List<Map<String, Object>> relationships = new ArrayList<>();
                for (Relationship rel : path.relationships()) {
                    Map<String, Object> relMap = new LinkedHashMap<>();
                    relMap.put("from", rel.startNodeId());
                    relMap.put("to", rel.endNodeId());
                    relMap.put("type", rel.type());
                    relMap.put("properties", rel.asMap());
                    relMap.put("fromName", idToName.getOrDefault(rel.startNodeId(), "null"));
                    relMap.put("toName", idToName.getOrDefault(rel.endNodeId(), "null"));
                    relationships.add(relMap);
                }

                Map<String, Object> cycleEntry = new LinkedHashMap<>();
                cycleEntry.put("id", uniqueCycles.size() + 1);
                cycleEntry.put("path", pathNodes);
                cycleEntry.put("relationships", relationships);

                uniqueCycles.put(key, cycleEntry);
            }

            List<Map<String, Object>> cyclesList = new ArrayList<>(uniqueCycles.values());

            Map<String, Object> responseMap = new LinkedHashMap<>();
            responseMap.put("analysisType", "cycles");
            responseMap.put("description",
                "Обнаружены циклические зависимости, которые могут усложнять тестирование, сборку и повторное использование компонентов.");
            responseMap.put("cycles", cyclesList);

            String jsonResponse = objectMapper.writeValueAsString(responseMap);
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

    public ResponseEntity<String> findGodElements(String graphTag, String nodeTypes, String relTypes,String nodeIdentifiers) {

        try {
            graphTag = parseGodElementParam.parseGraphTag(graphTag);
            nodeTypes = parseGodElementParam.parseNodeTypes(nodeTypes);
            relTypes = parseGodElementParam.parseRelTypes(relTypes);
            nodeIdentifiers = parseGodElementParam.parseNodeIdentifiers(nodeIdentifiers, graphTag);

            Result result = analyseRepository.findGodElements(graphTag, nodeTypes, relTypes, nodeIdentifiers);

            List<Map<String, Object>> nodes = new ArrayList<>();

            
            double averageDegree = 0;

            while (result.hasNext()) {
                Record record = result.next();
                if(averageDegree == 0) {
                    averageDegree = record.get("avgDegree").asDouble();
                }
                Node node = record.get("component").asNode();
                long totalDegree = record.get("totalDegree").asLong();

                Map<String, Object> nodeMap = new LinkedHashMap<>();
                nodeMap.put("id", node.id());
                nodeMap.put("label", node.labels().iterator().next());
                nodeMap.put("properties", node.asMap());
                nodeMap.put("totalDegree", totalDegree);
                nodes.add(nodeMap);
            }

            Map<String, Object> responseMap = new LinkedHashMap<>();
            responseMap.put("nodes", nodes);
            responseMap.put("averageDegree", averageDegree);

            String jsonResponse = objectMapper.writeValueAsString(responseMap);
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
