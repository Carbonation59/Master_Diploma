package ru.beeline.architecting_graph.dto.search;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class ProductSearchDTO {

    private Integer id;
    private String name;
    private String alias;
}
