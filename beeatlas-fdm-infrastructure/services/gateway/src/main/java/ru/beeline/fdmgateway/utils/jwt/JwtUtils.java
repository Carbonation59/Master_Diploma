/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway.utils.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import ru.beeline.fdmgateway.utils.eauth.EAuthKey;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static ru.beeline.fdmgateway.utils.eauth.EAuthHelper.getEAuthKey;

@Slf4j
public class JwtUtils {
    public static Map<String, String> encodeJWT(String token) {
        try {
            String[] parts = token.split("\\.");
            Base64.Decoder decoder = Base64.getUrlDecoder();

            String payload = new String(decoder.decode(parts[1]));
            String jsonObject = JSONObject.escape(payload).replace("\\", "");

            ObjectMapper mapper = new ObjectMapper();

            // convert JSON string to Map
            Map<String, String> map = mapper.readValue(jsonObject, Map.class);

            return map;

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return null;
    }

    public static JwtUserData getUserData(String token) {
        Map<String, String> data = JwtUtils.encodeJWT(token.substring(token.indexOf(" ")));
        return data != null ? new JwtUserData(data) : null;
    }

    public static boolean isValid(String token) {
        EAuthKey jwk = getEAuthKey();
        if (jwk != null) {
            try {
                token = token.split(" ")[1];
                Base64.Decoder decoder = Base64.getUrlDecoder();
                BigInteger modulus = new BigInteger(1, decoder.decode(jwk.getN()));
                BigInteger exponent = new BigInteger(1, decoder.decode(jwk.getE()));

                RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
                PublicKey pubKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);

                Jwts
                        .parser().verifyWith(pubKey).build()
                        .parseSignedClaims(token);
                return true;
            } catch (Exception e) {
                log.error("Token validation failed: " + e.getMessage());
                return false;
            }
        } else {
            log.error("EAuthKey is null");
            return false;
        }
    }

    public static boolean isExpired(String token) {
        DecodedJWT decodedJWT = JWT.decode(token.substring(token.indexOf(" ") + 1));
        Date expiresAt = decodedJWT.getExpiresAt();
        boolean isExpired = expiresAt.before(new Date());
        if (isExpired) {
            log.error("Token is expired, expired date: " + expiresAt);
        }
        return isExpired;
    }
}