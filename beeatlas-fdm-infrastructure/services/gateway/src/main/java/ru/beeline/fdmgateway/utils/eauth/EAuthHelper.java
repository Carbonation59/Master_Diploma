/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway.utils.eauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import static ru.beeline.fdmgateway.utils.RestHelper.getRestTemplate;


@Slf4j
public class EAuthHelper {

    private static String keyUrl = "";
    private static EAuthKey eAuthKey;

    public static EAuthKey getAndSavePublicKey(String url) {
        keyUrl = url;
        try {
            final RestTemplate restTemplate = getRestTemplate();
            final String key = restTemplate.getForObject(url, String.class);
            if (key != null) {
                ObjectMapper mapper = new ObjectMapper();

                EAuthKeys keys = mapper.readValue(key, EAuthKeys.class);
                if (!keys.getKeys().isEmpty()) {
                    eAuthKey = keys.getKeys().get(0);
                    log.info("EAuth key сохранен: " + eAuthKey);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return eAuthKey;
    }

    public static EAuthKey getEAuthKey() {
        if (!keyUrl.isEmpty() && eAuthKey == null) {
            eAuthKey = getAndSavePublicKey(keyUrl);
        }
        return eAuthKey;
    }
}
