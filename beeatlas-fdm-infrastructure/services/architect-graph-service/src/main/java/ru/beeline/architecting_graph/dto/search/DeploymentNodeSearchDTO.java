package ru.beeline.architecting_graph.dto.search;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class DeploymentNodeSearchDTO {

    private Integer id;
    private String name;
    private String environmentName;
}
