/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.beeline.fdmgateway.service.UserService;
import ru.beeline.fdmgateway.utils.eauth.EAuthHelper;
import ru.beeline.fdmgateway.utils.eauth.EAuthKey;

@RestController
public class ApplicationController {
    @Autowired
    UserService userService;

    @Value("${app.version}")
    private String appVersion;

    @Value("${app.name}")
    private String appName;


    @GetMapping("/")
    public String getData() {
        return "Welcome " + appName + " " + appVersion;
    }

    @GetMapping("/api/runtime/v1/eauthkey")
    public EAuthKey getEAuthKey() {
        return EAuthHelper.getEAuthKey();
    }

    @DeleteMapping("/cache")
    public ResponseEntity clearCache() {
        userService.removeFromCache();
        return new ResponseEntity(HttpStatus.OK);
    }

    @DeleteMapping("/cache/{login}")
    public ResponseEntity removeFromCacheByLogin(@PathVariable String login) {
        userService.removeFromCache(login);
        return new ResponseEntity(HttpStatus.OK);
    }
}
