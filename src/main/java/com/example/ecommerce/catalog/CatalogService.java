package com.example.ecommerce.catalog;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    public static final int DEFAULT_PAGE_SIZE = 12;

    /** Page sizes a shopper can choose; any other value falls back to {@link #DEFAULT_PAGE_SIZE}. */
    public static final List<Integer> PAGE_SIZE_OPTIONS = List.of(12, 24, 48, 96);

    private final ProductRepository productRepository;

    public CatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> listProducts(String categorySlug, String q, String pageParam, String sizeParam) {
        String slug = normalize(categorySlug);
        String term = normalize(q);
        int page = parsePage(pageParam);
        int size = parseSize(sizeParam);

        List<Specification<Product>> specs = new ArrayList<>();
        specs.add(ProductSpecifications.active());
        if (slug != null) {
            specs.add(ProductSpecifications.inCategory(slug));
        }
        if (term != null) {
            specs.add(ProductSpecifications.matches(term));
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return productRepository.findAll(Specification.allOf(specs), pageable);
    }

    public Product getActiveProduct(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /** Trims the value and returns null for null or blank input. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Null, non-numeric or negative values become page 0. */
    static int parsePage(String pageParam) {
        if (pageParam == null) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(pageParam.trim()), 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Null, non-numeric or unsupported values become {@link #DEFAULT_PAGE_SIZE}. */
    static int parseSize(String sizeParam) {
        if (sizeParam == null) {
            return DEFAULT_PAGE_SIZE;
        }
        try {
            int size = Integer.parseInt(sizeParam.trim());
            return PAGE_SIZE_OPTIONS.contains(size) ? size : DEFAULT_PAGE_SIZE;
        } catch (NumberFormatException e) {
            return DEFAULT_PAGE_SIZE;
        }
    }
}
