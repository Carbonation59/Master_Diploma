/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model.ViewObjects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ElementView {

    private String id;
    private Integer x;
    private Integer y;

    public ElementView createWithId(String id) {
        ElementView elementView = new ElementView();
        elementView.setId(id);
        elementView.setX(0);
        elementView.setY(0);
        return elementView;
    }
}
