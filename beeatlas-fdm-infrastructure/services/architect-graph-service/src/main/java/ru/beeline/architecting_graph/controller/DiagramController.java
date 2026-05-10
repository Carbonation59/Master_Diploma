/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.beeline.architecting_graph.dto.ContextElementDTO;
import ru.beeline.architecting_graph.dto.DiagramElementDTO;
import ru.beeline.architecting_graph.dto.DiagramElementInfluenceDTO;
import ru.beeline.architecting_graph.service.createDiagrams.DiagramService;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class DiagramController {

    @Autowired
    DiagramService diagramService;


    @GetMapping("/deployment/{environment}/{softwareSystemMnemonic}")
    @Operation(summary = "Генерация json с описанием deploymentView")
    public ResponseEntity<String> getDeploymentDiagramm(@PathVariable String environment,
                                                        @PathVariable String softwareSystemMnemonic,
                                                        @PathVariable(required = false) String rankDirection) {

        return diagramService.createDiagram(softwareSystemMnemonic, null, environment, rankDirection);
    }

    @GetMapping("/context/{softwareSystemMnemonic}")
    @Operation(summary = "Генерация json с описанием contextView")
    public ResponseEntity<String> getContextDiagram(@PathVariable String softwareSystemMnemonic,
                                                    @RequestParam(required = false) String rankDirection) {
        return getC4Diagram(softwareSystemMnemonic, null, rankDirection);
    }

    @GetMapping("/diagram/context")
    @Operation(summary = "Построение context диаграммы V2")
    public ResponseEntity<String> getContextDiagramV2(@RequestParam String cmdb,
                                                      @RequestParam(required = false) String rankDirection,
                                                      @RequestParam String communicationDirection) {
        return diagramService.createContextDiagramV2(cmdb, rankDirection, communicationDirection);
    }

    @GetMapping("/context/{softwareSystemMnemonic}/{containerMnemonic}")
    @Operation(summary = "Генерация json с описанием containerView")
    public ResponseEntity<String> getC4Diagram(@PathVariable String softwareSystemMnemonic,
                                               @PathVariable(required = false) String containerMnemonic,
                                               @RequestParam(required = false) String rankDirection) {

        return diagramService.createDiagram(softwareSystemMnemonic, containerMnemonic, null, rankDirection);
    }

    @GetMapping("/diagram/deployment")
    @Operation(summary = "Построение Deployment диаграммы")
    public ResponseEntity<String> getDiagramDeployment(@RequestParam String cmdb,
                                                       @RequestParam String env,
                                                       @RequestParam(name = "rank-direction", required = false) String rankDirection,
                                                       @RequestParam(name = "deployment-name") String deploymentName) {
        return diagramService.getDiagramDeployment(cmdb, env, rankDirection, deploymentName);
    }

    @GetMapping("/diagram/dot")
    @Operation(
            summary = "Построение Deployment диаграммы dot",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный ответ",
                            content = @Content(mediaType = "text/plain")),
                    @ApiResponse(responseCode = "400", description = "Неверный запрос: Unsupported node type или другие ошибки",
                            content = @Content(mediaType = "text/plain"))
            }
    )
    public ResponseEntity<String> getDiagramDeploymentDot(@RequestParam Long id) {
        return diagramService.getDiagramDeploymentDot(id);
    }

    @GetMapping("/context/dot")
    @Operation(summary = "Построение Deployment диаграммы с зависимыми систетмами в формате DOT")
    public ResponseEntity<String> getContextDiagramDot(@RequestParam String cmdb) {
        return diagramService.getContextDiagramDot(cmdb);
    }

    @GetMapping("/context/influence/dot")
    @Operation(summary = "Построение Deployment диаграммы с влияемыми системами в формате DOT")
    public ResponseEntity<String> getContextInfluenceDiagramDot(@RequestParam String cmdb) {
        return diagramService.getContextInfluenceDiagramDot(cmdb);
    }

    @GetMapping("/diagram/elements")
    @Operation(
            summary = "Построение elements",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный ответ",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = DiagramElementDTO.class))),
                    @ApiResponse(responseCode = "400", description = "Неверный запрос",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = String.class)))
            }
    )
    public ResponseEntity<List<DiagramElementDTO>> getDiagramDeploymentElements(@RequestParam Long id) {
        return diagramService.getDiagramDeploymentElements(id);
    }

    @GetMapping("/context/elements")
    @Operation(
            summary = "Построение elements",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный ответ",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = DiagramElementDTO.class)))
            }
    )
    public ResponseEntity<List<ContextElementDTO>> getContextElements(@RequestParam String cmdb) {
        return diagramService.getContextElements(cmdb);
    }

    @GetMapping("/influence/dot")
    @Operation(
            summary = "ПОлучение DOT диаграммы",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный ответ",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "400", description = "Неверный запрос: Unsupported node type или другие ошибки",
                            content = @Content(mediaType = "text/plain"))
            }
    )
    public ResponseEntity<String> getInfluenceDot(@RequestParam Long id) {
        return diagramService.getInfluenceDot(id);
    }
    @GetMapping("/influence/elements")
    @Operation(
            summary = "Получение элементов от которых зависит элемент инфраструктуры",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный ответ",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "400", description = "Неверный запрос",
                            content = @Content(mediaType = "text/plain"))
            }
    )
    public ResponseEntity<List<DiagramElementInfluenceDTO>> getInfluenceElements(@RequestParam Long id) {
        return diagramService.getInfluenceElements(id);
    }

    @GetMapping("/context/influence/elements")
    @Operation(
            summary = "Получение список влияющих систем",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный ответ",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "400", description = "Неверный запрос",
                            content = @Content(mediaType = "text/plain"))
            }
    )
    public ResponseEntity<List<ContextElementDTO>> getInfluenceElements(@RequestParam String cmdb) {
        return diagramService.getContextInfluenceElements(cmdb);
    }
}
