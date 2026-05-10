/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.beeline.architecting_graph.dto.*;
import ru.beeline.architecting_graph.exception.ConflictValuesException;
import ru.beeline.architecting_graph.service.getElements.ElementService;
import ru.beeline.architecting_graph.service.graph.ContainerInstanceService;
import ru.beeline.architecting_graph.service.graph.GraphConstructionService;
import ru.beeline.architecting_graph.service.graph.ProductInfluenceService;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1")
@Validated
public class GraphController {


    @Autowired
    GraphConstructionService graphConstructionService;

    @Autowired
    ProductInfluenceService productInfluenceService;

    @Autowired
    ContainerInstanceService containerInstanceService;

    @Autowired
    ElementService elementService;

    @GetMapping("/search/software-system")
    @Operation(summary = "Поиск deploymentNode")
    public ResponseEntity<List<SearchSoftwareSystemDTO>> getSoftwareSystem(@RequestParam String search) {
        return graphConstructionService.getSoftwareSystem(search);
    }

    @GetMapping("/influence")
    @Operation(summary = "Получить системы связанные с контейнером")
    public ResponseEntity<InfluenceResponseDTO> getContainerInfluence(
            @RequestParam String cmdb,
            @RequestParam String name) {
        return graphConstructionService.getContainerInfluence(cmdb, name);
    }

    @GetMapping("/graph/{graph-type}/task/{task-id}")
    @Operation(summary = "Получение статуса графа по taskKey и типу графа")
    public ResponseEntity<TaskCacheDTO> getGraphByTask(@PathVariable("graph-type") String graphType,
            @PathVariable("task-id") String taskId,
            @Value("${app.feature.use-doc-service:false}") boolean isDocServiceEnabled) {
        if (!isDocServiceEnabled) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);
        }
        return graphConstructionService.getGraphByTask(graphType, taskId);
    }

    @PostMapping("/graph/local/json")
    @Operation(summary = "Пересоздание локального графа, используя документ, в котором описывается система (все вершины и связи помечаются graphTag: Local)")
    public ResponseEntity<String> LocalGraph(@RequestBody String json) {
        return graphConstructionService.graphConstruct(json, "Local");
    }

    @PostMapping("/graph/json")
    @Operation(summary = "Добавление системы из указанного документа в глобальный граф (все вершины и связи помечаются graphTag: Global)")
    public ResponseEntity<String> GlobalGraph(@RequestBody String json) {
        return graphConstructionService.graphConstruct(json, "Global");
    }

    @GetMapping("/graph/product/{cmdb}/influence")
    @Operation(summary = "Метод для получения связанных систем")
    public ResponseEntity<ProductInfluenceDTO> getInfluence(@PathVariable String cmdb) {
        try {
            return ResponseEntity.ok(productInfluenceService.getRelatedSystems(cmdb));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/graph/deployment/{cmdb}/influence")
    @Operation(summary = "Метод для получения связанных систем деплоймента")
    public ResponseEntity<ProductInfluenceDTO> getDeploymentInfluence(@PathVariable String cmdb,
            @RequestParam String name,
            @RequestParam String env) {
        try {
            return ResponseEntity.ok(productInfluenceService.getDeploymentRelatedSystems(cmdb, name, env));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (ConflictValuesException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping("/elements")
    public ResponseEntity<String> getElements(@RequestHeader(value = "CYPHER-QUERY") String query) {
        return elementService.processingQuery(query);
    }
}
