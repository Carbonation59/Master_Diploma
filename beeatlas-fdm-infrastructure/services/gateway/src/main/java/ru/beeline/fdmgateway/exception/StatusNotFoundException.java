/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway.exception;

public class StatusNotFoundException extends Exception {
    public StatusNotFoundException(String message) {
        super(message);
    }
}
