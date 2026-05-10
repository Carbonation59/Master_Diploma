package ru.beeline.architecting_graph.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import ru.beeline.architecting_graph.dto.TaskCacheDTO;
import ru.beeline.architecting_graph.service.RabbitService;
import ru.beeline.architecting_graph.service.graph.GraphConstructionService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;


@Slf4j
@Component
@EnableRabbit
@ConditionalOnProperty(name = "app.feature.use-doc-service", havingValue = "true")
public class GraphConsumers {

    private final ObjectMapper objectMapper;
    private final RabbitService rabbitService;
    private final GraphConstructionService graphConstructionService;
    private final RedisTemplate<String, TaskCacheDTO> redisTemplate;

    public GraphConsumers(ObjectMapper objectMapper,
                          RabbitService rabbitService,
                          GraphConstructionService graphConstructionService,
                          @Autowired(required = false) RedisTemplate<String, TaskCacheDTO> redisTemplate) {
        this.objectMapper = objectMapper;
        this.rabbitService = rabbitService;
        this.graphConstructionService = graphConstructionService;
        this.redisTemplate = redisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationStart() {
        log.info("Startup: updating PROCESS -> ERROR in Redis cache");

        Set<String> keys = redisTemplate.keys("graph:*");
        if (keys != null) {
            for (String key : keys) {
                TaskCacheDTO dto = redisTemplate.opsForValue().get(key);
                if (dto != null && "PROCESS".equalsIgnoreCase(dto.getStatus())) {
                    dto.setStatus("ERROR");
                    redisTemplate.opsForValue().set(key, dto, Duration.ofHours(24));
                    log.info("Updated status to ERROR for key {}", key);
                }
            }
        }
    }

    @RabbitListener(queues = "${queue.create-local-graph.name}")
    public void resultLocalGraph(String message) {
        log.info("Received from create_local_graph: {}", message);
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            if (jsonNode.has("taskKey") && jsonNode.has("docId")) {
                String taskKey = jsonNode.get("taskKey").asText();
                Long docId = jsonNode.get("docId").asLong();

                String redisKey = "graph:" + taskKey;

                TaskCacheDTO existingDto = redisTemplate.opsForValue().get(redisKey);
                if (existingDto != null && "local".equalsIgnoreCase(existingDto.getType()) && taskKey.equals(existingDto.getTaskKey())) {
                    log.info("Record with taskKey={} and type=local already exists, skipping processing", taskKey);
                    return;
                }

                TaskCacheDTO newCache = TaskCacheDTO.builder()
                        .id(docId)
                        .taskKey(taskKey)
                        .status("QUEUE")
                        .type("local")
                        .cachedAt(LocalDateTime.now())
                        .build();

                redisTemplate.opsForValue().set(redisKey, newCache, Duration.ofHours(24));
                try {
                        rabbitService.sendMessage("build_local_graph", message);
                } catch (Exception e) {
                    log.error("Error send to build-global-graph", taskKey, docId, e);
                }
            } else {
                log.info("Message does not match the required format");
            }
        } catch (Exception e) {
            log.error("Internal server Error: {}", e.getMessage(), e);
            log.info(e.getMessage());
        }
    }

    @RabbitListener(queues = "${queue.build-local-graph.name}")
    public void buildLocalGraph(String message) {
        log.info("Received from build_local_graph: {}", message);
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            if (jsonNode.has("taskKey") && jsonNode.has("docId")) {
                String taskKey = jsonNode.get("taskKey").asText();
                Long docId = jsonNode.get("docId").asLong();

                String redisKey = "graph:" + taskKey;

                TaskCacheDTO existingDto = redisTemplate.opsForValue().get(redisKey);
                if (existingDto == null || !"local".equalsIgnoreCase(existingDto.getType())
                        || !existingDto.getStatus().equals("QUEUE")) {
                    log.info("Record with taskKey={} and type=local already exists, skipping processing", taskKey);
                    return;
                }

                TaskCacheDTO newCache = TaskCacheDTO.builder()
                        .id(docId)
                        .taskKey(taskKey)
                        .status("PROCESS")
                        .type("local")
                        .cachedAt(LocalDateTime.now())
                        .build();

                redisTemplate.opsForValue().set(redisKey, newCache, Duration.ofHours(24));
                try {
                    ResponseEntity<String> result = graphConstructionService.graphConstruct(docId, "Local");
                    if (result.getStatusCode().is2xxSuccessful()) {
                        newCache.setType("local");
                        newCache.setStatus("DONE");
                        redisTemplate.opsForValue().set(redisKey, newCache, Duration.ofHours(24));

                        ObjectNode item = objectMapper.createObjectNode();
                        item.put("taskKey", taskKey);
                        item.put("status", "DONE");
                        rabbitService.sendMessage("result_local_graph", objectMapper.writeValueAsString(item));
                    } else {
                        log.info("graph construct StatusCode is " + result.getStatusCode());
                        throw new RuntimeException(result.getBody());
                    }
                } catch (Exception e) {
                    log.error("Error building graph for taskKey={}, docId={}: {}", taskKey, docId, e);
                    log.info(e.getMessage());
                    newCache.setType("local");
                    newCache.setStatus("ERROR");
                    redisTemplate.opsForValue().set(redisKey, newCache, Duration.ofHours(24));

                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("taskKey", taskKey);
                    item.put("status", "ERROR");
                    rabbitService.sendMessage("result_local_graph", objectMapper.writeValueAsString(item));
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Документ не найден");
                }
            } else {
                log.error("Message does not match the required format");
            }
        } catch (Exception e) {
            log.error("Internal server Error: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${queue.create-global-graph.name}")
    public void createGlobalGraph(String message) {
        log.info("Received from create_global_graph: {}", message);
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            if (jsonNode.has("taskKey") && jsonNode.has("docId")) {
                String taskKey = jsonNode.get("taskKey").asText();
                Long docId = jsonNode.get("docId").asLong();

                String redisKey = "graph:" + taskKey;

                TaskCacheDTO existingDto = redisTemplate.opsForValue().get(redisKey);
                if (existingDto != null && "global".equalsIgnoreCase(existingDto.getType()) && taskKey.equals(existingDto.getTaskKey())) {
                    log.info("Record with taskKey={} and type=global already exists, skipping processing", taskKey);
                    return;
                }

                TaskCacheDTO newCache = TaskCacheDTO.builder()
                        .id(docId)
                        .taskKey(taskKey)
                        .status("QUEUE")
                        .type("global")
                        .cachedAt(LocalDateTime.now())
                        .build();

                redisTemplate.opsForValue().set(redisKey, newCache, Duration.ofHours(24));
                try {
                    rabbitService.sendMessage("build_global_graph", message);
                } catch (Exception e) {
                    log.error("Error sending create_global_graph for taskKey={}, docId={}: {}", taskKey, docId, e);

                }
            } else {
                log.error("Message does not match the required format");
            }
        } catch (Exception e) {
            log.error("Internal server Error: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${queue.build-global-graph.name}")
    public void buildGlobalGraph(String message) {
        log.info("Received from build_global_graph: {}", message);
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            if (jsonNode.has("taskKey") && jsonNode.has("docId")) {
                String taskKey = jsonNode.get("taskKey").asText();
                Long docId = jsonNode.get("docId").asLong();

                String redisKey = "graph:" + taskKey;

                TaskCacheDTO existingDto = redisTemplate.opsForValue().get(redisKey);
                if (existingDto == null
                        || !"global".equalsIgnoreCase(existingDto.getType())
                        || !existingDto.getStatus().equals("QUEUE")) {
                    log.info("Record with taskKey={} and type=global already exists, skipping processing", taskKey);
                    return;
                }

                TaskCacheDTO newCache = TaskCacheDTO.builder()
                        .id(docId)
                        .taskKey(taskKey)
                        .status("PROCESS")
                        .type("global")
                        .cachedAt(LocalDateTime.now())
                        .build();

                redisTemplate.opsForValue().set(redisKey, newCache, Duration.ofHours(24));
                try {
                    if (graphConstructionService.graphConstruct(docId, "Global").getStatusCode().is2xxSuccessful()) {

                        newCache.setType("global");
                        newCache.setStatus("DONE");
                        redisTemplate.opsForValue().set(redisKey, newCache, Duration.ofHours(24));

                        ObjectNode item = objectMapper.createObjectNode();
                        item.put("taskKey", taskKey);
                        item.put("status", "DONE");
                        rabbitService.sendMessage("result_global_graph", objectMapper.writeValueAsString(item));
                    } else {
                        throw new Exception("graph does not created");
                    }
                } catch (Exception e) {
                    log.error("Error building graph for taskKey={}, docId={}: {}", taskKey, docId, e);

                    newCache.setType("global");
                    newCache.setStatus("ERROR");
                    redisTemplate.opsForValue().set(redisKey, newCache, Duration.ofHours(24));

                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("taskKey", taskKey);
                    item.put("status", "ERROR");
                    rabbitService.sendMessage("result_global_graph", objectMapper.writeValueAsString(item));
                }
            } else {
                log.error("Message does not match the required format");
            }
        } catch (Exception e) {
            log.error("Internal server Error: {}", e.getMessage(), e);
        }
    }

}