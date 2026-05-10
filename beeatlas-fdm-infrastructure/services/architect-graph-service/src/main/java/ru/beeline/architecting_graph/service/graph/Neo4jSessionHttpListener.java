/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import org.springframework.stereotype.Component;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

@Component
public class Neo4jSessionHttpListener implements HttpSessionListener {

    private final Neo4jSessionManager sessionManager;

    public Neo4jSessionHttpListener(Neo4jSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void sessionCreated(HttpSessionEvent se) {
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        try {
            sessionManager.closeSession();
            System.out.println("Neo4j session closed on HTTP session destroy");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}