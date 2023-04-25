package com.gftraining.microservice_product.unit_test.services;


import com.gftraining.microservice_product.configuration.CategoriesConfig;
import com.gftraining.microservice_product.configuration.ServicesUrl;
import com.gftraining.microservice_product.model.ProductDTO;
import com.gftraining.microservice_product.model.ProductEntity;
import com.gftraining.microservice_product.repositories.ProductRepository;
import com.gftraining.microservice_product.services.ProductService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    public static MockWebServer mockWebServer;
    @InjectMocks
    @Spy
    ProductService service;
    @Mock
    ProductRepository repository;
    @Mock
    CategoriesConfig categoriesConfig;
    @Mock
    ModelMapper modelMapper;
    final List<ProductEntity> productList = Arrays.asList(
            new ProductEntity(1L, "Playmobil", "Juguetes", "juguetes de plástico", new BigDecimal("40.00"), 100),
            new ProductEntity(2L, "Espaguetis", "Comida", "pasta italiana elaborada con harina de grano duro y agua", new BigDecimal("20.00"), 220)
    );
    final List<ProductEntity> productListSameName = Arrays.asList(
            new ProductEntity(1L, "Playmobil", "Juguetes", "juguetes de plástico", new BigDecimal("40.00"), 100),
            new ProductEntity(2L, "Playmobil", "Juguetes", "juguetes de plástico", new BigDecimal("40.00"), 100)
    );
    final ProductEntity productEntity = new ProductEntity(1L, "Pelota", "Juguetes", "pelota futbol", new BigDecimal("19.99"), 24);
    final ProductDTO productDTO = new ProductDTO(productEntity.getName(), productEntity.getCategory(), productEntity.getDescription(), productEntity.getPrice(), productEntity.getStock());
    final Map<String, Integer> cartsChanged = new HashMap<>() {{
        put("cartsChanged", 1);
    }};
    @Mock
    private ServicesUrl servicesUrl;

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("When calling getAll, Then a list of Products is returned")
    void testGetAll() {
        given(repository.findAll()).willReturn(productList);

        assertThat(service.getAllProducts()).isEqualTo(productList);
    }

    @Test
    @DisplayName("Given a product name, When finding products on the repository by name, Then a list of products with that name is returned")
    void getProductByName() {
        given(repository.findAllByName(anyString())).willReturn(productListSameName);

        assertThat(service.getProductByName("Playmobil")).isEqualTo(productListSameName);
    }

    @Test
    @DisplayName("Given a product id, When finding a product on the repository, Then the product is returned")
    void getProductById() {
        given(repository.findById(anyLong())).willReturn(Optional.of(productEntity));

        assertThat(service.getProductById(1L)).usingRecursiveComparison().isEqualTo(productEntity);
    }


    @Test
    @DisplayName("Given a Product, When the product is saved, Then verify if repository is called and if the id is 1")
    void putProductById() {
        given(categoriesConfig.getCategories()).willReturn(Map.of("Juguetes", 20));
        given(repository.findById(anyLong())).willReturn(Optional.of(productEntity));
        given(modelMapper.map(productDTO, ProductEntity.class)).willReturn(productEntity);

        service.putProductById(productDTO, 1L);
        verify(repository).save(any());
    }

    @Test
    @DisplayName("Given a Product with a wrong category, When the product is saved, Then throw an error")
    void putProductById_returnsCategoryError() {

        Assertions.assertThrows(EntityNotFoundException.class, () -> service.putProductById(productDTO, 1L));
    }

    @Test
    @DisplayName("Given a Product with a wrong id, When the product is saved, Then throw an error")
    void putProductById_returnsIdNotFoundError() {
        given(categoriesConfig.getCategories()).willReturn(Map.of("Juguetes", 20));

        Assertions.assertThrows(EntityNotFoundException.class, () -> service.putProductById(productDTO, 1L));
    }

    @Order(1)
    @Test
    @DisplayName("given a product id, when calling cart api to update products, then returns Ok and number of carts affected.")
    void patchCartProducts_returnCartsChanged() throws InterruptedException {
        when(servicesUrl.getCartUrl()).thenReturn("htpp://localhost:" + mockWebServer.getPort());

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(String.valueOf(new JSONObject(cartsChanged)))
                .addHeader("Content-Type", "application/json"));

        Mono<Object> cartsMono = service.patchCartProducts(productDTO, productEntity.getId());

        StepVerifier.create(cartsMono)
                .expectNext(cartsChanged)
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("PATCH");

    }

    @Test()
    @DisplayName("given a product id, when calling cart api to update product, then returns error 500.")
    void patchCartProducts_returnSError500() {
        when(servicesUrl.getCartUrl()).thenReturn("htpp://localhost:" + mockWebServer.getPort());

        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        Mono<Object> cartsMono = service.patchCartProducts(productDTO, 1L);

        StepVerifier.create(cartsMono)
                .expectError()
                .verify();

    }

    @Test
    @DisplayName("Given an id and an units, When calling updateStock, Then verify if repository is called")
    void updateStock() {
        given(repository.findById(1L)).willReturn(Optional.of(productEntity));

        service.updateStock(5, 1L);

        verify(repository, times(1)).findById(anyLong());
        verify(repository, times(1)).save(any());
    }

    @Test
    @DisplayName("Given an id and an units, When calling updateStock, Then verify if exception jumps")
    void updateStock_StockLessThan0() {
        given(repository.findById(1L)).willReturn(Optional.of(productEntity));

        Assertions.assertThrows(Exception.class, () -> service.updateStock(500, 1L));
    }

    @Test
    @DisplayName("given a product id, when delete product by id, then the product is deleted")
    void deleteProductById() {
        //given
        given(repository.findById(anyLong())).willReturn(Optional.of(productEntity));
        //when
        service.deleteProductById(1L);
        //then
        verify(repository).findById(anyLong());
        verify(repository).deleteById(anyLong());
    }

    @Test
    @DisplayName("given a product id, when delete product by id, then the product is not found")
    void deleteProductById_NotFoundException() {
        Assertions.assertThrows(EntityNotFoundException.class, () -> service.deleteProductById(9999L));
    }

    @Test
    @DisplayName("given a product id, when calling cart api to delete product, then returns Ok and number of carts affected.")
    void deleteCartProducts_returnCartsChanged() throws InterruptedException {
        Long productId = 7L;
        when(servicesUrl.getCartUrl()).thenReturn("htpp://localhost:" + mockWebServer.getPort());

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(String.valueOf(new JSONObject(cartsChanged)))
                .addHeader("Content-Type", "application/json"));

        Mono<Object> cartsMono = service.deleteCartProducts(productId);

        StepVerifier.create(cartsMono)
                .expectNext(cartsChanged)
                .verifyComplete();
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
    }

    @Test()
    @DisplayName("given a product id, when calling cart api to delete product, then returns error 500.")
    void deleteCartProducts_returnSError500() {
        Long productId = 7L;
        when(servicesUrl.getCartUrl()).thenReturn("htpp://localhost:" + mockWebServer.getPort());

        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        Mono<Object> cartsMono = service.deleteCartProducts(productId);

        StepVerifier.create(cartsMono)
                .expectError()
                .verify();
    }

    @Test
    @DisplayName("given a product id, when calling user api to delete favorite product, then returns 204 No Content.")
    void deleteUserProducts_returns204NoContent() throws InterruptedException {
        Long productId = 7L;
        when(servicesUrl.getUserUrl()).thenReturn("htpp://localhost:" + mockWebServer.getPort());

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(204));

        Mono<HttpStatus> userDeleteMono = service.deleteUserProducts(productId);

        StepVerifier.create(userDeleteMono)
                .expectComplete()
                .verify();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
    }

    @Test()
    @DisplayName("given a product id, when calling user api to delete product, then returns error 500.")
    void deleteUserProducts_returnsError500() {
        Long productId = 7L;
        when(servicesUrl.getUserUrl()).thenReturn("htpp://localhost:" + mockWebServer.getPort());

        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        Mono<HttpStatus> userDeleteMono = service.deleteUserProducts(productId);

        StepVerifier.create(userDeleteMono)
                .expectError()
                .verify();

    }

    @Test
    @DisplayName("Given a product id, When finding a product on the repository, Then the product is returned")
    void saveProduct() {
        given(categoriesConfig.getCategories()).willReturn(Map.of("Juguetes", 20));
        given(modelMapper.map(productDTO, ProductEntity.class)).willReturn(productEntity);
        given(repository.save(any())).willReturn(productEntity);
        Long id = service.saveProduct(productDTO);

        verify(repository).save(any());
        assertThat(id).isEqualTo(1L);
    }

    @Test
    @DisplayName("Given a Product with a wrong category, When the product is saved, Then throw an error")
    void saveProduct_returnsCategoryError() {
        Assertions.assertThrows(EntityNotFoundException.class, () -> service.saveProduct(productDTO));
    }

    @Test
    @DisplayName("Given a path, When calling updateProductsFromJson, Then verify if repository is called")
    void updateDatabase() throws IOException {
        //Put your own path
        service.updateProductsFromJson("C:\\Files\\data.json");

        verify(repository).deleteAll();
        verify(repository).saveAll(any());
    }
}
