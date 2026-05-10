/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.controller;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.beeline.architecting_graph.dto.*;
import ru.beeline.architecting_graph.dto.product.TcDTO;
import ru.beeline.architecting_graph.dto.search.OperationDeploymentNodeSearchDTO;
import ru.beeline.architecting_graph.exception.ConflictValuesException;
import ru.beeline.architecting_graph.service.compareVersions.CompareVersionsService;
import ru.beeline.architecting_graph.service.createDiagrams.ContainerComponentBuilder;
import ru.beeline.architecting_graph.service.getElements.ElementService;
import ru.beeline.architecting_graph.service.graph.ContainerInstanceService;
import ru.beeline.architecting_graph.service.graph.GraphConstructionService;
import ru.beeline.architecting_graph.service.graph.ProductInfluenceService;

import javax.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1")
@Validated
public class GraphController {

    @Autowired
    CompareVersionsService compareVersionService;

    @Autowired
    GraphConstructionService graphConstructionService;

    @Autowired
    ProductInfluenceService productInfluenceService;

    @Autowired
    ContainerComponentBuilder containerComponentBuilder;

    @Autowired
    ContainerInstanceService containerInstanceService;

    @Autowired
    ElementService elementService;

    @GetMapping("/search/deployment-node")
    @Operation(summary = "Поиск deploymentNode")
    public ResponseEntity<List<DeploymentNodeDTO>> getDeploymentNode(@RequestParam String search) {
        return graphConstructionService.getDeploymentNode(search);
    }

    @GetMapping("/search/container")
    @Operation(summary = "Поиск containerNode")
    public ResponseEntity<List<ContainerNodeDTO>> getContainerNode(@RequestParam(required = false) String search) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(containerComponentBuilder.findContainersWithParentCmdb(search));
    }

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

    @GetMapping("/deployment-nodes/operation")
    @Operation(summary = "Поиск деплоймент нод по реализованным методам")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Деплоймент ноды"),
            @ApiResponse(responseCode = "400", description = "Отсутствует обязательный параметр path", content = @Content)
    })
    public ResponseEntity<OperationDeploymentNodeSearchDTO> getOperationWithDeploymentNodeByMethods(
            @RequestParam String path,
            @RequestParam(required = false) String type) {
        return graphConstructionService.getOperationWithDeploymentNodeByMethods(path, type);
    }

    @PostMapping("/graph/local/{docId}")
    @Operation(summary = "Пересоздание локального графа, используя документ, в котором описывается система (все вершины и связи помечаются graphTag: Local)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Граф успешно пересоздан"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен", content = @Content),
            @ApiResponse(responseCode = "404", description = "Документ не найден", content = @Content),
            @ApiResponse(responseCode = "503", description = "Ошибка при загрузке документа", content = @Content)
    })
    public ResponseEntity<String> LocalGraph(@PathVariable("docId") Long docId,
            @Value("${app.feature.use-doc-service:false}") boolean isDocServiceEnabled) {
        if (!isDocServiceEnabled) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);
        }
        return graphConstructionService.graphConstruct(docId, "Local");
    }

    @PostMapping("/node/{id}/tag")
    @Operation(summary = "Добавление кастомных тегов к нодам глобального графа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Теги успешно добавлены", content = @Content),
            @ApiResponse(responseCode = "404", description = "Нода с указанным ID не существует", content = @Content),
            @ApiResponse(responseCode = "400", description = "Нода существует, но graphTag != Global", content = @Content) })
    public ResponseEntity LocalGraph(@PathVariable("id") Long id, @RequestBody List<String> tags) {

        return graphConstructionService.postTags(id, tags);
    }

    @PostMapping("/sequence")
    @Operation(summary = "Построение сиквенса")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Упешно построен сиквенс", content = @Content),
            @ApiResponse(responseCode = "400", description = "Неправильные поля", content = @Content) })
    public ResponseEntity<?> createSequence(@Valid @RequestBody List<@Valid SequenceDto> sequenceDtos) {
        return graphConstructionService.createSequence(sequenceDtos);
    }

    @PostMapping("/graph/{docId}")
    @Operation(summary = "Добавление системы из указанного документа в глобальный граф (все вершины и связи помечаются graphTag: Global)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Граф успешно пересоздан"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен", content = @Content),
            @ApiResponse(responseCode = "404", description = "Документ не найден", content = @Content),
            @ApiResponse(responseCode = "503", description = "Ошибка при загрузке документа", content = @Content)
    })
    public ResponseEntity<String> GlobalGraph(@PathVariable("docId") Long docId,
            @Value("${app.feature.use-doc-service:false}") boolean isDocServiceEnabled) {
        if (!isDocServiceEnabled) {
            throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);
        }
        return graphConstructionService.graphConstruct(docId, "Global");
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

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список ТС найден", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TcDTO.class)))),
            @ApiResponse(responseCode = "404", description = "DeploymentNode с указанным id не найдена", content = @Content(schema = @Schema(implementation = ErrorResponse.class))

            )
    })
    @GetMapping("/deployment-node/{id}/containers/tech-capability")
    @Operation(summary = "Получение по id deploymentNode контейнеров, которые в ней развернуты с реализованными в них ТС")
    public ResponseEntity<List<TcDTO>> getContainerInstancesByDeploymentNodeId(@PathVariable Integer id) {
        return containerInstanceService.getContainerInstancesByDeploymentNodeId(id);
    }

    @GetMapping("/diff/{cmdb}/{firstVersion}/{secondVersion}")
    @Operation(summary = "Сравнение двух версий указанной системы")
    public ResponseEntity<String> compareVersions(@PathVariable String cmdb,
            @PathVariable Integer firstVersion,
            @PathVariable(required = false) Integer secondVersion) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(compareVersionService.compareVersion(cmdb, firstVersion, secondVersion));
    }

    @GetMapping("/diff/{cmdb}/{firstVersion}")
    @Operation(summary = "Сравнение указанной версии системы с текущей (последней/актуальной)")
    public ResponseEntity<String> compareWithCur(@PathVariable String cmdb, @PathVariable Integer firstVersion) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(compareVersionService.compareVersion(cmdb, firstVersion, null));
    }

    @GetMapping("/elements")
    public ResponseEntity<String> getElements(@RequestHeader(value = "CYPHER-QUERY") String query) {
        return elementService.processingQuery(query);
    }
}
