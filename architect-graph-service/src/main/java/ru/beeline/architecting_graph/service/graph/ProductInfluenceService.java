/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.beeline.architecting_graph.dto.ProductInfluenceDTO;
import ru.beeline.architecting_graph.exception.ConflictValuesException;
import ru.beeline.architecting_graph.repository.neo4j.DeploymentNodesRepository;
import ru.beeline.architecting_graph.repository.neo4j.SoftwareSystemRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductInfluenceService {

    @Autowired
    SoftwareSystemRepository softwareSystemRepository;

    @Autowired
    DeploymentNodesRepository deploymentNodesRepository;

    public ProductInfluenceDTO getRelatedSystems(String cmdb) {
            if (!softwareSystemRepository.productExists(cmdb)) {
                throw new NoSuchElementException("Продукт с cmdb = " + cmdb + " не найден");
            }
            List<String> dependentSystems = softwareSystemRepository.getDependentSystems(cmdb);
            List<String> influencingSystems = softwareSystemRepository.getInfluencingSystems(cmdb);

            return new ProductInfluenceDTO(
                    dependentSystems != null ?
                            dependentSystems.stream().distinct().collect(Collectors.toList()) :
                            Collections.emptyList(),
                    influencingSystems != null ?
                            influencingSystems.stream().distinct().collect(Collectors.toList()) :
                            Collections.emptyList()
            );
    }

    public ProductInfluenceDTO getDeploymentRelatedSystems(String cmdb, String name, String env) {
        Result deploymentNode = deploymentNodesRepository.getDeploymentNodeByEnvironmentAndCMDB(cmdb, name, env);
        Long deploymentNodeId = getDeploymentNodeId(deploymentNode);
        List<Long> deploymentNodeIds = deploymentNodesRepository.getDeploymentNodeChildRecursiveById(deploymentNodeId);
        if (!deploymentNodeIds.contains(deploymentNodeId)) {
            deploymentNodeIds.add(deploymentNodeId);
        }
        Set<String> influencingSystems = new HashSet<>();
        influencingSystems.addAll(softwareSystemRepository.getDirectInfluencingSystems(deploymentNodeIds));
        influencingSystems.addAll(softwareSystemRepository.findInfluencingSystemsByDeploymentNodeIds(deploymentNodeIds));
        influencingSystems.addAll(softwareSystemRepository.findInfluencingSystemsByDeploymentNodes(deploymentNodeIds));
        influencingSystems.addAll(softwareSystemRepository.getInfrastructureInfluencingSystems(deploymentNodeIds));

        Set<String> dependentSystems = new HashSet<>();
        dependentSystems.addAll(softwareSystemRepository.getDirectDependentSystems(deploymentNodeIds));
        dependentSystems.addAll(softwareSystemRepository.findDependentSystemsByDeploymentNodeIds(deploymentNodeIds));
        dependentSystems.addAll(softwareSystemRepository.findDependentSystemsByDeploymentNodes(deploymentNodeIds));
        dependentSystems.addAll(softwareSystemRepository.getDependentSystemsForDeploymentNodes(deploymentNodeIds));

        return ProductInfluenceDTO.builder()
                .influencingSystems(new ArrayList<>(influencingSystems))
                .dependentSystems(new ArrayList<>(dependentSystems))
                .build();
    }

    private Long getDeploymentNodeId(Result deploymentNode) {
        Long id = null;
        if (deploymentNode.hasNext()) {
            id = deploymentNode.next().get("id").asLong();
        } else {
            throw new NoSuchElementException("Запись найдена");
        }
        if (deploymentNode.hasNext()) {
            throw new ConflictValuesException("Конфликт данных");
        }
        return id;
    }
}
