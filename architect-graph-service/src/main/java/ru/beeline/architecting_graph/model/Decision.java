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
public class Decision {

    private String id;
    private String date;
    private String status;
    private String title;
    private String content;
    private Format format;
    private String elementId;

    public enum Format {
        Markdown,
        AsciiDoc
    }
}
