/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import ru.beeline.architecting_graph.dto.SequenceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import ru.beeline.architecting_graph.client.DocumentClient;
import ru.beeline.architecting_graph.client.ProductClient;
import ru.beeline.architecting_graph.dto.*;
import ru.beeline.architecting_graph.dto.search.ArchOperationDTO;
import ru.beeline.architecting_graph.dto.search.DeploymentNodeSearchDTO;
import ru.beeline.architecting_graph.dto.search.DiscoveredOperationDTO;
import ru.beeline.architecting_graph.dto.search.OperationDeploymentNodeSearchDTO;
import ru.beeline.architecting_graph.exception.ValidationException;
import ru.beeline.architecting_graph.model.Workspace;
import ru.beeline.architecting_graph.repository.neo4j.*;
import java.util.Comparator;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.beeline.architecting_graph.utils.JwtUtils.isIpAddress;

@Slf4j
@Service
public class GraphConstructionService {

    @Autowired
    EnvironmentRepository environmentRepository;

    @Autowired
    DocumentClient documentClient;

    @Autowired
    GraphUpdateFunctions graphUpdateFunctions;

    @Autowired(required = false)
    RedisTemplate<String, TaskCacheDTO> redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DeploymentNodesRepository deploymentNodesRepository;

    @Autowired
    SoftwareSystemRepository softwareSystemRepository;

    @Autowired
    ContainerRepository containerRepository;

    @Autowired
    ComponentRepository componentRepository;

    @Autowired
    GenericRepository genericRepository;

    @Autowired
    ProductClient productClient;

    public ResponseEntity<String> graphConstruct(Long docId, String graphTag) {
        log.info("graphConstruct is running");
        String workspaceJson = documentClient.getDocument(docId);
        if (workspaceJson == null) {
            log.info("Документ не найден");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Документ не найден");
        }
        return graphConstruct(workspaceJson, graphTag);
    }

    public ResponseEntity<String> graphConstruct(String workspaceJson, String graphTag) {
        log.info("graphConstruct is running");
        Workspace workspace;
        try {
            workspace = objectMapper.readValue(workspaceJson, Workspace.class);
        } catch (Exception e) {
            log.info("Полученный workspace не валиден: " + e.getMessage());
            log.info("workspaceJson is: " + workspaceJson);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Полученный workspace не валиден");
        }
        try {
            graphUpdateFunctions.createGraph(graphTag, workspace);
        } catch (Exception e) {
            log.info("Граф не построен: " + e.getMessage());
            return ResponseEntity.badRequest().body("Граф не построен\n" + e.getMessage());
        }
        log.info("graph constructed");
        return ResponseEntity.status(HttpStatus.CREATED).body("Граф построен");
    }

    private String handleClientError(HttpClientErrorException e) {
        HttpStatus status = e.getStatusCode();
        if (status == HttpStatus.NOT_FOUND) {
            return null;
        } else if (status == HttpStatus.BAD_REQUEST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Полученный workspace не валиден");
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ запрещен");
        }
    }

    public static Workspace getWorkspaceFileForTest(ObjectMapper objectMapper) throws Exception {
        String FilePath = "workspace_RNC.json";
        File file = new File(FilePath);
        Workspace workspace = objectMapper.readValue(file, Workspace.class);
        return workspace;
    }

    public ResponseEntity<TaskCacheDTO> getGraphByTask(String graphType, String taskId) {
        String redisKey = "graph:" + taskId;

        TaskCacheDTO existingDto = redisTemplate.opsForValue().get(redisKey);

        if (existingDto == null) {
            log.warn("No cache entry for taskId: {}", taskId);
            return ResponseEntity.notFound().build();
        }

        if (!graphType.equalsIgnoreCase(existingDto.getType())) {
            log.warn("Cache entry type '{}' does not match requested graphType '{}'", existingDto.getType(), graphType);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(existingDto);
    }

    public ResponseEntity<List<DeploymentNodeDTO>> getDeploymentNode(String search) {
        List<DeploymentNodeDTO> result = new ArrayList<>();
        Result deploymentNodes = deploymentNodesRepository.findDeploymentNodesBySearch(search);
        if(!deploymentNodes.hasNext() && isIpAddress(search))
        {
            List<ProductInfraSearchDTO> products = productClient.getProductInfraByVimIp(search);
            if(products.isEmpty()){
                ResponseEntity.ok(result);
            }
            List<String> originNames = products.stream()
                    .map(ProductInfraSearchDTO::getName)
                    .collect(Collectors.toList());
            originNames.forEach(name-> {
                deploymentNodesRepository.updateIpForDeploymentNodesByOriginNames(name, products.get(0).getValue());
            });

            deploymentNodes = deploymentNodesRepository.findDeploymentNodesBySearch(search);
        }
        while (deploymentNodes.hasNext()) {
            try {

                Record deployment = deploymentNodes.next();
                Record system = getParentSoftwareSystem(deployment.get("n").asNode().id());
                Record environment = getParentEnvironment(deployment.get("n").asNode().id());
                result.add(DeploymentNodeDTO.builder()
                                   .id(deployment.get("n").asNode().id())
                                   .deploymentName(deployment.get("n").asNode().get("originalName").asString())
                                   .environmentName(environment.get("parent.name").asString())
                                   .cmdb(system.get("d").asNode().get("cmdb").asString())
                                   .ip(deployment.get("n").asNode().containsKey("ip") ? deployment.get("n")
                                           .asNode()
                                           .get("ip")
                                           .asString() : null)
                                   .host(deployment.get("n").asNode().containsKey("host") ? deployment.get("n")
                                           .asNode()
                                           .get("host")
                                           .asString() : null)
                                   .build());
            } catch (Exception e){
                log.error(e.getMessage(), e.getStackTrace());
            }
        }

        return ResponseEntity.ok(result);
    }

    private Record getParentSoftwareSystem(Long id) {
        Result result = softwareSystemRepository.getParentSystemByDeploymentNodeId(id);
        if (result.hasNext()) {
            Record record = result.next();
            Long parentSystemId = record.get("parent").asNode().id();
            return softwareSystemRepository.getSystemById(parentSystemId).next();
        } else {
            log.info("search ParentDeployment for id=" + id);
            Result parentNodeResult = deploymentNodesRepository.getParentDeploymentNodeId(id);
            Record record = parentNodeResult.next();
            Long parentNodeId = record.get("parent").asNode().id();
            return getParentSoftwareSystem(parentNodeId);
        }
    }

    private Record getParentEnvironment(Long nodeId) {
        Result result = environmentRepository.getDeploymentNodeEnvironmentNameByIdChild(nodeId);
        if (result.hasNext()) {
            return result.next();
        } else {
            Result parentNodeResult = deploymentNodesRepository.getParentDeploymentNodeId(nodeId);
            Record record = parentNodeResult.next();
            Long parentNodeId = record.get("parentNodeId").asLong();
            return getParentEnvironment(parentNodeId);
        }
    }

    public ResponseEntity<String> createSequence(List<SequenceDto> sequenceDtos) {
        if (sequenceDtos.isEmpty()){
            throw new ValidationException("Отсутствут sequenceDtos");
        }
        for (SequenceDto sequenceDto : sequenceDtos) {
            String diagramKey = sequenceDto.getDiagramKey();
            List<SequenceDto.SequenceItemDto> sequence = sequenceDto.getSequence();

            SequenceDto.SequenceItemDto firstItem = sequence.stream()
                    .filter(item -> item.getOrder() == 1)
                    .findFirst()
                    .orElse(null);

            if (firstItem == null) {
                log.warn("Нет элемента с order=1 для diagramKey={}", diagramKey);
                return ResponseEntity.badRequest().body("Нет элемента с order=1 для diagramKey=" + diagramKey);
            }

            SequenceDto.ComponentDto inDto = firstItem.getIn();
            long[] inSsInfo = genericRepository.findOrCreateSoftwareSystemForSequence(inDto.getSoftwaresystem());
            long inSsVersion = inSsInfo[1];
            long inContainerId = genericRepository.findOrCreateContainerUnderSS(inSsInfo[0], inDto.getContainer(), inSsVersion);
            long inComponentId = genericRepository.findOrCreateComponentUnderContainer(inContainerId, inDto.getComponent(), inSsVersion);
            genericRepository.findOrCreateStartPoint(inComponentId, firstItem.getMethod(), diagramKey, firstItem.getTcCode(), inSsVersion);

            List<SequenceDto.SequenceItemDto> remainingItems = sequence.stream()
                    .filter(item -> item.getOrder() > 1)
                    .sorted(Comparator.comparingInt(SequenceDto.SequenceItemDto::getOrder))
                    .collect(Collectors.toList());

            for (SequenceDto.SequenceItemDto item : remainingItems) {
                SequenceDto.ComponentDto outDto = item.getOut();
                SequenceDto.ComponentDto itemInDto = item.getIn();

                long[] outSsInfo = genericRepository.findOrCreateSoftwareSystemForSequence(outDto.getSoftwaresystem());
                long outSsVersion = outSsInfo[1];
                long outContainerId = genericRepository.findOrCreateContainerUnderSS(outSsInfo[0], outDto.getContainer(), outSsVersion);
                long outComponentId = genericRepository.findOrCreateComponentUnderContainer(outContainerId, outDto.getComponent(), outSsVersion);

                long[] itemInSsInfo = genericRepository.findOrCreateSoftwareSystemForSequence(itemInDto.getSoftwaresystem());
                long itemInSsVersion = itemInSsInfo[1];
                long itemInContainerId = genericRepository.findOrCreateContainerUnderSS(itemInSsInfo[0], itemInDto.getContainer(), itemInSsVersion);
                long itemInComponentId = genericRepository.findOrCreateComponentUnderContainer(itemInContainerId, itemInDto.getComponent(), itemInSsVersion);

                if (!genericRepository.sequenceRelationshipExists(outComponentId, itemInComponentId,
                                                                  item.getMethod(), diagramKey, item.getTcCode())) {
                    genericRepository.createSequenceRelationship(outComponentId, itemInComponentId,
                                                                 item.getMethod(), diagramKey, item.getTcCode(), outSsVersion);
                }
            }
        }
        log.info("Последовательности успешно созданы, количество диаграмм: {}", sequenceDtos.size());
        return ResponseEntity.ok().build();
    }

    public ResponseEntity<List<SearchSoftwareSystemDTO>> getSoftwareSystem(String search) {
        List<SearchSoftwareSystemDTO> result = new ArrayList<>();
        Result softwareSystems = softwareSystemRepository.searchSoftwareSystemsByCMDBorName(search);
        while (softwareSystems.hasNext()) {
            Record softwareSystem = softwareSystems.next();
            result.add(SearchSoftwareSystemDTO.builder()
                               .name(softwareSystem.get("n").asNode().get("name").asString())
                               .cmdb(softwareSystem.get("n").asNode().get("cmdb").asString())
                               .build());
        }
    return ResponseEntity.ok(result);}

    public ResponseEntity<InfluenceResponseDTO> getContainerInfluence(String cmdb, String name) {
        if (!softwareSystemRepository.productExists(cmdb)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Long containerId = containerRepository.findContainerIdByParentSystemAndName(name, cmdb);
        if (containerId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Set<String> dependentSystems =
                new HashSet<>(softwareSystemRepository.findDependentSystemsByContainerId(containerId));

        Set<String> influencingSystems = new HashSet<>(softwareSystemRepository.findInfluencingSystemsByNodeId(containerId));

        List<Long> components = componentRepository.findComponentsByContainer(containerId);
        for (Long componentId : components) {
            dependentSystems.addAll(softwareSystemRepository.findDependentChildSystemsByComponent(componentId));
            influencingSystems.addAll(softwareSystemRepository.findDependentParentSystemsByComponent(componentId));
        }

        return ResponseEntity.ok(InfluenceResponseDTO.builder()
                .dependentSystems(new ArrayList<>(dependentSystems))
                .influencingSystems(new ArrayList<>(influencingSystems))
                .build());
    }

    public ResponseEntity postTags(Long id, List<String> tags) {

        if (!genericRepository.checkIfObjectExistsById(id)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Нода с указанным ID не существует");
        }

        Value graphTagValue = genericRepository.getObjectParameterGeneric("Global", id, "graphTag");
        if (!"Global".equals(graphTagValue.asString())) {
            return ResponseEntity.badRequest()
                    .body("Нода с id = " + id + " не имеет тега 'Global'");
        }
        if(tags == null || tags.isEmpty()){
            return ResponseEntity.badRequest()
                    .body("Отсутствуют теги");
        }

        String newTags = String.join(",", tags);

        try {
            Value existingTags = genericRepository.getObjectParameterGeneric("Global", id, "specialTags");
            String currentTags = (existingTags != null && existingTags.asString() != null) ? existingTags.asString() : "";
            currentTags = "null".equals(currentTags) ? "" : currentTags;
            String updatedTags = currentTags.isEmpty() ? newTags : currentTags + "," + newTags;
            genericRepository.setObjectParameterGeneric("Global", id, "specialTags", updatedTags);
        } catch (Exception e) {
            genericRepository.setObjectParameterGeneric("Global", id, "specialTags", newTags);
        }

        return ResponseEntity.ok().build();
    }

    public ResponseEntity<OperationDeploymentNodeSearchDTO> getOperationWithDeploymentNodeByMethods(String path,
                                                                                                    String type) {
        if (path == null || path.isEmpty()) {
            throw new ValidationException("Отсутствует обязательный параметр path");
        }
        log.info("callProductClient");
        OperationDeploymentNodeSearchDTO operations = productClient.getOperations(path, type);
        log.info("add to arch operations");
        if(operations.getArchOperations()!=null) {
            operations.getArchOperations().forEach(arcOperation -> {
                fillDeploymentNode(arcOperation);
            });
        }
        log.info("add to discover operations");
        if(operations.getArchOperations()!=null) {
            operations.getDiscoveredOperations().forEach(dsvrOperation -> {
                fillDeploymentNode(dsvrOperation);
            });
        }
        log.info("result");
        return ResponseEntity.ok(operations);
    }

    private void fillDeploymentNode(ArchOperationDTO arcOperation) {
        if (arcOperation.getContainer() == null || arcOperation.getProduct() == null) {
            return;
        }

        String containerName = arcOperation.getContainer().getName();
        String productAlias = arcOperation.getProduct().getAlias();

        List<DeploymentNodeSearchDTO> deploymentNodes = genericRepository.findDeploymentNodes(containerName, productAlias);
        arcOperation.setDeploymentsNodes(deploymentNodes);
    }

    private void fillDeploymentNode(DiscoveredOperationDTO dsvrOperation) {
        if (dsvrOperation.getContainer() == null || dsvrOperation.getProduct() == null) {
            return;
        }

        String containerName = dsvrOperation.getContainer().getName();
        String productAlias = dsvrOperation.getProduct().getAlias();

        List<DeploymentNodeSearchDTO> deploymentNodes = genericRepository.findDeploymentNodes(containerName, productAlias);
        dsvrOperation.setDeploymentsNodes(deploymentNodes);
    }
}
