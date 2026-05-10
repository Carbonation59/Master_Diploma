/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.createDiagrams;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.graphstream.graph.Graph;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.stream.file.FileSinkDOT;
import org.neo4j.driver.Result;
import org.neo4j.driver.types.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.client.ProductClient;
import ru.beeline.architecting_graph.client.StructurizrClient;
import ru.beeline.architecting_graph.dto.ContextElementDTO;
import ru.beeline.architecting_graph.dto.DiagramElementDTO;
import ru.beeline.architecting_graph.dto.DiagramElementInfluenceDTO;
import ru.beeline.architecting_graph.dto.ProductInfoShortDTO;
import ru.beeline.architecting_graph.exception.ValidationException;
import ru.beeline.architecting_graph.model.GraphObject;
import ru.beeline.architecting_graph.model.Workspace;
import ru.beeline.architecting_graph.repository.neo4j.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
public class DiagramService {

    @Autowired
    ViewService viewService;

    @Autowired
    GenericRepository genericRepository;

    @Autowired
    SoftwareSystemRepository softwareSystemRepository;

    @Autowired
    ContainerRepository containerRepository;

    @Autowired
    EnvironmentRepository environmentRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    StructurizrClient structurizrClient;

    @Autowired
    DeploymentNodesRepository deploymentNodesRepository;

    @Autowired
    InfrastructureNodesRepository infrastructureNodesRepository;

    @Autowired
    private ProductClient productClient;

    public Boolean checkifContainerExists(String softwareSystemMnemonic, String containerMnemonic) {
        Result result = containerRepository.checkIfContainerExists(softwareSystemMnemonic, containerMnemonic);
        return result.hasNext();
    }

    public Boolean checkIfEnvironmentExists(String environment) {
        Result result = environmentRepository.checkIfEnvironmentExists(environment);
        return result.hasNext();
    }

    public ResponseEntity<String> createDiagram(String softwareSystemMnemonic,
                                                String containerMnemonic,
                                                String environment,
                                                String rankDirection) {
        if (rankDirection == null) {
            rankDirection = "LeftRight";
        }
        if (!rankDirection.equals("TopBottom") && !rankDirection.equals("LeftRight")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Недопустимая ориентация диаграммы");
        }
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        GraphObject systemGraphObject = new GraphObject("SoftwareSystem",
                                                        "structurizr_dsl_identifier",
                                                        softwareSystemMnemonic);
        boolean exists = genericRepository.checkIfObjectExists("Global", systemGraphObject);
        if (!exists) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Система не найдена");
        }
        Workspace workspace;
        try {
            if (containerMnemonic == null && environment == null) {
                workspace = viewService.GetContextView(softwareSystemMnemonic, rankDirection);
            } else if (containerMnemonic != null) {
                if (!checkifContainerExists(softwareSystemMnemonic, containerMnemonic)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Контейнер не найден");
                }
                workspace = viewService.GetComponentView(softwareSystemMnemonic, containerMnemonic, rankDirection);
            } else {
                if (!checkIfEnvironmentExists(environment)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Окружение не найдено");
                }
                workspace = viewService.GetDeploymentView(softwareSystemMnemonic, environment, rankDirection);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(workspace);
            json = structurizrClient.changeJson(json);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Ошибка при сериализации\n" + e.getMessage());
        }
    }

    public ResponseEntity<String> createContextDiagramV2(String cmdb,
                                                         String rankDirection,
                                                         String communicationDirection) {
        if (rankDirection == null) {
            rankDirection = "LeftRight";
        }
        if (!rankDirection.equals("TopBottom") && !rankDirection.equals("LeftRight")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Недопустимая ориентация диаграммы");
        }
        if (!communicationDirection.equals("in") && !communicationDirection.equals("out")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Недопустимая ориентация диаграммы");
        }
        Result ssResult = softwareSystemRepository.getSoftwareSystem(cmdb);

        if (!ssResult.hasNext()) {
            throw new RuntimeException("SoftwareSystem не найден");
        }

        Node softwareSystemNode = ssResult.next().get("softwareSystem").asNode();
        Long softwareSystemId = softwareSystemNode.id();
        String softwareSystemName = softwareSystemNode.get("cmdb").asString();

        Set<Long> allNodeIds = softwareSystemRepository.getContainerAndComponentChildIds(cmdb);
        allNodeIds.add(softwareSystemId);

        List<Map<String, Object>> relationships = "in".equalsIgnoreCase(communicationDirection) ? relationshipRepository.getIncomingRelationships(
                allNodeIds,
                softwareSystemId) : relationshipRepository.getOutgoingRelationships(allNodeIds, softwareSystemId);
        Set<Long> relIds = relationships.stream()
                .map(rel -> Long.parseLong((String) rel.get("in".equalsIgnoreCase(communicationDirection) ? "sourceId" : "destinationId")))
                .collect(Collectors.toSet());

        Set<Map<String, Object>> softwareSystems = softwareSystemRepository.getSoftwareSystemsFromRelationships(new ArrayList<>(
                relIds));

        Map<Long, Map<String, String>> containerToParentSS = softwareSystemRepository.findParentSoftwareSystemsByContainers(
                relIds);
        Map<Long, Map<String, String>> componentToParentSS = softwareSystemRepository.getParentSoftwareSystemsForComponents(
                relIds);

        Set<String> existingNames = softwareSystems.stream()
                .map(ss -> ss.get("name").toString())
                .collect(Collectors.toSet());

        for (Map<String, Object> rel : relationships) {
            Long relId = Long.parseLong((String) rel.get("in".equalsIgnoreCase(communicationDirection) ? "sourceId" : "destinationId"));

            Map<String, String> parentSS = null;
            if (containerToParentSS.containsKey(relId)) {
                parentSS = containerToParentSS.get(relId);
            }
            if (componentToParentSS.containsKey(relId)) {
                parentSS = componentToParentSS.get(relId);
            }
            if (parentSS != null) {
                rel.put("in".equalsIgnoreCase(communicationDirection) ? "sourceId" : "destinationId",
                        parentSS.get("id"));
                if (!existingNames.contains(parentSS.get("name"))) {
                    softwareSystems.add(Map.of("id", parentSS.get("id").toString(), "name", parentSS.get("name")));
                    existingNames.add(parentSS.get("name"));
                }
            }
        }
        relationships = relationships.stream()
                .filter(rel -> !rel.get("sourceId").equals(rel.get("destinationId")))
                .collect(Collectors.toList());
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(createDiagram(rankDirection,
                                                                  softwareSystemId,
                                                                  softwareSystemName,
                                                                  relationships,
                                                                  softwareSystems));
            json = structurizrClient.changeJson(json);
            return ResponseEntity.ok(json);
        } catch (Exception e) {
            log.error(e.getMessage(), e.getStackTrace());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка сериализации JSON" + e.getMessage());
        }
    }

    private static Map<String, Object> createDiagram(String rankDirection,
                                                     Long softwareSystemId,
                                                     String softwareSystemName,
                                                     List<Map<String, Object>> relationships,
                                                     Set<Map<String, Object>> softwareSystems) {
        Map<String, Object> diagram = new HashMap<>();

        Map<String, Object> model = new HashMap<>();
        Map<String, Object> ssMap = new HashMap<>();
        ssMap.put("id", softwareSystemId.toString());
        ssMap.put("name", softwareSystemName);
        ssMap.put("relationships", relationships);
        softwareSystems.add(ssMap);
        model.put("softwareSystems", softwareSystems);

        Map<String, Object> automaticLayout = new HashMap<>();
        automaticLayout.put("applied", false);
        automaticLayout.put("edgeSeparation", 0);
        automaticLayout.put("implementation", "Graphviz");
        automaticLayout.put("nodeSeparation", 300);
        automaticLayout.put("rankDirection", rankDirection);
        automaticLayout.put("rankSeparation", 300);
        automaticLayout.put("vertices", false);

        Map<String, Object> systemContextView = new HashMap<>();
        systemContextView.put("automaticLayout", automaticLayout);
        systemContextView.put("elements", softwareSystems);
        systemContextView.put("key", "context");
        systemContextView.put("relationships", relationships);
        systemContextView.put("softwareSystemId", softwareSystemId.toString());

        Map<String, Object> views = new HashMap<>();
        List<Map<String, Object>> systemContextViews = new ArrayList<>();
        systemContextViews.add(systemContextView);
        views.put("systemContextViews", systemContextViews);

        diagram.put("model", model);
        diagram.put("views", views);
        return diagram;
    }

    public ResponseEntity<String> getDiagramDeployment(String cmdb,
                                                       String env,
                                                       String rankDirection,
                                                       String deploymentName) {
        if (rankDirection == null) {
            rankDirection = "LeftRight";
        }
        if (!rankDirection.equals("TopBottom") && !rankDirection.equals("LeftRight")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Недопустимая ориентация диаграммы");
        }
        Result deploymentNodeAndSoftwareSystem = deploymentNodesRepository.findDeploymentNodeByNameEnvCmdb(
                deploymentName,
                env,
                cmdb);
        if (!deploymentNodeAndSoftwareSystem.hasNext()) {
            throw new RuntimeException("SoftwareSystem не найден");
        }
        var record = deploymentNodeAndSoftwareSystem.next();
        Node dnNode = record.get("dn").asNode();
        Map<String, Object> mainSoftwareSystem = getSoftwareSystem(record);
        Map<String, Map<String, Object>> ssMap = new HashMap<>();
        Map<String, Map<String, Object>> dnMap = new HashMap<>();
        Map<String, Map<String, Object>> ciMap = new HashMap<>();
        Map<String, Map<String, Object>> inMap = new HashMap<>();
        Map<String, Map<String, Object>> cMap = new HashMap<>();
        Map<String, Map<String, Object>> relMap = new HashMap<>();
        ssMap.put(mainSoftwareSystem.get("id").toString(), mainSoftwareSystem);

        Map<String, Object> deploymentNode = recursiveConstructDnCiIn(dnNode, ssMap, dnMap, inMap, ciMap, cMap);
        List<Map<String, Object>> deploymentNodes = Arrays.asList(deploymentNode);

        dnMap.values().forEach(dn -> {
            processDnToDn(dn, dnMap, deploymentNodes, relMap);
            processInToDn(dn, dnMap, inMap, deploymentNodes, relMap);
        });

        inMap.values().forEach(in -> {
            processDntoIn(in, dnMap, deploymentNodes, inMap, relMap);
            processIntoIn(in, dnMap, deploymentNodes, inMap, relMap);
        });
        Map<String, Map<String, Object>> containersInstances = new HashMap<>();

        ciMap.values().forEach(containerInstance -> {
            processCI(containerInstance, ciMap, relMap, ssMap, cMap, dnMap, containersInstances);
        });

        ciMap.putAll(containersInstances);

        List<Map<String, String>> elements = new ArrayList<>();
        elements.addAll(dnMap.keySet()
                                .stream()
                                .map(id -> Map.of("id", String.valueOf(id)))
                                .collect(Collectors.toList()));
        elements.addAll(ciMap.keySet()
                                .stream()
                                .map(id -> Map.of("id", String.valueOf(id)))
                                .collect(Collectors.toList()));
        elements.addAll(inMap.keySet()
                                .stream()
                                .map(id -> Map.of("id", String.valueOf(id)))
                                .collect(Collectors.toList()));
        elements.addAll(ssMap.keySet()
                                .stream()
                                .map(id -> Map.of("id", String.valueOf(id)))
                                .collect(Collectors.toList()));

        List<Map<String, String>> relations = relMap.keySet()
                .stream()
                .map(id -> Map.of("id", String.valueOf(id)))
                .collect(Collectors.toList());

        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(createDiagram(rankDirection,
                                                                  deploymentNodes,
                                                                  ssMap,
                                                                  elements,
                                                                  relations));
            json = structurizrClient.changeJson(json);
            return ResponseEntity.ok(json);
        } catch (Exception e) {
            log.error(e.getMessage(), e.getStackTrace());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка сериализации JSON" + e.getMessage());
        }
    }

    private void processCI(Map<String, Object> containerInstance,
                           Map<String, Map<String, Object>> ciMap,
                           Map<String, Map<String, Object>> relMap,
                           Map<String, Map<String, Object>> ssMap,
                           Map<String, Map<String, Object>> cMap,
                           Map<String, Map<String, Object>> dnMap,
                           Map<String, Map<String, Object>> containersInstances) {
        Result resultSet = genericRepository.findIncomingRelationshipsByContainerInstance(Long.parseLong(String.valueOf(
                containerInstance.get("id"))));
        while (resultSet.hasNext()) {
            var rec = resultSet.next();
            var rel = rec.get("r").asRelationship();
            var src = rec.get("src").asNode();
            var dst = rec.get("dst").asNode();
            var container = rec.get("container").asNode();
            var ss = rec.get("ss").asNode();
            var dn = rec.get("dn").asNode();

            Map<String, Object> relation = new HashMap<>();
            relation.put("id", String.valueOf(rel.id()));
            relation.put("sourceId", String.valueOf(src.id()));
            relation.put("destinationId", String.valueOf(dst.id()));
            relation.put("description", rel.get("description").asString(null) + String.valueOf(rel.id()));
            relation.put("technology", rel.get("technology").asString(null));
            ((HashSet) ciMap.get(String.valueOf(dst.id())).get("relationships")).add(relation);
            relMap.put(String.valueOf(rel.id()), relation);

            if (ssMap.containsKey(String.valueOf(ss.id()))) {
                if (!cMap.containsKey(String.valueOf(src.id()))) {
                    Map<String, Object> newContainer = new HashMap<>();
                    newContainer.put("id", String.valueOf(container.id()));
                    newContainer.put("name", container.get("name").asString());
                    newContainer.put("relationships", new HashSet<>());
                    cMap.put(String.valueOf(src.id()), newContainer);
                    ((HashSet) ssMap.get(String.valueOf(ss.id())).get("containers")).add(newContainer);
                }
            } else {
                Map<String, Object> newContainer = new HashMap<>();
                newContainer.put("id", String.valueOf(container.id()));
                newContainer.put("name", container.get("name").asString());
                newContainer.put("relationships", new HashSet<>());
                cMap.put(String.valueOf(src.id()), newContainer);

                Map<String, Object> newSoftwareSystem = new HashMap<>();
                newSoftwareSystem.put("id", String.valueOf(ss.id()));
                newSoftwareSystem.put("name", ss.get("cmdb").asString());
                newSoftwareSystem.put("containers", newContainer);
                ssMap.put(String.valueOf(src.id()), newSoftwareSystem);
            }
            if (dnMap.containsKey(String.valueOf(dn.id()))) {
                if (!dnMap.get(String.valueOf(dn.id()))
                        .get("containerInstances")
                        .toString()
                        .contains(String.valueOf(src.id()))) {
                    Map<String, Object> ci = new HashMap<>();
                    ci.put("id", String.valueOf(src.id()));
                    ci.put("environment", "BY BEEATLAS");
                    ci.put("containerId", String.valueOf(container.id()));
                    containersInstances.put(String.valueOf(src.id()), ci);
                    ((HashSet) dnMap.get(String.valueOf(dn.id())).get("containerInstances")).add(ci);
                }
            } else {
                Map<String, Object> ci = new HashMap<>();
                ci.put("id", String.valueOf(src.id()));
                ci.put("environment", "BY BEEATLAS");
                ci.put("containerId", String.valueOf(src.id()));
                containersInstances.put(String.valueOf(src.id()), ci);
                HashSet hashSet = new HashSet();
                hashSet.add(ci);
                Map<String, Object> newDeploymentNode = new HashMap<>();
                newDeploymentNode.put("id", String.valueOf(dn.id()));
                newDeploymentNode.put("name", dn.get("name").asString());
                newDeploymentNode.put("containerInstances", hashSet);
                newDeploymentNode.put("infrastructureNodes", new ArrayList<>());
                newDeploymentNode.put("environment", "BY BEEATLAS");
                newDeploymentNode.put("children", new ArrayList<>());
                newDeploymentNode.put("relationships", new HashSet<>());
                dnMap.put(String.valueOf(dn.id()), newDeploymentNode);
            }
        }
    }

    private void processIntoIn(Map<String, Object> in,
                               Map<String, Map<String, Object>> dnMap,
                               List<Map<String, Object>> deploymentNodes,
                               Map<String, Map<String, Object>> inMap,
                               Map<String, Map<String, Object>> relMap) {
        Result resultIn = genericRepository.findIncomingDeploymentNodeRelationshipsFromIncomingDeploymentNode(Long.parseLong(
                String.valueOf(in.get("id"))));
        while (resultIn.hasNext()) {
            var rec = resultIn.next();
            var rel = rec.get("r").asRelationship();
            var src = rec.get("src").asNode();
            var dst = rec.get("dst").asNode();

            Map<String, Object> relation = new HashMap<>();
            relation.put("id", String.valueOf(rel.id()));
            relation.put("sourceId", String.valueOf(src.id()));
            relation.put("destinationId", String.valueOf(dst.id()));
            relation.put("description", rel.get("description").asString(null) + String.valueOf(rel.id()));
            relation.put("technology", rel.get("technology").asString(null));
            ((HashSet) inMap.get(String.valueOf(dst.id())).get("relationships")).add(relation);
            relMap.put(String.valueOf(rel.id()), relation);

            if (!inMap.containsKey(String.valueOf(src.id()))) {
                Map<String, Object> InfrastructureNode = new HashMap<>();
                InfrastructureNode.put("id", String.valueOf(src.id()));
                InfrastructureNode.put("name", src.get("name").asString());
                InfrastructureNode.put("environment", "BY BEEATLAS");
                Result dnToIn = genericRepository.findDeploymentNodesByInfrastructureId(Long.parseLong(String.valueOf(
                        src.id())));
                while (dnToIn.hasNext()) {
                    var dnToInRec = dnToIn.next();
                    var subDn = dnToInRec.get("dn").asNode();
                    if (dnMap.containsKey(String.valueOf(subDn.id()))) {
                        ((ArrayList) dnMap.get(String.valueOf(subDn.id())).get("infrastructureNodes")).add(
                                InfrastructureNode);
                    } else {
                        Map<String, Object> newDeploymentNode = new HashMap<>();
                        newDeploymentNode.put("id", String.valueOf(src.id()));
                        newDeploymentNode.put("name", src.get("name").asString());
                        newDeploymentNode.put("environment", "BY BEEATLAS");
                        deploymentNodes.add(newDeploymentNode);
                    }
                }
            }
        }
    }

    private void processDntoIn(Map<String, Object> in,
                               Map<String, Map<String, Object>> dnMap,
                               List<Map<String, Object>> deploymentNodes,
                               Map<String, Map<String, Object>> inMap,
                               Map<String, Map<String, Object>> relMap) {
        Result resultDn = genericRepository.findRelationshipsFromDeploymentNode(Long.parseLong(String.valueOf(in.get(
                "id"))));
        while (resultDn.hasNext()) {
            var rec = resultDn.next();
            var rel = rec.get("r").asRelationship();
            var src = rec.get("src").asNode();
            var dst = rec.get("dst").asNode();

            Map<String, Object> relation = new HashMap<>();
            relation.put("id", String.valueOf(rel.id()));
            relation.put("sourceId", String.valueOf(src.id()));
            relation.put("destinationId", String.valueOf(dst.id()));
            relation.put("description", rel.get("description").asString(null) + String.valueOf(rel.id()));
            relation.put("technology", rel.get("technology").asString(null));

            if (!dnMap.containsKey(String.valueOf(src.id()))) {
                Map<String, Object> newDeploymentNode = new HashMap<>();
                newDeploymentNode.put("id", String.valueOf(src.id()));
                newDeploymentNode.put("name", src.get("name").asString());
                newDeploymentNode.put("environment", "BY BEEATLAS");
                deploymentNodes.add(newDeploymentNode);
            }
            ((HashSet) inMap.get(String.valueOf(src.id())).get("relationships")).add(relation);
            relMap.put(String.valueOf(rel.id()), relation);

        }
    }

    private void processInToDn(Map<String, Object> dn,
                               Map<String, Map<String, Object>> dnMap,
                               Map<String, Map<String, Object>> inMap,
                               List<Map<String, Object>> deploymentNodes,
                               Map<String, Map<String, Object>> relMap) {
        Result resultIn = genericRepository.findIncomingDeploymentNodeRelationshipsFromInfrastructureNode(Long.parseLong(
                String.valueOf(dn.get("id"))));
        while (resultIn.hasNext()) {
            var rec = resultIn.next();
            var rel = rec.get("r").asRelationship();
            var src = rec.get("src").asNode();
            var dst = rec.get("dst").asNode();

            Map<String, Object> relation = new HashMap<>();
            relation.put("id", String.valueOf(rel.id()));
            relation.put("sourceId", String.valueOf(src.id()));
            relation.put("destinationId", String.valueOf(dst.id()));
            relation.put("description", rel.get("description").asString(null) + String.valueOf(rel.id()));
            relation.put("technology", rel.get("technology").asString(null));
            ((HashSet) dnMap.get(String.valueOf(dst.id())).get("relationships")).add(relation);
            relMap.put(String.valueOf(rel.id()), relation);

            if (!inMap.containsKey(String.valueOf(src.id()))) {
                Map<String, Object> InfrastructureNode = new HashMap<>();
                InfrastructureNode.put("id", String.valueOf(src.id()));
                InfrastructureNode.put("name", src.get("name").asString());
                InfrastructureNode.put("environment", "BY BEEATLAS");
                Result dnToIn = genericRepository.findDeploymentNodesByInfrastructureId(Long.parseLong(String.valueOf(
                        src.id())));
                while (dnToIn.hasNext()) {
                    var dnToInRec = dnToIn.next();
                    var subDn = dnToInRec.get("dn").asNode();
                    if (dnMap.containsKey(String.valueOf(subDn.id()))) {
                        ((ArrayList) dnMap.get(String.valueOf(subDn.id())).get("infrastructureNodes")).add(
                                InfrastructureNode);
                    } else {
                        Map<String, Object> newDeploymentNode = new HashMap<>();
                        newDeploymentNode.put("id", String.valueOf(src.id()));
                        newDeploymentNode.put("name", src.get("name").asString());
                        newDeploymentNode.put("environment", "BY BEEATLAS");
                        deploymentNodes.add(newDeploymentNode);
                    }
                }
            }
        }
    }

    private void processDnToDn(Map<String, Object> dn,
                               Map<String, Map<String, Object>> dnMap,
                               List<Map<String, Object>> deploymentNodes,
                               Map<String, Map<String, Object>> relMap) {
        Result resultDn = genericRepository.findIncomingDeploymentNodeRelationships(Long.parseLong(String.valueOf(dn.get(
                "id"))));
        while (resultDn.hasNext()) {
            var rec = resultDn.next();
            var rel = rec.get("r").asRelationship();
            var src = rec.get("src").asNode();
            var dst = rec.get("dst").asNode();

            Map<String, Object> relation = new HashMap<>();
            relation.put("id", String.valueOf(rel.id()));
            relation.put("sourceId", String.valueOf(src.id()));
            relation.put("destinationId", String.valueOf(dst.id()));
            relation.put("description", rel.get("description").asString(null) + String.valueOf(rel.id()));
            relation.put("technology", rel.get("technology").asString(null));

            if (!dnMap.containsKey(src.get("id"))) {
                Map<String, Object> newDeploymentNode = new HashMap<>();
                newDeploymentNode.put("id", String.valueOf(src.id()));
                newDeploymentNode.put("name", src.get("name").asString());
                newDeploymentNode.put("environment", "BY BEEATLAS");
                deploymentNodes.add(newDeploymentNode);
            }
            ((HashSet) dnMap.get(dst.get("id")).get("relationships")).add(relation);
            relMap.put(String.valueOf(rel.id()), relation);

        }
    }

    public Map<String, Object> recursiveConstructDnCiIn(Node dnNode,
                                                        Map<String, Map<String, Object>> ssMap,
                                                        Map<String, Map<String, Object>> dnMap,
                                                        Map<String, Map<String, Object>> inMap,
                                                        Map<String, Map<String, Object>> ciMap,
                                                        Map<String, Map<String, Object>> cMap) {
        String dnName = dnNode.get("name").asString();
        String dnId = String.valueOf(dnNode.id());
        Map<String, Object> deploymentNode = new HashMap<>();
        deploymentNode.put("id", dnId);
        deploymentNode.put("name", dnName);
        deploymentNode.put("environment", "BY BEEATLAS");

        Result containerInstancesWithContainers = genericRepository.getContainerInstancesWithContainersAndSoftwareSystems(
                dnNode.id());
        Set<Map<String, Object>> containerInstances = new HashSet<>();
        List<Map<String, Object>> containers = new ArrayList<>();
        List<Map<String, Object>> softwareSystems = new ArrayList<>();
        while (containerInstancesWithContainers.hasNext()) {
            var containerInstancesWithContainersRecord = containerInstancesWithContainers.next();
            var ciNode = containerInstancesWithContainersRecord.get("ci").asNode();
            var ssNode = containerInstancesWithContainersRecord.get("ss").asNode();
            var cNode = containerInstancesWithContainersRecord.get("c").asNode();

            Map<String, Object> containerInstance = new HashMap();
            containerInstance.put("id", String.valueOf(ciNode.id()));
            containerInstance.put("environment", "BY BEEATLAS");
            containerInstance.put("containerId", String.valueOf(cNode != null ? cNode.id() : ciNode.id()));
            containerInstance.put("relationships", new HashSet<>());
            containerInstances.add(containerInstance);
            ciMap.put(String.valueOf(ciNode.id()), containerInstance);

            Map<String, Object> container = new HashMap<>();
            container.put("id", String.valueOf(cNode.id()));
            container.put("name", cNode.get("name").asString());
            container.put("relationships", new HashSet<>());
            containers.add(container);
            cMap.put(String.valueOf(cNode.id()), container);

            if (ssMap.containsKey(String.valueOf(ssNode.id()))) {
                ((HashSet) ssMap.get(String.valueOf(ssNode.id())).get("containers")).add(container);
            } else {
                Map<String, Object> softwareSystem = new HashMap<>();
                softwareSystem.put("id", String.valueOf(ssNode.id()));
                softwareSystem.put("name", ssNode.get("cmdb").asString());
                softwareSystem.put("containers", new ArrayList<>(Arrays.asList(container)));
                softwareSystems.add(softwareSystem);
            }

        }
        deploymentNode.put("containerInstances", containerInstances);
        deploymentNode.put("infrastructureNodes",
                           mapInfrastructureNodes(infrastructureNodesRepository.getInfrastructureNodesByDeploymentNodeId(
                                   dnNode.id()), inMap));

        List<Map<String, Object>> children = new ArrayList<>();
        Result kids = deploymentNodesRepository.getChildDeploymentNodesById(dnNode.id());
        while (kids.hasNext()) {
            var childNode = kids.next().get("child").asNode();
            Map<String, Object> child = recursiveConstructDnCiIn(childNode, ssMap, dnMap, inMap, ciMap, cMap);
            children.add(child);
            dnMap.put(child.get("id").toString(), child);
        }
        deploymentNode.put("children", children);

        return deploymentNode;
    }

    public List<Map<String, Object>> mapInfrastructureNodes(Result result, Map<String, Map<String, Object>> inMap) {
        List<Map<String, Object>> infrastructureNodes = new ArrayList<>();

        while (result.hasNext()) {
            var record = result.next();
            var infNode = record.get("inf").asNode();

            Map<String, Object> infrastructure = new HashMap<>();
            infrastructure.put("id", String.valueOf(infNode.id()));
            infrastructure.put("name", infNode.get("name").asString());
            infrastructure.put("environment", "BY BEEATLAS");
            infrastructure.put("relationships", new HashSet<>());

            infrastructureNodes.add(infrastructure);
            inMap.put(String.valueOf(infNode.id()), infrastructure);
        }
        return infrastructureNodes;
    }

    private static Map<String, Object> getSoftwareSystem(org.neo4j.driver.Record record) {
        var ssNode = record.get("ss").asNode();

        Map<String, Object> softwareSystemObj = new HashMap<>();
        softwareSystemObj.put("id", String.valueOf(ssNode.id()));
        softwareSystemObj.put("name", ssNode.get("cmdb").asString());
        softwareSystemObj.put("containers", new HashSet<>());
        return softwareSystemObj;
    }


    private static Map<String, Object> createDiagram(String rankDirection,
                                                     List<Map<String, Object>> deploymentNodes,
                                                     Map<String, Map<String, Object>> softwareSystem,
                                                     List<Map<String, String>> elements,
                                                     List<Map<String, String>> relations) {
        Map<String, Object> diagram = new HashMap<>();
        Map<String, Object> model = new HashMap<>();
        model.put("deploymentNodes", deploymentNodes);
        model.put("softwareSystems", new ArrayList<>(softwareSystem.values()));

        Map<String, Object> automaticLayout = new HashMap<>();
        automaticLayout.put("applied", false);
        automaticLayout.put("edgeSeparation", 0);
        automaticLayout.put("implementation", "Graphviz");
        automaticLayout.put("nodeSeparation", 300);
        automaticLayout.put("rankDirection", rankDirection);
        automaticLayout.put("rankSeparation", 300);
        automaticLayout.put("vertices", false);

        Map<String, Object> systemContextView = new HashMap<>();
        systemContextView.put("automaticLayout", automaticLayout);
        systemContextView.put("elements", elements);
        systemContextView.put("environment", "BY-BEEATLAS");
        systemContextView.put("key", "BY-BEEATLAS-01");
        systemContextView.put("paperSize", "A6_Portrait");
        systemContextView.put("relationships", relations);

        Map<String, Object> views = new HashMap<>();
        List<Map<String, Object>> systemContextViews = new ArrayList<>();
        systemContextViews.add(systemContextView);
        views.put("deploymentViews", systemContextViews);

        diagram.put("model", model);
        diagram.put("views", views);

        return diagram;
    }

    public ResponseEntity<String> getDiagramDeploymentDot(Long nodeId) {
        var record = genericRepository.getNodeTypeAndNameById(nodeId);
        if (!record.hasNext())
            throw new ValidationException("Node with id " + nodeId + " does not exist");

        org.neo4j.driver.Record rec = record.next();
        String nodeType = rec.get("nodeType").asString("");
        String rootName = rec.get("name").asString("Unnamed");
        String label = rootName.contains(".") ? rootName.substring(0, rootName.indexOf('.')) : rootName;

        if (nodeType.isEmpty())
            throw new ValidationException("Node with id " + nodeId + " does not exist");

        Graph graph = new SingleGraph("Neo4j Graph");
        graph.setAttribute("rankdir", "RL");
        org.graphstream.graph.Node centralNode = graph.addNode("central");
        centralNode.setAttribute("shape", "rect");
        centralNode.setAttribute("style", "filled");
        centralNode.setAttribute("label", label);
        centralNode.setAttribute("color", "green");
        centralNode.setAttribute("fillcolor", "lightgreen");

        switch (nodeType) {
            case "DeploymentNode":
                var depResults = genericRepository.getDeploymentNodeDependencies(nodeId);
                if (depResults.hasNext()) {
                    var depRecord = depResults.next();
                    addNodesFromCollection(graph,
                                           depRecord,
                                           "deploymentSources",
                                           "yellow",
                                           "orange",
                                           "Вызов",
                                           "central");
                    addNodesFromCollection(graph,
                                           depRecord,
                                           "infrastructureSources",
                                           "yellow",
                                           "orange",
                                           "Вызов",
                                           "central");
                    addNodesFromCollection(graph,
                                           depRecord,
                                           "deploymentTargets",
                                           "blue",
                                           "lightblue",
                                           "Deploy",
                                           "central");
                    addNodesFromCollection(graph,
                                           depRecord,
                                           "infrastructureNodes",
                                           "blue",
                                           "lightblue",
                                           "Deploy",
                                           "central");
                    addNodesFromCollection(graph, depRecord, "containers", "blue", "lightblue", "Deploy", "central");
                }
                break;

            case "Container":
                var containerResults = genericRepository.getContainerDependencies(nodeId);
                if (containerResults.hasNext()) {
                    var containerRecord = containerResults.next();
                    addNodesFromCollection(graph,
                                           containerRecord,
                                           "containersSources",
                                           "yellow",
                                           "orange",
                                           "Вызов",
                                           "central");
                }
                break;

            case "InfrastructureNode":
                var infraResults = genericRepository.getInfrastructureNodeDependencies(nodeId);
                if (infraResults.hasNext()) {
                    var infraRecord = infraResults.next();
                    addNodesFromCollection(graph,
                                           infraRecord,
                                           "infrastructureSources",
                                           "yellow",
                                           "orange",
                                           "Вызов",
                                           "central");
                    addNodesFromCollection(graph,
                                           infraRecord,
                                           "deploymentSources",
                                           "yellow",
                                           "orange",
                                           "Вызов",
                                           "central");
                }
                break;

            default:
                throw new ValidationException("Unsupported node type: " + nodeType);
        }

        return ResponseEntity.ok(convertToDotFormat(graph));
    }

    private void addNodesFromCollection(Graph graph,
                                        org.neo4j.driver.Record record,
                                        String key,
                                        String color,
                                        String fillColor,
                                        String edgeLabel,
                                        String centralNodeId) {
        List<Object> nodeObjects = record.get(key).asList();
        if (nodeObjects.isEmpty())
            return;

        for (Object obj : nodeObjects) {
            if (!(obj instanceof org.neo4j.driver.types.Node))
                continue;

            org.neo4j.driver.types.Node node = (org.neo4j.driver.types.Node) obj;
            String nodeId = key + "_" + node.id();
            org.graphstream.graph.Node graphNode;
            if (graph.getNode(nodeId) == null) {
                graphNode = graph.addNode(nodeId);
            } else {
                graphNode = graph.getNode(nodeId);
            }
            String label = node.get("name") != null ? node.get("name").asString() : "Unnamed";
            label = label.contains(".") ? label.substring(0, label.indexOf('.')) : label;
            graphNode.setAttribute("shape", "rect");
            graphNode.setAttribute("style", "filled");

            graphNode.setAttribute("label", label);
            graphNode.setAttribute("color", color);
            graphNode.setAttribute("fillcolor", fillColor);

            String edgeId = nodeId + "_" + centralNodeId;
            if (graph.getEdge(edgeId) == null) {
                graph.addEdge(edgeId, nodeId, centralNodeId, true).setAttribute("label", edgeLabel);
            }
        }
    }

    private String convertToDotFormat(Graph graph) {
        try {
            FileSinkDOT sink = new FileSinkDOT();
            sink.setDirected(true);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            sink.writeAll(graph, outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error converting graph to DOT format", e);
        }
    }

    public ResponseEntity<List<DiagramElementDTO>> getDiagramDeploymentElements(Long nodeId) {
        var record = genericRepository.getNodeTypeAndNameById(nodeId);
        if (!record.hasNext()) {
            return ResponseEntity.badRequest().build();
        }
        var rec = record.next();
        String nodeType = rec.get("nodeType").asString("");
        if (nodeType.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<org.neo4j.driver.Record> dependencyRecords = new ArrayList<>();

        switch (nodeType) {
            case "DeploymentNode":
                var depResults = genericRepository.getDeploymentNodeDependencies(nodeId);
                if (depResults.hasNext())
                    dependencyRecords.add(depResults.next());
                break;

            case "Container":
                var containerResults = genericRepository.getContainerDependencies(nodeId);
                if (containerResults.hasNext())
                    dependencyRecords.add(containerResults.next());
                break;

            case "InfrastructureNode":
                var infraResults = genericRepository.getInfrastructureNodeDependencies(nodeId);
                if (infraResults.hasNext())
                    dependencyRecords.add(infraResults.next());
                break;

            default:
                return ResponseEntity.badRequest().build();
        }

        List<DiagramElementDTO> dependentElements = new ArrayList<>();

        if (dependencyRecords.isEmpty()) {
            return ResponseEntity.ok(dependentElements);
        }

        var recordDeps = dependencyRecords.get(0);

        Map<String, List<org.neo4j.driver.types.Node>> nodesMap = new HashMap<>();

        switch (nodeType) {
            case "DeploymentNode":
                putIfNotEmpty(nodesMap, "deploymentSources", recordDeps);
                putIfNotEmpty(nodesMap, "infrastructureSources", recordDeps);
                putIfNotEmpty(nodesMap, "deploymentTargets", recordDeps);
                putIfNotEmpty(nodesMap, "infrastructureNodes", recordDeps);
                putIfNotEmpty(nodesMap, "containers", recordDeps);
                break;

            case "Container":
                putIfNotEmpty(nodesMap, "containersSources", recordDeps);
                break;

            case "InfrastructureNode":
                putIfNotEmpty(nodesMap, "infrastructureSources", recordDeps);
                putIfNotEmpty(nodesMap, "deploymentSources", recordDeps);
                break;
        }
        List<ProductInfoShortDTO> products = productClient.getAllProductsInfo();
        for (List<org.neo4j.driver.types.Node> nodeList : nodesMap.values()) {
            for (org.neo4j.driver.types.Node node : nodeList) {
                Long id = node.id();
                String fullName = node.get("name") != null ? node.get("name").asString() : "Unnamed";
                String cmdb = trimAfterLastDot(fullName);

                ProductInfoShortDTO productInfo = products.stream()
                        .filter(product -> product.getAlias().equalsIgnoreCase(cmdb))
                        .findFirst()
                        .orElse(null);

                dependentElements.add(DiagramElementDTO.builder()
                                              .id(id)
                                              .name(trimAfterFirstDot(fullName))
                                              .dependentCount(genericRepository.getDependentCountByNodeId(id))
                                              .cmdb(cmdb)
                                              .critical(productInfo != null ? productInfo.getCritical() : "")
                                              .ownerName(productInfo != null ? productInfo.getOwnerName() : "")
                                              .build());
            }
        }

        return ResponseEntity.ok(dependentElements);
    }

    private void putIfNotEmpty(Map<String, List<org.neo4j.driver.types.Node>> map,
                               String key,
                               org.neo4j.driver.Record record) {
        if (record == null || !record.containsKey(key))
            return;

        List<org.neo4j.driver.types.Node> nodes = new ArrayList<>();
        var list = record.get(key).asList();

        for (Object obj : list) {
            if (obj instanceof org.neo4j.driver.types.Node) {
                nodes.add((org.neo4j.driver.types.Node) obj);
            }
        }

        if (!nodes.isEmpty()) {
            map.put(key, nodes);
        }
    }

    private String trimAfterLastDot(String s) {
        int index = s.lastIndexOf('~');
        return index < 0 ? s : s.substring(index + 1);
    }

    public ResponseEntity<String> getContextDiagramDot(String cmdb) {
        Set<String> cmdbList = new HashSet<>();
        addCmdbFromResult(cmdbList, genericRepository.getDependentSystemsRelationship(cmdb));
        addCmdbFromResult(cmdbList, genericRepository.getDependentSystemsChildContainerRelationship(cmdb));
        addCmdbFromResult(cmdbList, genericRepository.getDependentSystemsChildContainerChildRelationship(cmdb));

        Graph graph = new SingleGraph("Neo4j Graph");
        graph.setAttribute("rankdir", "RL");

        org.graphstream.graph.Node centralNode = graph.addNode("central");
        centralNode.setAttribute("label", cmdb);
        centralNode.setAttribute("shape", "rect");
        centralNode.setAttribute("style", "filled");
        centralNode.setAttribute("color", "green");
        centralNode.setAttribute("fillcolor", "lightgreen");

        for (String depCmdb : cmdbList) {
            org.graphstream.graph.Node node;
            if (graph.getNode(depCmdb) == null) {
                node = graph.addNode(depCmdb);
            } else {
                node = graph.getNode(depCmdb);
            }
            node.setAttribute("label", depCmdb);
            node.setAttribute("shape", "rect");
            node.setAttribute("style", "filled");
            node.setAttribute("color", "red");
            node.setAttribute("fillcolor", "orange");

            String edgeId = depCmdb + "_central";
            if (graph.getEdge(edgeId) == null) {
                graph.addEdge(edgeId, depCmdb, "central", true).setAttribute("label", "Вызов");
            }
        }

        String dotFormat = convertToDotFormat(graph);
        return ResponseEntity.ok(dotFormat);
    }

    private void addCmdbFromResult(Set<String> set, Result result) {
        while (result.hasNext()) {
            var record = result.next();
            var node = record.get("dependentSystem").asNode();
            if (node.containsKey("cmdb")) {
                String cmdbValue = node.get("cmdb").asString().toLowerCase();
                set.add(cmdbValue);
            }
        }
    }

    public ResponseEntity<List<ContextElementDTO>> getContextElements(String cmdb) {
        Set<String> dependentCmdbSet = new HashSet<>();

        addCmdbFromResult(dependentCmdbSet, genericRepository.getDependentSystemsRelationship(cmdb));
        addCmdbFromResult(dependentCmdbSet, genericRepository.getDependentSystemsChildContainerRelationship(cmdb));
        addCmdbFromResult(dependentCmdbSet, genericRepository.getDependentSystemsChildContainerChildRelationship(cmdb));

        return ResponseEntity.ok(productClient.getAllProductsInfo()
                                         .stream()
                                         .filter(product -> dependentCmdbSet.contains(product.getAlias().toLowerCase()))
                                         .map(product -> ContextElementDTO.builder()
                                                 .id(Long.parseLong(product.getId()))
                                                 .cmdb(product.getAlias())
                                                 .critical(product.getCritical())
                                                 .ownerName(product.getOwnerName())
                                                 .build())
                                         .toList());
    }

    public ResponseEntity<String> getInfluenceDot(Long Id) {
        var record = genericRepository.getNodeTypeAndNameById(Id);
        if (!record.hasNext()) {
            return ResponseEntity.badRequest().build();
        }
        var rec = record.next();
        String nodeType = rec.get("nodeType").asString("");
        String rootName = rec.get("name").asString("Unnamed");
        if (nodeType.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<org.neo4j.driver.Record> dependencyRecords = new ArrayList<>();

        switch (nodeType) {
            case "DeploymentNode":
                var depResults = genericRepository.getDeploymentNodeDependenciesShort(Id);
                if (depResults.hasNext())
                    dependencyRecords.add(depResults.next());
                break;

            case "Container":
                var containerResults = genericRepository.getContainerDependenciesShort(Id);
                if (containerResults.hasNext())
                    dependencyRecords.add(containerResults.next());
                break;

            case "InfrastructureNode":
                var infraResults = genericRepository.getInfrastructureNodeDependenciesShort(Id);
                if (infraResults.hasNext())
                    dependencyRecords.add(infraResults.next());
                break;

            default:
                return ResponseEntity.badRequest().body("Unsupported node type: " + nodeType);
        }

        Graph graph = new SingleGraph("InfluenceGraph");
        graph.setAttribute("rankdir", "RL");

        String rootLabel = trimAfterFirstDot(rootName);
        org.graphstream.graph.Node centralNode = graph.addNode("central");
        centralNode.setAttribute("label", rootLabel);
        centralNode.setAttribute("shape", "rect");
        centralNode.setAttribute("style", "filled");
        centralNode.setAttribute("color", "green");
        centralNode.setAttribute("fillcolor", "lightgreen");

        org.neo4j.driver.Record dependencyRecord = dependencyRecords.get(0);

        addNodesFromCollection(graph, dependencyRecord, "deploymentSources", "deploymentSources_", "orange", "red", "Вызов", "central");
        addNodesFromCollection(graph, dependencyRecord, "infrastructureSources", "infrastructureSources_", "orange", "red", "Вызов", "central");
        addNodesFromCollection(graph, dependencyRecord, "deploymentTargets", "deploymentParent_", "lightblue", "blue", "Deploy", "central");
        addNodesFromCollection(graph, dependencyRecord, "deploymentParent", "deploymentParent_", "lightblue", "blue", "Deploy", "central");
        addNodesFromCollection(graph, dependencyRecord, "containersSources", "containersSources_", "orange", "red", "Вызов", "central");

        String dotString = convertToDotFormat(graph);
        return ResponseEntity.ok(dotString);
    }

    private void addNodesFromCollection(Graph graph, org.neo4j.driver.Record record,
                                        String key, String idPrefix, String fillColor, String borderColor,
                                        String edgeLabel, String centralNodeId) {

        if (record.get(key).toString().equals("[]") || record.get(key).toString().equals("NULL")) return;
        List<Object> nodeObjects = record.get(key).asList();

        for (Object obj : nodeObjects) {
            if (!(obj instanceof org.neo4j.driver.types.Node)) continue;

            org.neo4j.driver.types.Node node = (org.neo4j.driver.types.Node) obj;
            String neo4jNodeId = String.valueOf(node.id());
            String nodeId = idPrefix + neo4jNodeId;

            org.graphstream.graph.Node graphNode;
            if (graph.getNode(nodeId) == null) {
                graphNode = graph.addNode(nodeId);
            } else {
                graphNode = graph.getNode(nodeId);
            }

            String name = node.get("name") != null ? node.get("name").asString("Unnamed") : "Unnamed";
            String label = trimAfterFirstDot(name);

            graphNode.setAttribute("label", label);
            graphNode.setAttribute("shape", "rect");
            graphNode.setAttribute("style", "filled");
            graphNode.setAttribute("fillcolor", fillColor);
            graphNode.setAttribute("color", borderColor);

            String edgeId = nodeId + "_" + centralNodeId;
            if (graph.getEdge(edgeId) == null) {
                graph.addEdge(edgeId, centralNodeId, nodeId, true).setAttribute("label", edgeLabel);
            }
        }
    }

    private String trimAfterFirstDot(String s) {
        int idx = s.indexOf('.');
        return (idx > 0) ? s.substring(0, idx) : s;
    }

    public ResponseEntity<List<DiagramElementInfluenceDTO>> getInfluenceElements(Long id) {
        var record = genericRepository.getNodeTypeAndNameById(id);
        if (!record.hasNext()) {
            return ResponseEntity.badRequest().build();
        }
        var rec = record.next();
        String nodeType = rec.get("nodeType").asString("");
        if (nodeType.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<org.neo4j.driver.Record> dependencyRecords = new ArrayList<>();
        Map<String, List<org.neo4j.driver.types.Node>> nodesMap = new HashMap<>();

        switch (nodeType) {
            case "DeploymentNode":
                var depResults = genericRepository.getDeploymentNodeDependenciesShort(id);
                if (depResults.hasNext())
                    dependencyRecords.add(depResults.next());
                break;

            case "Container":
                var containerResults = genericRepository.getContainerDependenciesShort(id);
                if (containerResults.hasNext())
                    dependencyRecords.add(containerResults.next());
                break;

            case "InfrastructureNode":
                var infraResults = genericRepository.getInfrastructureNodeDependenciesShort(id);
                if (infraResults.hasNext())
                    dependencyRecords.add(infraResults.next());
                break;

            default:
                return ResponseEntity.badRequest().build();
        }
        org.neo4j.driver.Record dependencyRecord = dependencyRecords.get(0);

        List<String> keys = List.of("deploymentSources", "infrastructureSources", "deploymentParent", "containersSources");

        for (String key : keys) {
            if (dependencyRecord.containsKey(key) && !dependencyRecord.get(key).isNull()) {
                List<org.neo4j.driver.types.Node> nodeList = dependencyRecord.get(key).asList(value -> value.asNode());
                if (!nodeList.isEmpty()) {
                    nodesMap.put(key, nodeList);
                }
            }
        }

        List<ProductInfoShortDTO> products = productClient.getAllProductsInfo();
        List<DiagramElementInfluenceDTO> dependentElements = new ArrayList<>();

        for (List<org.neo4j.driver.types.Node> nodeList : nodesMap.values()) {
            for (org.neo4j.driver.types.Node node : nodeList) {
                Long nodeId = node.id();
                String fullName = node.get("name") != null ? node.get("name").asString() : "Unnamed";

                String cmdb = trimAfterLastDot(fullName);

                ProductInfoShortDTO productInfo = products.stream()
                        .filter(product -> product.getAlias().equalsIgnoreCase(cmdb))
                        .findFirst()
                        .orElse(null);

                int influenceCount = genericRepository.getDependentCount(nodeId);

                dependentElements.add(DiagramElementInfluenceDTO.builder()
                                              .id(nodeId)
                                              .name(trimAfterFirstDot(fullName))
                                              .influenceCount(influenceCount)
                                              .cmdb(cmdb)
                                              .critical(productInfo != null ? productInfo.getCritical() : "")
                                              .ownerName(productInfo != null ? productInfo.getOwnerName() : "")
                                              .build());
            }
        }
        return ResponseEntity.ok(dependentElements);
    }

    public ResponseEntity<List<ContextElementDTO>> getContextInfluenceElements(String cmdb) {
    Set<String> dependentCmdbSet = new HashSet<>();

    addCmdbFromResult(dependentCmdbSet, genericRepository.getDependentInfluenceSystem(cmdb));
    addCmdbFromResult(dependentCmdbSet, genericRepository.getDependentSystemsChildContainerRelationshipInfluence(cmdb));
    addCmdbFromResult(dependentCmdbSet, genericRepository.getDependentSystemsChildContainerChildRelationshipInfluenceSystem(cmdb));

        return ResponseEntity.ok(productClient.getAllProductsInfo()
                .stream()
                                         .filter(product -> dependentCmdbSet.contains(product.getAlias().toLowerCase()))
            .map(product -> ContextElementDTO.builder()
            .id(Long.parseLong(product.getId()))
            .cmdb(product.getAlias())
            .critical(product.getCritical())
            .ownerName(product.getOwnerName())
            .build())
            .toList());
    }

    public ResponseEntity<String> getContextInfluenceDiagramDot(String cmdb) {
        Set<String> cmdbList = new HashSet<>();
        addCmdbFromResult(cmdbList, genericRepository.getDependentInfluenceSystem(cmdb));
        addCmdbFromResult(cmdbList, genericRepository.getDependentSystemsChildContainerRelationshipInfluence(cmdb));
        addCmdbFromResult(cmdbList, genericRepository.getDependentSystemsChildContainerChildRelationshipInfluenceSystem(cmdb));

        Graph graph = new SingleGraph("Neo4j Graph");
        graph.setAttribute("rankdir", "RL");

        org.graphstream.graph.Node centralNode = graph.addNode("central");
        centralNode.setAttribute("label", cmdb);
        centralNode.setAttribute("shape", "rect");
        centralNode.setAttribute("style", "filled");
        centralNode.setAttribute("color", "green");
        centralNode.setAttribute("fillcolor", "lightgreen");

        for (String depCmdb : cmdbList) {
            org.graphstream.graph.Node node;
            if (graph.getNode(depCmdb) == null) {
                node = graph.addNode(depCmdb);
            } else {
                node = graph.getNode(depCmdb);
            }
            node.setAttribute("label", depCmdb);
            node.setAttribute("shape", "rect");
            node.setAttribute("style", "filled");
            node.setAttribute("color", "red");
            node.setAttribute("fillcolor", "orange");

            String edgeId = depCmdb + "_central";
            if (graph.getEdge(edgeId) == null) {
                graph.addEdge(edgeId, "central", depCmdb, true).setAttribute("label", "Вызов");
            }
        }

        String dotFormat = convertToDotFormat(graph);
        return ResponseEntity.ok(dotFormat);
    }
}

