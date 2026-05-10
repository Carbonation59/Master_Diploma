/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.service.graph;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

@Component
public class Neo4jSessionManager {

    private final Driver driver;

    private final ThreadLocal<Session> sessionThreadLocal = new ThreadLocal<>();

    public Neo4jSessionManager(Driver driver) {
        this.driver = driver;
    }

    private void createSession() {
        Session session = driver.session();
        sessionThreadLocal.set(session);
    }

    public Session getSession() {
        if(sessionThreadLocal.get() == null){
            createSession();
        }
        return sessionThreadLocal.get();
    }

    public void closeSession() {
        Session session = sessionThreadLocal.get();
        if (session != null) {
            session.close();
            sessionThreadLocal.remove();
        }
    }
}