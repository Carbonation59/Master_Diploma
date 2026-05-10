/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.fdmgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.beeline.fdmgateway.utils.PKBeanImpl;

@SpringBootApplication
public class FdmGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FdmGatewayApplication.class, args);
    }

    @Bean(initMethod="runAfterObjectCreated")
    public PKBeanImpl savePublicKey() {
        return new PKBeanImpl();
    }
}
