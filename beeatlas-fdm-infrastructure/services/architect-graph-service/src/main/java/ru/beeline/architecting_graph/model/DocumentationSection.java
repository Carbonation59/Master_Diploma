/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentationSection {

    private String content;
    private Format format;
    private Integer order;

    public enum Format {
        Markdown,
        AsciiDoc
    }
}
