package com.example.ecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProductRepositoryTest {

    private static final int ACTIVE_PRODUCTS = 19;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private List<String> search(String slug, String q) {
        List<Specification<Product>> specs = new ArrayList<>();
        specs.add(ProductSpecifications.active());
        if (slug != null) {
            specs.add(ProductSpecifications.inCategory(slug));
        }
        if (q != null) {
            specs.add(ProductSpecifications.matches(q));
        }
        Page<Product> page = productRepository.findAll(Specification.allOf(specs),
                PageRequest.of(0, 100, Sort.by("name").ascending()));
        return page.getContent().stream().map(Product::getSku).toList();
    }

    private static Product withSku(Page<Product> page, String sku) {
        return page.getContent().stream().filter(p -> p.getSku().equals(sku)).findFirst().orElseThrow();
    }

    @Test
    void activeShouldExcludeInactiveProduct() {
        List<String> skus = search(null, null);

        assertThat(skus).hasSize(ACTIVE_PRODUCTS).doesNotContain("CLOTH-OFF")
                .contains("ELEC-001", "BOOK-001", "HOME-LOW", "BOOK-OOS", "ELEC-LAST", "HOME-PCT");
    }

    @Test
    void shouldFilterByCategorySlug() {
        assertThat(search("books", null))
                .containsExactlyInAnyOrder("BOOK-001", "BOOK-002", "BOOK-003", "BOOK-004", "BOOK-OOS");
        assertThat(search("clothing", null))
                .containsExactlyInAnyOrder("CLOTH-001", "CLOTH-002", "CLOTH-003");
    }

    @Test
    void unknownCategoryShouldReturnNothing() {
        assertThat(search("no-such-category", null)).isEmpty();
    }

    @Test
    void shouldSearchNameCaseInsensitively() {
        assertThat(search(null, "LAPTOP")).containsExactly("ELEC-001");
        assertThat(search(null, "laptop")).containsExactly("ELEC-001");
        assertThat(search(null, "LaPtOp")).containsExactly("ELEC-001");
    }

    @Test
    void shouldSearchDescription() {
        // "stovetop" appears only in the Cast Iron Skillet description.
        assertThat(search(null, "Stovetop")).containsExactly("HOME-LOW");
    }

    @Test
    void shouldCombineCategoryAndSearch() {
        assertThat(search(null, "cotton")).contains("HOME-PCT", "CLOTH-002");
        assertThat(search("clothing", "cotton")).containsExactly("CLOTH-002");
        assertThat(search("home-kitchen", "cotton")).containsExactly("HOME-PCT");
    }

    @Test
    void searchShouldNotReturnInactiveProduct() {
        assertThat(search(null, "denim")).isEmpty();
    }

    @Test
    void percentShouldMatchLiterally() {
        assertThat(search(null, "%")).containsExactly("HOME-PCT");
        assertThat(search(null, "100%")).containsExactly("HOME-PCT");
        // As a wildcard "%cotton" would match HOME-PCT and CLOTH-002; literally it matches nothing.
        assertThat(search(null, "%cotton")).isEmpty();
    }

    @Test
    void underscoreShouldMatchLiterally() {
        // No seeded text contains "_".
        assertThat(search(null, "_")).isEmpty();
        // As a wildcard "t_a" would match "Tea"; literally it matches nothing.
        assertThat(search(null, "t_a")).isEmpty();
    }

    @Test
    void backslashAndExclamationShouldMatchLiterally() {
        assertThat(search(null, "\\")).isEmpty();
        assertThat(search(null, "!")).isEmpty();
        assertThat(search(null, "!%")).isEmpty();
    }

    @Test
    void catalogServiceShouldIgnoreBlankQueryAndPageFallback() {
        CatalogService service = new CatalogService(productRepository);

        Page<Product> blank = service.listProducts("  ", "   ", "abc", null);
        assertThat(blank.getNumber()).isZero();
        assertThat(blank.getTotalElements()).isEqualTo(ACTIVE_PRODUCTS);
        assertThat(blank.getContent()).hasSize(12);

        Page<Product> negative = service.listProducts(null, null, "-1", null);
        assertThat(negative.getNumber()).isZero();

        Page<Product> second = service.listProducts(null, null, "1", null);
        assertThat(second.getNumber()).isEqualTo(1);
        assertThat(second.getContent()).hasSize(ACTIVE_PRODUCTS - 12);
    }

    @Test
    void catalogServiceShouldUseChosenPageSizeAndFallBackToDefault() {
        CatalogService service = new CatalogService(productRepository);

        Page<Product> large = service.listProducts(null, null, null, "24");
        assertThat(large.getSize()).isEqualTo(24);
        assertThat(large.getContent()).hasSize(Math.min(ACTIVE_PRODUCTS, 24));

        assertThat(service.listProducts(null, null, null, "7").getSize()).isEqualTo(12);
        assertThat(service.listProducts(null, null, null, "abc").getSize()).isEqualTo(12);
        assertThat(service.listProducts(null, null, null, "-24").getSize()).isEqualTo(12);
    }

    @Test
    void listShouldBeSortedByNameWithCategoryFetched() {
        CatalogService service = new CatalogService(productRepository);

        Page<Product> page = service.listProducts(null, null, null, null);

        List<String> names = page.getContent().stream().map(Product::getName).toList();
        assertThat(names).isSorted();
        assertThat(names).contains("Aurora 4K Laptop");
        assertThat(page.getContent()).allMatch(p -> Hibernate.isInitialized(p.getCategory()));
        assertThat(withSku(page, "ELEC-001").getCategory().getName()).isEqualTo("Electronics");
    }

    @Test
    void findByIdAndActiveTrueShouldReturnActiveProductWithCategoryInitialized() {
        Product laptop = productRepository.findAll().stream()
                .filter(p -> p.getSku().equals("ELEC-001")).findFirst().orElseThrow();
        Long id = laptop.getId();
        // Make sure the category is not served from the persistence context by the findAll above.
        productRepository.flush();
        clearPersistenceContext();

        Product found = productRepository.findByIdAndActiveTrue(id).orElseThrow();

        assertThat(found.getName()).isEqualTo("Aurora 4K Laptop");
        assertThat(found.getPrice()).isEqualByComparingTo("1234.56");
        assertThat(Hibernate.isInitialized(found.getCategory())).isTrue();
        assertThat(found.getCategory().getSlug()).isEqualTo("electronics");
    }

    @Test
    void findByIdAndActiveTrueShouldExcludeInactiveProduct() {
        Long inactiveId = productRepository.findAll().stream()
                .filter(p -> p.getSku().equals("CLOTH-OFF")).findFirst().orElseThrow().getId();

        assertThat(productRepository.findByIdAndActiveTrue(inactiveId)).isEmpty();
        assertThat(productRepository.findByIdAndActiveTrue(999_999L)).isEmpty();
    }

    @Test
    void categoriesShouldBeOrderedByName() {
        assertThat(categoryRepository.findAllByOrderByNameAsc()).extracting(Category::getSlug)
                .containsExactly("books", "clothing", "electronics", "home-kitchen");
    }

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private void clearPersistenceContext() {
        entityManager.clear();
    }
}
