/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import ru.beeline.architecting_graph.dto.SequenceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.dto.*;
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

@Slf4j
@Service
public class GraphConstructionService {

    @Autowired
    EnvironmentRepository environmentRepository;

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
}
