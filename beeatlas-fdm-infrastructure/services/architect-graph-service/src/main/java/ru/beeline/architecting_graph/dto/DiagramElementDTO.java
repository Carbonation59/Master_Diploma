/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagramElementDTO {
    private Long id;
    private String name;
    private Integer dependentCount;
    private String cmdb;
    private String critical;
    private String ownerName;
}