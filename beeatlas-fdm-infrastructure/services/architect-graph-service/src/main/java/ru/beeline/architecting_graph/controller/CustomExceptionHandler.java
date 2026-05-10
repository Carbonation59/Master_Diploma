/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.controller;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ru.beeline.architecting_graph.exception.DocumentForbiddenException;
import ru.beeline.architecting_graph.exception.DocumentServerException;
import ru.beeline.architecting_graph.exception.NotFoundException;
import ru.beeline.architecting_graph.exception.ValidationException;

@ControllerAdvice
@Slf4j
public class CustomExceptionHandler {
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleException(ValidationException e) {
        log.error(e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("400 BAD_REQUEST : " + e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleException(NotFoundException e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .header("content-type", MediaType.APPLICATION_JSON_VALUE)
                .body(e.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Object> handleException(ServiceUnavailableException e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("content-type", MediaType.APPLICATION_JSON_VALUE)
                .body(e.getMessage());
    }

    @ExceptionHandler(DocumentServerException.class)
    public ResponseEntity<Object> handleException(DocumentServerException e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("content-type", MediaType.APPLICATION_JSON_VALUE)
                .body(e.getMessage());
    }

    @ExceptionHandler(DocumentForbiddenException.class)
    public ResponseEntity<Object> handleException(DocumentForbiddenException e) {
        log.error(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .header("content-type", MediaType.APPLICATION_JSON_VALUE)
                .body(e.getMessage());
    }

}