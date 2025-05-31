package com.e2e.tests.order;

import com.e2e.tests.util.E2ESuite;
import com.e2e.tests.util.TestRestFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;


import static org.junit.jupiter.api.Assertions.assertTrue;


@SpringBootTest(classes = E2ESuite.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class OrderServiceE2ETest extends E2ESuite {

    @Autowired
    private TestRestFacade restFacade;

    @Value("${order.service.url}")
    private String orderServiceUrl;

    @Test
    void shouldGetOrderById() {
        int orderId = 2;
        ResponseEntity<String> response = restFacade.get(
                orderServiceUrl + "/order-service/api/orders/" + orderId,
                String.class);
        System.out.println("Response: " + response.getBody());
        System.out.println("Status Code: " + response.getStatusCode());
        assertTrue(response.getStatusCode().is2xxSuccessful(), "Unexpected status code: " + response.getStatusCode());
    }
}