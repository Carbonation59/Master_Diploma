package ru.beeline.architecting_graph.dto.search;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class OperationDeploymentNodeSearchDTO {

    private List<ArchOperationDTO> archOperations;
    private List<DiscoveredOperationDTO> discoveredOperations;
}
