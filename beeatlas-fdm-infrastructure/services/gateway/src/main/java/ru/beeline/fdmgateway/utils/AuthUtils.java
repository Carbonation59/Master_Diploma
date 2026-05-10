/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

@Slf4j
@Component
public class AuthUtils {

    public static String buildMessage(String method, String path, String body, String contentType, String nonce) throws NoSuchAlgorithmException {
        String md5Body = md5(body);
        return method + "\n" + path + "\n" + md5Body + "\n" + contentType + "\n" + nonce + "\n";
    }

    public static String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(messageDigest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute MD5", e);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();

        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String hmacSha256(String message, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] macData = sha256Hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(macData);
    }

    public static boolean validateAuthorization(String message, String apiSecret, String authorizationHeader) {
        try {
            String expectedSignature = hmacSha256(message, apiSecret);
            return Objects.equals(expectedSignature, authorizationHeader);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error during HMAC-SHA256 signature validation.");
            return false;
        }
    }
}
