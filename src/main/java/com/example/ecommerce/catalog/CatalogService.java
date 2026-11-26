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

    /** Page sizes a visitor may choose. Anything else falls back to {@link #DEFAULT_PAGE_SIZE}. */
    public static final List<Integer> PAGE_SIZES = List.of(12, 24, 48);

    public static final int DEFAULT_PAGE_SIZE = 12;

    private final ProductRepository productRepository;

    public CatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> listProducts(String categorySlug, String q, String pageParam, int size) {
        String slug = normalize(categorySlug);
        String term = normalize(q);
        int page = parsePage(pageParam);

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

    /**
     * Returns the requested page size if it is one of {@link #PAGE_SIZES}, otherwise the default.
     * The allowlist keeps clients from requesting arbitrarily large pages.
     */
    public static int parsePageSize(String sizeParam) {
        if (sizeParam == null) {
            return DEFAULT_PAGE_SIZE;
        }
        try {
            int size = Integer.parseInt(sizeParam.trim());
            return PAGE_SIZES.contains(size) ? size : DEFAULT_PAGE_SIZE;
        } catch (NumberFormatException e) {
            return DEFAULT_PAGE_SIZE;
        }
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
}
