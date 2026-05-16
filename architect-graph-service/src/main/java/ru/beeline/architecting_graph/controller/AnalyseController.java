package ru.beeline.architecting_graph.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;

import ru.beeline.architecting_graph.service.analyse.AnalyseService;

@RestController
@RequestMapping("/api/v1/analyse")
@Validated

public class AnalyseController {

    @Autowired
    AnalyseService analyseService;

    @GetMapping("/cycles")
    @Operation(summary = "Поиск циклических зависимостей в архитектуре")
    public ResponseEntity<String> findCycles(
            @RequestParam(required = false) String graphTag,
            @RequestParam(required = false) String nodeTypes,
            @RequestParam(required = false) String relTypes,
            @RequestParam(required = false) String nodeIdentifiers) {

        return analyseService.findCycles(graphTag, nodeTypes, relTypes, nodeIdentifiers);
    }

    @GetMapping("/singlepoints")
    @Operation(summary = "Поиск единых точек отказа в архитектуре")
    // связи учитываются как ненаправленные
    public ResponseEntity<String> findSinglePoints(
            @RequestParam(required = false) String graphTag,
            @RequestParam(required = false) String nodeTypes,
            @RequestParam(required = false) String relTypes,
            @RequestParam(required = false) String nodeIdentifiers) {

        return analyseService.findSinglePoints(graphTag, nodeTypes, relTypes, nodeIdentifiers);
    }

    @GetMapping("/godelements")
    @Operation(summary = "Поиск \"божественного объекта\" в архитектуре")
    // связи учитываются как ненаправленные
    // без PageRank
    // без betweenness centrality -- показывает, как часто узел выступает в роли
    // «моста» на кратчайших путях между другими узлами
    public ResponseEntity<String> findGodElements(
            @RequestParam(required = false) String graphTag,
            @RequestParam(required = false) String nodeTypes,
            @RequestParam(required = false) String relTypes,
            @RequestParam(required = false) String nodeIdentifiers) {

        return analyseService.findGodElements(graphTag, nodeTypes, relTypes, nodeIdentifiers);
    }

    @GetMapping("/pathcapacity")
    @Operation(summary = "Пропускная способность пути")
    // связи учитываются как направленные
    public ResponseEntity<String> findPathCapacity(
            @RequestParam(required = false) String nodeTypes,
            @RequestParam(required = false) String relTypes,
            @RequestParam(required = false) String nodeIdentifiers) {

        return analyseService.findPathCapacity(nodeTypes, relTypes, nodeIdentifiers);
    }

    @GetMapping("/criticalinfrastructure")
    @Operation(summary = "Поиск критической инфраструктуры (общих узлов развёртывания)")
    public ResponseEntity<String> findCriticalInfrastructure(
            @RequestParam(required = false) String nodeIdentifiers) {

        return analyseService.findCriticalInfrastructure(nodeIdentifiers);
    }

    @GetMapping("/perimeterviolation")
    @Operation(summary = "Контроль периметра (нарушение периметра развёртывания)")
    public ResponseEntity<String> findPerimeterViolation(
            @RequestParam(required = false) String deploymentNodeIdentifier,
            @RequestParam(required = false) String properties) {

        return analyseService.findPerimeterViolation(deploymentNodeIdentifier, properties);
    }

}
