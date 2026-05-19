/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.beeline.architecting_graph.service.getElements.ElementService;
import ru.beeline.architecting_graph.service.graph.ContainerInstanceService;
import ru.beeline.architecting_graph.service.graph.GraphConstructionService;
import ru.beeline.architecting_graph.service.graph.ProductInfluenceService;


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

    @GetMapping("/elements")
    public ResponseEntity<String> getElements(@RequestHeader(value = "CYPHER-QUERY") String query) {
        return elementService.processingQuery(query);
    }
}
