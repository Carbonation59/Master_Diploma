/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class ProductInfraSearchDTO {
    private String name;
    private String parameter;
    private String value;
    private List<String> parentSystems;
}
