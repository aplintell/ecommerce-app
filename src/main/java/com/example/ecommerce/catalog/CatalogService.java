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

    /** Default number of products per page. */
    public static final int PAGE_SIZE = 12;

    /** Step used by the "fewer / more per page" control. */
    public static final int PAGE_SIZE_STEP = 4;
    public static final int MIN_PAGE_SIZE = 4;
    public static final int MAX_PAGE_SIZE = 24;

    private final ProductRepository productRepository;

    public CatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> listProducts(String categorySlug, String q, String pageParam) {
        return listProducts(categorySlug, q, pageParam, PAGE_SIZE);
    }

    public Page<Product> listProducts(String categorySlug, String q, String pageParam, int pageSize) {
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
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("name").ascending());
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

    /**
     * Null, non-numeric, out-of-range or off-step values become {@link #PAGE_SIZE}.
     * Valid sizes are {@link #MIN_PAGE_SIZE}..{@link #MAX_PAGE_SIZE} in steps of {@link #PAGE_SIZE_STEP}.
     */
    public static int parsePageSize(String sizeParam) {
        if (sizeParam == null) {
            return PAGE_SIZE;
        }
        try {
            int size = Integer.parseInt(sizeParam.trim());
            boolean valid = size >= MIN_PAGE_SIZE && size <= MAX_PAGE_SIZE && size % PAGE_SIZE_STEP == 0;
            return valid ? size : PAGE_SIZE;
        } catch (NumberFormatException e) {
            return PAGE_SIZE;
        }
    }
}
