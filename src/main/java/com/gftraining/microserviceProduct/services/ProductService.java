package com.gftraining.microserviceProduct.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gftraining.microserviceProduct.model.ProductDTO;
import com.gftraining.microserviceProduct.model.ProductEntity;
import com.gftraining.microserviceProduct.repositories.ProductRepository;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;


@Service
public class ProductService {
    private ProductRepository productRepository;
    public ProductService(ProductRepository productRepository) {
    super();
        this.productRepository = productRepository;
    }

    public List<ProductEntity> allProducts() {
        List<ProductEntity> products = productRepository.findAll();
        for (ProductEntity product : products) {
            product.setFinalPrice(calculateFinalPrice(product.getPrice(),product.getCategory().getDiscount()));
        }
        return products;
    }

    public void deleteProductById(Long id) {
        productRepository.deleteById(id);
    }
    public ProductEntity getProductById(Long id) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product with id: "+id+" not found."));
        product.setFinalPrice(calculateFinalPrice(product.getPrice(),product.getCategory().getDiscount()));
        return product;
    }

    public List<ProductEntity> getProductByName(String name) {
        List<ProductEntity> products = productRepository.findAllByName(name);
        if (products.isEmpty()) throw new EntityNotFoundException("Products with name: "+name+" not found.");

        for (ProductEntity product: products){
            product.setFinalPrice(calculateFinalPrice(product.getPrice(),product.getCategory().getDiscount()));
        }
        return products;
    }
    public @NonNull Long saveProduct(ProductDTO productDTO){
        ProductEntity product = new ProductEntity();

        product.setName(productDTO.getName());
        product.setDescription(productDTO.getName());
        product.setPrice(productDTO.getPrice());
        product.setStock(productDTO.getStock());
        product.setCategory(productDTO.getCategory());

        return productRepository.save(product).getId();
    }
    public void updateProductsFromJson(String path) throws IOException {
        productRepository.deleteAll();
        ObjectMapper objectMapper = new ObjectMapper();
        List<ProductEntity> products = objectMapper.readValue(new File(path), new TypeReference<List<ProductEntity>>(){});
        productRepository.saveAll(products);

    }

    public void putProductById(ProductDTO newProduct, Long id) {
        productRepository.findById(id).map(product ->
        {
            product.setName(newProduct.getName());
            product.setId(id);
            product.setCategory(newProduct.getCategory());
            product.setDescription(newProduct.getDescription());
            product.setPrice(newProduct.getPrice());
            product.setStock(newProduct.getStock());
            return productRepository.save(product);
        });
    }

    public BigDecimal calculateFinalPrice(BigDecimal price, int discount){
        return price.subtract(price.multiply(BigDecimal.valueOf(discount)).divide(new BigDecimal("100")))
                .round(new MathContext(4, RoundingMode.HALF_UP));
    }
}
