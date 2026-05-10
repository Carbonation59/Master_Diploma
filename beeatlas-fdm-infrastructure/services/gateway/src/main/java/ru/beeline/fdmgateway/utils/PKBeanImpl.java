/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static ru.beeline.fdmgateway.utils.eauth.EAuthHelper.getAndSavePublicKey;


@Component
public class PKBeanImpl {

    @Value("${jwks}")
    private String jwksUrl;

    @Value("${app.authentic-auth}")
    private Boolean authenticAuth;

    @Value("${app.authentic-auth-url}")
    private String authenticAuthUrl;

    public void runAfterObjectCreated() {
        if(authenticAuth){
            getAndSavePublicKey(authenticAuthUrl+ "/application/o/beeatlas/jwks/");
        } else {
            getAndSavePublicKey(jwksUrl);
        }
    }
}