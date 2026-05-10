/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import ru.beeline.architecting_graph.dto.TaskCacheDTO;

@Configuration
@ConditionalOnProperty(
        name = "app.feature.use-doc-service",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisConfig {

    public RedisConfig() {
        System.out.println("=== RedisConfig constructor called! ===");
    }

    @Bean
    public RedisTemplate<String, TaskCacheDTO> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, TaskCacheDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonRedisSerializer<TaskCacheDTO> serializer = new Jackson2JsonRedisSerializer<>(TaskCacheDTO.class);
        serializer.setObjectMapper(mapper);
        template.setValueSerializer(serializer);
        template.setKeySerializer(new StringRedisSerializer());

        return template;
    }
}