package ru.beeline.architecting_graph.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.beeline.architecting_graph.client.AuthSSOClient;


@Slf4j
@Component
@ConditionalOnProperty(
        name = "app.feature.use-doc-service",
        havingValue = "true",
        matchIfMissing = true
)
public class RabbitService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CachingConnectionFactory connectionFactory;

    @Autowired
    private AuthSSOClient authSSOClient;

    public void sendMessage(String queue, Object message) {
        try {
            if (!isConnected()) {
                refreshConnection();
            }

            rabbitTemplate.convertAndSend(queue, message, messagePostProcessor -> {
                messagePostProcessor.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return messagePostProcessor;
            });
        } catch (Exception e) {
            log.error("Error sending message: ", e);
        }
    }

    private boolean isConnected() {
        try {
            connectionFactory.createConnection().close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void refreshConnection() {
        try {
            connectionFactory.resetConnection();
            connectionFactory.setPassword(authSSOClient.getToken());
        } catch (Exception e) {
            log.error("Error refreshing connection: ", e);
        }
    }
}