/*
 * Copyright (c) 2024 PJSC VimpelCom
 */

package ru.beeline.architecting_graph.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.beeline.architecting_graph.dto.ProductInfoShortDTO;
import ru.beeline.architecting_graph.dto.ProductInfraSearchDTO;
import ru.beeline.architecting_graph.dto.product.TcDTO;
import ru.beeline.architecting_graph.dto.search.OperationDeploymentNodeSearchDTO;

import java.util.List;


@Slf4j
@Service
public class ProductClient {

    RestTemplate restTemplate;
    private final String productServerUrl;

    public ProductClient(@Value("${integration.product-server-url}") String productServerUrl,
                         RestTemplate restTemplate) {
        this.productServerUrl = productServerUrl;
        this.restTemplate = restTemplate;
    }

    public List<ProductInfoShortDTO> getAllProductsInfo() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("Request to Product ServerUrl: GET /api/v1/product/info");
            ResponseEntity<List<ProductInfoShortDTO>> response = restTemplate.exchange(
                    productServerUrl + "/api/v1/product/info",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<ProductInfoShortDTO>>() {}
            );
            log.info("Response from Product ServerUrl: " + response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Product info endpoint not found: " + e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Exception calling product service: " + e.getMessage(), e);
            return List.of();
        }
    }

    public List<ProductInfraSearchDTO> getProductInfraByVimIp(String search) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String url = String.format("%s/api/v1/product/infra/search?parameter=vimIp&value=%s", productServerUrl, search);

            log.info("Request to Product ServerUrl: GET " + url);

            ResponseEntity<List<ProductInfraSearchDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<ProductInfraSearchDTO>>() {}
            );
            log.info("Response from Product ServerUrl: " + response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Product infra search endpoint not found: " + e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Exception calling product infra search service: " + e.getMessage(), e);
            return List.of();
        }
    }

    public OperationDeploymentNodeSearchDTO getOperations(String path, String type) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            path = path.replace("{", "%7B").replace("}", "%7D");

            String url = UriComponentsBuilder
                    .fromHttpUrl(productServerUrl + "/api/v1/operation")
                    .queryParam("path", path)
                    .build()
                    .toUriString();

            if(type != null){
                url = url + "&type=" + type;
            }
            log.info("Request to Product ServerUrl: GET " + url);

            ResponseEntity<OperationDeploymentNodeSearchDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    OperationDeploymentNodeSearchDTO.class
            );
            log.info("Response from Product ServerUrl: " + response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("operation search endpoint not found: " + e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Exception calling operation search: " + e.getMessage(), e);
            return null;
        }
    }

    public List<TcDTO> getTechCapabilitiesByContainerProduct(String alias, List<String> containers) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(productServerUrl + "/api/v1/product/implemented/container/tech-capability");
            if (alias != null) {
                builder.queryParam("alias", alias);
            }
            if (containers != null && !containers.isEmpty()) {
                builder.queryParam("containers", containers);
            }

            String url = builder.build().toUriString();

            log.info("Request to Product ServerUrl: GET " + url);

            ResponseEntity<List<TcDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<TcDTO>>() {}
            );
            log.info("Response from Product ServerUrl: " + response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Tech capability endpoint not found: " + e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Exception calling tech capability service: " + e.getMessage(), e);
            return List.of();
        }
    }
}
