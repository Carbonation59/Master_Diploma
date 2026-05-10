package ru.beeline.architecting_graph.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.beeline.architecting_graph.client.AuthSSOClient;

@Configuration
@ConditionalOnProperty(
        name = "app.feature.use-doc-service",
        havingValue = "true",
        matchIfMissing = true
)
public class RabbitConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitConfig.class);

    @Value("${app.ambassador-auth}")
    private Boolean ambassadorAuth;

    @Value("${spring.rabbitmq.username}")
    private String userName;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Value("${spring.rabbitmq.virtual-host}")
    private String virtualHost;

    @Value("${queue.create-local-graph.name}")
    private String queueName;

    @Value("${spring.rabbitmq.template.exchange}")
    private String topicExchangeName;

    @Value("${spring.rabbitmq.template.routing-key}")
    private String routingName;

    @Value("${spring.rabbitmq.host}")
    private String connectFactoryName;

    @Autowired
    private AuthSSOClient authSSOClient;

    public RabbitConfig() {
        System.out.println("=== RabbitConfig constructor called! ===");
    }

    @Bean
    public Queue queue() {
        return new Queue(queueName, true);
    }

    @Bean
    DirectExchange directExchange(){
        return new DirectExchange(topicExchangeName);
    }

    @Bean
    Binding binding(Queue queue, DirectExchange directExchange){
        return BindingBuilder.bind(queue).to(directExchange).with(routingName);
    }


    @Bean
    public CachingConnectionFactory connectionFactory() {
        return ambassadorAuth ? createConnectionFactoryWithToken() : createConnectionFactoryWithPass();
    }

    public CachingConnectionFactory createConnectionFactoryWithPass() {
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory(connectFactoryName);
        cachingConnectionFactory.setUsername(userName);
        cachingConnectionFactory.setPassword(password);
        cachingConnectionFactory.setVirtualHost(virtualHost);
        return cachingConnectionFactory;
    }


    private CachingConnectionFactory createConnectionFactoryWithToken() {
        CachingConnectionFactory factory = new CachingConnectionFactory(connectFactoryName);
        factory.setUsername("");
        factory.setPassword(authSSOClient.getToken());
        factory.setVirtualHost(virtualHost);

        factory.addConnectionListener(new ConnectionListener() {
            @Override
            public void onCreate(Connection connection) {
                LOGGER.info("create connection and update token");
                factory.setPassword(authSSOClient.getToken());
            }

            @Override
            public void onClose(Connection connection) {
            }
        });
        return factory;
    }

    @Bean
    MessageConverter messageConverter() {
        return new SimpleMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}
