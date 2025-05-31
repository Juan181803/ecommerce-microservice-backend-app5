package com.e2e.tests.util;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.POST;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class TestRestFacade {

    private final RestTemplate restTemplate;

    @Autowired
    public TestRestFacade(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public <T> ResponseEntity<T> post(String url, Object body, Class<T> responseType) {
        final var response = restTemplate.exchange(
                url,
                POST,
                new HttpEntity<>(body),
                responseType
        );
        assertTrue(
                response.getStatusCode().is2xxSuccessful(),
                "Unexpected status code: " + response.getStatusCode()
        );
        return response;
    }

    public <T> ResponseEntity<T> get(String url, Class<T> responseType) {
        final var response = restTemplate.getForEntity(
                url,
                responseType
        );
        assertTrue(
                response.getStatusCode().is2xxSuccessful(),
                "Unexpected status code: " + response.getStatusCode()
        );
        return response;
    }
} 