/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkspaceConfiguration {

    private List<User> users;
    private Visibility visibility;
    private Scope scope;

    public enum Visibility {
        Public,
        Private
    }

    public enum Scope {
        Landscape,
        SoftwareSystem
    }
}
