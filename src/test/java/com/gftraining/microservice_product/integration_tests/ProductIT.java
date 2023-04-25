package com.gftraining.microservice_product.integration_tests;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.gftraining.microservice_product.model.ProductDTO;
import com.gftraining.microservice_product.services.ProductService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.ArrayList;

import static com.gftraining.microservice_product.integration_tests.ITConfig.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/data-test.sql", executionPhase = BEFORE_TEST_METHOD)
class ProductIT {
    private static WireMockServer wireMockServer;

    @Autowired
    MockMvc mockmvc;
    @Autowired
    ProductService service;
    
    final ProductDTO productDTO = new ProductDTO("Pelota", "Juguetes", "pelota de futbol", new BigDecimal("19.99"), 24);
    final ProductDTO badProductDTO = new ProductDTO("S", "0", "S", new BigDecimal(0), 10);

    @Test
    @DisplayName("When perform get request /products/getAll, Then is expected to have status of 200, be an ArrayList, be a Json and have size 13")
    void testGetAll() throws Exception {
        mockmvc.perform(MockMvcRequestBuilders.get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", isA(ArrayList.class)))
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.*", hasSize(13)))
                .andExpect(content().string(matchesJsonSchemaInClasspath(PRODUCT_ARRAY_SCHEMA)));
    }

    @Test
    @DisplayName("Given an id, When perform get request /products/id/{id}, Then is expected to have status of 200, be a Json and have {id: 1, name: Wonder, stock: 90}")
    void getProductById() throws Exception {
        mockmvc.perform(get("/products/id/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json("{id: 1, name: Wonder, stock: 90}"))
                .andExpect(content().string(matchesJsonSchemaInClasspath(PRODUCT_SCHEMA)));
    }

    @Test
    @DisplayName("Given an id, When perform get request /products/id/{id}, Then is expected to have status of 404")
    void getProductById_NotFoundException() throws Exception {
        mockmvc.perform(get("/products/id/{id}", 200))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string(matchesJsonSchemaInClasspath(ERROR_SCHEMA)));
    }

    @Test
    @DisplayName("Given a name, When perform get request /products/name/{name}, Then is expected to have status of 404, be a Json and have size 2")
    void getProductByName() throws Exception {
        mockmvc.perform(get("/products/name/{name}", "Los Surcos del Azar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(content().string(matchesJsonSchemaInClasspath(PRODUCT_ARRAY_SCHEMA)));
    
    }

    @Test
    @DisplayName("Given a name, When perform get request /products/name/{name}, Then is expected to have status of 404")
    void getProductByName_NotFoundException() throws Exception {
        mockmvc.perform(get("/products/name/{name}", "Pepe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string(matchesJsonSchemaInClasspath(ERROR_SCHEMA)));

    }

    @Test
    @DisplayName("Given an id, When perform put request /products/{id}, Then is expected to have status of 201")
    void putProductById() throws Exception {
        mockmvc.perform(put("/products/{id}", 1).contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(productDTO)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given a bad id, When perform put request /products/{id}, Then is expected to have status of 404")
    void putProductById_NotFoundException() throws Exception {
        mockmvc.perform(put("/products/{id}", 200).contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(productDTO)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(matchesJsonSchemaInClasspath(ERROR_SCHEMA)));
    }

    @Test
    @DisplayName("Given a bad Product, When perform put request /products/{id}, Then is expected to have status of 400")
    void putProductById_BadRequest() throws Exception {
        mockmvc.perform(put("/products/{id}", 1).contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(badProductDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(matchesJsonSchemaInClasspath(BAD_REQUEST_ERROR_SCHEMA)));
    }

    @Test
    @DisplayName("When retrying a patch call, then return 200 OK,")
    void putProductById_CartCallRetry() throws Exception {
        wireMockServerSetPort(8080);
        wireMockServer.stubFor(
                patch(urlEqualTo("/products/7")).inScenario("testing retires")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("OK response")
        );

        wireMockServer.stubFor(
                patch(urlEqualTo("/products/7")).inScenario("testing retires")
                        .whenScenarioStateIs("OK response")
                        .willReturn(aResponse().withStatus(200))
        );

        Mono<Object> cartsMono = service.patchCartProducts(productDTO, 7L);

        StepVerifier.create(cartsMono)
                .expectComplete()
                .verify();

        verify(2, patchRequestedFor(urlPathEqualTo("/products/7")));
        wireMockServerStop();
    }

    @Test
    @DisplayName("When retrying a delete call, then return 200 OK,")
    void deleteProductById_CartCallRetry() {
        wireMockServerSetPort(8080);
        wireMockServer.stubFor(
                delete(urlEqualTo("/products/7")).inScenario("testing retires")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("OK response")
        );

        wireMockServer.stubFor(
                delete(urlEqualTo("/products/7")).inScenario("testing retires")
                        .whenScenarioStateIs("OK response")
                        .willReturn(aResponse().withStatus(200))
        );

        Mono<Object> cartsMono = service.deleteCartProducts(7L);

        StepVerifier.create(cartsMono)
                .expectComplete()
                .verify();

        verify(2, deleteRequestedFor(urlPathEqualTo("/products/7")));
        wireMockServerStop();
    }

    @Test
    @DisplayName("When retrying a delete call, then return 204 OK,")
    void deleteProductById_UserCallRetry() {
        wireMockServerSetPort(8082);
        wireMockServer.stubFor(
                delete(urlEqualTo("/favorite/product/7")).inScenario("testing retires")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("OK response")
        );

        wireMockServer.stubFor(
                delete(urlEqualTo("/favorite/product/7")).inScenario("testing retires")
                        .whenScenarioStateIs("OK response")
                        .willReturn(aResponse().withStatus(204))
        );

        Mono<HttpStatus> usersMono = service.deleteUserProducts(7L);

        StepVerifier.create(usersMono)
                .expectComplete()
                .verify();

        verify(2, deleteRequestedFor(urlPathEqualTo("/favorite/product/7")));
        wireMockServerStop();
    }

    @Test
    @DisplayName("Given a path, When perform post request /products/JSON_load, Then is expected to have status of 201")
    void updateProductsFromJson() throws Exception {
        //Put your own path
        mockmvc.perform(MockMvcRequestBuilders.post("/products/JSON_load")
                        .param("path", "C:\\Files\\data.json"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Given an id and an integer, When perform put request /products/updateStock/{id}, Then is expected to have status of 200")
    void UpdateStock() throws Exception {
        mockmvc.perform(put("/products/updateStock/{id}", 7)
                        .content(asJsonString("10"))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Given an id and a String, When perform post request /products/updateStock/{id}, Then is expected to have status of 404")
    void UpdateStock_BadRequestException() throws Exception {
        mockmvc.perform(put("/products/updateStock/{id}", 7)
                        .content(asJsonString("asdv"))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given a Product, When perform post request /products, Then is expected to have status of 201, be a Json and have {\"id\":14,\"message\":\"DDBB updated\",\"status\":201}")
    void addNewProduct() throws Exception {
        mockmvc.perform(MockMvcRequestBuilders.post("/products")
                        .content(asJsonString(productDTO))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string("{\"id\":14,\"message\":\"DDBB updated\",\"status\":201}"));
    }

    @Test
    @DisplayName("Given a bad Product, When perform post request /products, Then is expected to have status of 400")
    void addNewProduct_BadRequest() throws Exception {
        mockmvc.perform(MockMvcRequestBuilders.post("/products")
                        .content(asJsonString(badProductDTO))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string(matchesJsonSchemaInClasspath(BAD_REQUEST_ERROR_SCHEMA)));
    }

    public static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void wireMockServerSetPort(int port) {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port));
        wireMockServer.start();

        WireMock.configureFor("localhost", port);
    }


    static void wireMockServerStop() {
        wireMockServer.stop();
    }
}
