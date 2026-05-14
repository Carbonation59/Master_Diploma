/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class ProductInfoShortDTO {

    private String alias;
    private String description;
    private String gitUrl;
    private String id;
    private String name;
    private String structurizrApiUrl;
    private String structurizrWorkspaceName;
    private String uploadSource;
    private LocalDateTime uploadDate;
    private String ownerName;
    private String critical;
}
