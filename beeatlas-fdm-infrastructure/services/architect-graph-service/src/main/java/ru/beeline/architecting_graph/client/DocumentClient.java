package ru.beeline.architecting_graph.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.beeline.architecting_graph.exception.DocumentForbiddenException;
import ru.beeline.architecting_graph.exception.DocumentServerException;
import ru.beeline.architecting_graph.exception.NotFoundException;
import ru.beeline.architecting_graph.exception.ValidationException;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class DocumentClient {

    RestTemplate restTemplate;
    private final String documentServerUrl;

    public DocumentClient(@Value("${spring.services.documents.url}") String documentServerUrl,
                          RestTemplate restTemplate) {
        this.documentServerUrl = documentServerUrl;
        this.restTemplate = restTemplate;
    }

    public String getDocument(Long docId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    documentServerUrl + "/api/v1/documents/" + docId,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {
                    }
            );
            if (response.getBody() == null) {
                throw new ValidationException("Документ пуст");
            }
            return new String(response.getBody(), StandardCharsets.UTF_8);
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            log.error("Ошибка при загрузке документа: ", e);
            throw new DocumentServerException("Ошибка при вызове документа: " + e.getStatusCode());
        } catch (HttpClientErrorException.Forbidden e) {
            log.error("Документ недоступен (403 Forbidden): ", e);
            throw new DocumentForbiddenException("Доступ к документу запрещён: " + e.getStatusCode());
        } catch (HttpClientErrorException.NotFound e) {
            log.error("Запись с данным id не найдена: ", e);
            throw new NotFoundException("Запись с данным id не найдена");
        } catch (RestClientException e) {
            throw new DocumentServerException("Ошибка при вызове документа");
        }
    }
}
