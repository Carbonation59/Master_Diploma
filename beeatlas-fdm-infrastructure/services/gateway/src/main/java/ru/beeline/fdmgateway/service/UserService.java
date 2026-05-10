/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.beeline.fdmgateway.client.UserClient;
import ru.beeline.fdmgateway.dto.UserInfoDTO;

import java.util.Date;
import java.util.Hashtable;

@Slf4j
@Service
public class UserService {
    private final static Hashtable<String, UserInfoDTO> userInfoCache = new Hashtable<>();
    private final UserClient userClient;
    private final Long cacheExpiration;
    private Date lastInvalidate = new Date();

    public UserService(UserClient userClient,
                       @Value("${spring.cache.expiration}") Long cacheExpiration) {
        this.userClient = userClient;
        this.cacheExpiration = cacheExpiration;
    }

    public void removeFromCache() {
        userInfoCache.clear();
        lastInvalidate = new Date();
    }

    public void removeFromCache(String login) {
        userInfoCache.remove(login.toLowerCase());
    }

    public UserInfoDTO getUserInfo(String email, String fullName, String idExt) {
        String login = email.substring(0, email.indexOf("@")).toLowerCase();

        if (isExpired()) {
            userInfoCache.clear();
            lastInvalidate = new Date();
        }

        if (!userInfoCache.containsKey(login)
                || userInfoCache.get(login) == null
                || userInfoCache.get(login).getId() == null
                || userInfoCache.get(login).getPermissions() == null
                || userInfoCache.get(login).getPermissions().isEmpty()) {
            userInfoCache.put(login, userClient.getUserInfo(email, fullName, idExt));
        }
        return userInfoCache.get(login);
    }

    private boolean isExpired() {
        return new Date().getTime() > lastInvalidate.getTime() + cacheExpiration;
    }
}
