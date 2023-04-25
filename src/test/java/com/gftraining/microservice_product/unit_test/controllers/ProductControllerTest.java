package com.gftraining.microservice_product.unit_test.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.gftraining.microservice_product.configuration.FeatureFlagsConfig;
import com.gftraining.microservice_product.controllers.ProductController;
import com.gftraining.microservice_product.model.ProductDTO;
import com.gftraining.microservice_product.model.ProductEntity;
import com.gftraining.microservice_product.services.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(ProductController.class)
class ProductControllerTest {

    final List<ProductEntity> productList = Arrays.asList(
            new ProductEntity(1L, "Playmobil", "Juguetes", "juguetes de plástico", new BigDecimal("40.00"), 100)
            , new ProductEntity(2L, "Espaguetis", "Comida", "pasta italiana elaborada con harina de grano duro y agua", new BigDecimal("2.00"), 220)
    );
    final List<ProductEntity> productListSameName = Arrays.asList(
            new ProductEntity(1L, "Playmobil", "Juguetes", "juguetes de plástico", new BigDecimal("40.00"), 100),
            new ProductEntity(2L, "Playmobil", "Juguetes", "juguetes de plástico", new BigDecimal("40.00"), 100)
    );
    final ProductEntity productEntity = new ProductEntity(1L, "Pelota", "Juguetes", "pelota futbol", new BigDecimal("19.99"), 24);
    @Autowired
    private MockMvc mockmvc;
    @MockBean
    private ProductService productService;
    @MockBean
    private FeatureFlagsConfig featureFlag;

    public static String asJsonString(final Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Given a call in ProductService getAll, When perform the get request /products/getAll, Then return a list of Products")
    void testGetAll() throws Exception {
        given(productService.getAllProducts()).willReturn(productList);

        mockmvc.perform(get("/products"))
                .andExpect(content().json(new ObjectMapper().writeValueAsString(productList)))
                .andReturn();
    }

    @Test
    @DisplayName("Given a product name, When calling service to find products by name, Then a list of products with that name is returned")
    void getProductByName() throws Exception {
        given(productService.getProductByName("Playmobil")).willReturn(productListSameName);

        mockmvc.perform(get("/products/name/{name}", "Playmobil"))
                .andExpect(status().isOk())
                .andExpect(content().json(asJsonString(productListSameName)));
    }

    @Test
    @DisplayName("given a product id, when calling service to find products by id, then the product is returned")
    void getProductById() throws Exception {
        given(productService.getProductById(1L)).willReturn(productEntity);

        mockmvc.perform(get("/products/id/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(content().json(asJsonString(productEntity)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Given any long and a json productEntity, When perform the put request /products/{id} and callCart flag is disabled, " +
            "Then expect status is created and is equal to productEntity")
    void putProductById_CallCartDisabled(boolean flag) throws Exception {
        given(productService.getProductById(anyLong())).willReturn(productEntity);
        when(featureFlag.isCallCartEnabled()).thenReturn(flag);
        when(productService.patchCartProducts(any(),anyLong())).thenReturn(Mono.just("{\"cartsChanged\":1}"));

        mockmvc.perform(put("/products/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(productEntity)))
                .andExpect(status().isOk());

        assertThat(productService.getProductById(1L)).isEqualTo(productEntity);

        if (flag) verify(productService).patchCartProducts(any(),anyLong());
    }

    @Test
    @DisplayName("When calling the service with any Long, Then the product is returned")
    void updateStock() throws Exception {
        when(productService.getProductById(anyLong())).thenReturn(productEntity);
        doNothing().when(productService).updateStock(4, 1L);

        mockmvc.perform(put("/products/updateStock/{id}", 1L)
                        .param("id", "1").contentType(MediaType.APPLICATION_JSON)
                        .content("5"))
                .andExpect(status().isNoContent());

        verify(productService).updateStock(5, 1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Given an id, When perform the delete request /products/{id} and callcart and calluser flags are disabled, " +
            "Then verify if the service is called and has no content")
    void deleteProductById_CallCartDisabledAndCallUserDisabled(boolean flag) throws Exception {
        when(featureFlag.isCallCartEnabled()).thenReturn(flag);
        when(featureFlag.isCallUserEnabled()).thenReturn(flag);

        when(productService.deleteCartProducts(anyLong())).thenReturn(Mono.just("{\"cartsChanged\":1}"));
        when(productService.deleteUserProducts(anyLong())).thenReturn(Mono.just(HttpStatus.NO_CONTENT));

        mockmvc.perform(delete("/products/{id}", 1L))
                .andExpect(status().isOk());

        verify(productService).deleteProductById(anyLong());

        if(flag) {
            verify(productService).deleteUserProducts(anyLong());
            verify(productService).deleteCartProducts(anyLong());
        }
    }

    @Test
    @DisplayName("Given a Product, When calling service to add a new Product, Then the Product is created and is a Json")
    void addProduct() throws Exception {
        given(productService.saveProduct(any(ProductDTO.class))).willReturn(productEntity.getId());

        mockmvc.perform(post("/products")
                        .content(asJsonString(productEntity))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    @DisplayName("Given a json Product with price 0, When calling service to add a new Product, " +
            "Then price constraint greater than zero is thrown and catch block is called and returns response error")
    void addProduct_ReturnBadRequest() throws Exception {
        ProductEntity productPriceZero = new ProductEntity(1L, "Pelota", "Juguetes", "pelota futbol", new BigDecimal(0), 24);
        given(productService.saveProduct(any(ProductDTO.class))).willReturn(productEntity.getId());

        mockmvc.perform(post("/products")
                        .content(asJsonString(productPriceZero))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    @DisplayName("Given a path, When perform the post request /products/JSON_load, Then verify if the service is called and has been created")
    void updateProductsFromJson() throws Exception {
        //Put your own path
        mockmvc.perform(post("/products/JSON_load")
                        .param("path", "C:\\Files\\data_test.json"))
                .andExpect(status().isCreated());

        verify(productService).updateProductsFromJson("C:\\Files\\data_test.json");
    }
}
