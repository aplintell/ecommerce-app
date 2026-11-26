package com.example.ecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.ecommerce.support.SeedData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void rootShouldRedirectToProducts() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/products"));
    }

    @Test
    void listShouldShowFirstTwelveActiveProductsSortedByName() throws Exception {
        String html = body(get("/products"));

        assertThat(html).contains("100% Cotton Tea Towel", "Aurora 4K Laptop", "Merino Wool Sweater");
        // 13th product and later are on page 2.
        assertThat(html).doesNotContain("Mystery at Harbor Point", "Wireless Ergonomic Mouse");
        assertThat(html).doesNotContain("Discontinued Denim Jacket");
        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(12);
        assertThat(html.indexOf("Aurora 4K Laptop")).isLessThan(html.indexOf("Bamboo Cutting Board"));
        assertThat(html.indexOf("Bamboo Cutting Board")).isLessThan(html.indexOf("Merino Wool Sweater"));
    }

    @Test
    void listShouldRenderExactMoneyFormatImageCategoryStockAndDetailLink() throws Exception {
        String html = body(get("/products"));
        long laptopId = SeedData.idOf(jdbc, "ELEC-001");

        assertThat(html).contains("$1,234.56");
        assertThat(html).contains("/images/products/placeholder.svg");
        assertThat(html).contains("href=\"/products/" + laptopId + "\"");
        assertThat(html).contains(">Electronics<", ">Books<", ">Home &amp; Kitchen<");
        assertThat(html).contains("In stock");
        assertThat(html).contains("Page 1 of 2");
    }

    @Test
    void listShouldShowOutOfStockLabel() throws Exception {
        String html = body(get("/products").param("q", "poetry"));

        assertThat(html).contains("Rare Poetry Collection", "Out of stock", "$9.99");
    }

    @Test
    void categoryFilterShouldShowOnlyThatCategoryAndHighlightIt() throws Exception {
        String html = body(get("/products").param("category", "electronics"));

        assertThat(html).contains("Aurora 4K Laptop", "Studio Monitor Headphones", "USB-C Charging Hub");
        assertThat(html).doesNotContain("Clean Architecture Handbook", "Cast Iron Skillet", "Merino Wool Sweater");

        Matcher nav = Pattern.compile("<a[^>]*>Electronics</a>").matcher(html);
        assertThat(nav.find()).as("Electronics nav link present").isTrue();
        assertThat(nav.group()).contains("active").contains("aria-current=\"page\"");

        Matcher books = Pattern.compile("<a[^>]*>Books</a>").matcher(html);
        assertThat(books.find()).isTrue();
        assertThat(books.group()).doesNotContain("active");
    }

    @Test
    void unknownCategoryShouldShowNoProductsFoundWith200() throws Exception {
        String html = body(get("/products").param("category", "no-such-thing"));

        assertThat(html).contains("No products found");
        assertThat(html).doesNotContain("class=\"product-card\"");
    }

    @Test
    void searchShouldMatchNameCaseInsensitivelyAndKeepTerm() throws Exception {
        String html = body(get("/products").param("q", "LAPTOP"));

        assertThat(html).contains("Aurora 4K Laptop");
        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(1);
        assertThat(html).containsPattern("<input[^>]*name=\"q\"[^>]*value=\"LAPTOP\"");
    }

    @Test
    void searchShouldMatchDescription() throws Exception {
        String html = body(get("/products").param("q", "stovetop"));

        assertThat(html).contains("Cast Iron Skillet");
        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(1);
    }

    @Test
    void searchWithPercentShouldMatchLiterally() throws Exception {
        String html = body(get("/products").param("q", "%"));

        assertThat(html).contains("100% Cotton Tea Towel");
        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(1);
    }

    @Test
    void searchWithUnderscoreShouldMatchLiterally() throws Exception {
        String html = body(get("/products").param("q", "_"));

        assertThat(html).contains("No products found");
    }

    @Test
    void searchAndCategoryShouldCombine() throws Exception {
        String html = body(get("/products").param("q", "cotton").param("category", "clothing"));

        assertThat(html).contains("Organic Cotton T-Shirt");
        assertThat(html).doesNotContain("100% Cotton Tea Towel");
        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(1);
    }

    @Test
    void blankSearchShouldBeIgnored() throws Exception {
        String html = body(get("/products").param("q", "   "));

        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(12);
        assertThat(html).contains("Page 1 of 2");
    }

    @Test
    void pageOneShouldShowSecondPage() throws Exception {
        String html = body(get("/products").param("page", "1"));

        assertThat(html).contains("Mystery at Harbor Point", "Wireless Ergonomic Mouse");
        assertThat(html).doesNotContain("Aurora 4K Laptop");
        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(7);
        assertThat(html).contains("Page 2 of 2");
    }

    @Test
    void paginationLinksShouldKeepSearchTerm() throws Exception {
        // "e" matches more than 12 active products.
        String html = body(get("/products").param("q", "e"));

        Matcher next = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>Next").matcher(html);
        assertThat(next.find()).as("next link present").isTrue();
        assertThat(next.group(1)).contains("q=e").contains("page=1");

        String page2 = body(get("/products").param("q", "e").param("page", "1"));
        Matcher prev = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>&laquo; Previous").matcher(page2);
        assertThat(prev.find()).as("previous link present").isTrue();
        assertThat(prev.group(1)).contains("q=e").contains("page=0");
    }

    @Test
    void pageLinksShouldKeepCategoryAndSearchTerm() throws Exception {
        // Page beyond the last: empty result with a link back to page 0 that keeps q and category.
        String html = body(get("/products").param("q", "o").param("category", "electronics").param("page", "5"));

        assertThat(html).contains("No products found");
        Matcher back = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>Back to first page").matcher(html);
        assertThat(back.find()).isTrue();
        assertThat(back.group(1)).contains("q=o").contains("category=electronics").contains("page=0");
    }

    @Test
    void nonNumericPageShouldFallBackToFirstPage() throws Exception {
        String html = body(get("/products").param("page", "abc"));

        assertThat(html).contains("Aurora 4K Laptop").doesNotContain("Wireless Ergonomic Mouse");
        assertThat(html).contains("Page 1 of 2");
    }

    @Test
    void negativePageShouldFallBackToFirstPage() throws Exception {
        String html = body(get("/products").param("page", "-1"));

        assertThat(html).contains("Aurora 4K Laptop").doesNotContain("Wireless Ergonomic Mouse");
        assertThat(html).contains("Page 1 of 2");
    }

    // The seed has 19 active products and 5 active books.
    @ParameterizedTest(name = "size={0}, category={1}: {2} cards, {3} pages")
    @CsvSource({
            "4,   ,      4,  5",
            "8,   ,      8,  3",
            "20,  ,      19, 1",
            "19,  ,      19, 1",
            "18,  ,      18, 2",
            "8,   books, 5,  1",
            "5,   books, 5,  1",
            "4,   books, 4,  2",
            "1,   ,      1,  19"
    })
    void sizeShouldLimitProductsAndRecalculatePages(String size, String category, int cards, int pages)
            throws Exception {
        MockHttpServletRequestBuilder request = get("/products").param("size", size);
        if (category != null) {
            request.param("category", category);
        }

        String html = body(request);

        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(cards);
        if (pages > 1) {
            assertThat(html).contains("Page 1 of " + pages);
        } else {
            assertThat(html).doesNotContain("class=\"pagination\"");
        }
        assertThat(html).containsPattern("<option value=\"" + size + "\"\\s+selected=\"selected\">");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "0", "-4", "2.5", "abc", "1e3", "99999999999"})
    void invalidSizeShouldFallBackToDefault(String size) throws Exception {
        String html = body(get("/products").param("size", size));

        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(12);
        assertThat(html).contains("Page 1 of 2");
        assertThat(html).containsPattern("<option value=\"12\"\\s+selected=\"selected\">");
    }

    @Test
    void sizeAboveMaximumShouldBeCapped() throws Exception {
        String html = body(get("/products").param("size", "5000"));

        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(19);
        assertThat(html).containsPattern("<option value=\"100\"\\s+selected=\"selected\">");
        assertThat(html).doesNotContain("value=\"5000\"");
    }

    @Test
    void lastPageShouldShowRemainingProducts() throws Exception {
        String html = body(get("/products").param("size", "4").param("page", "4"));

        assertThat(countOccurrences(html, "class=\"product-card\"")).isEqualTo(3);
        assertThat(html).contains("class=\"pagination-status\">Page 5 of 5<", "Previous").doesNotContain("Next &raquo;");
    }

    @Test
    void paginationLinksShouldKeepSizeAndFilters() throws Exception {
        String html = body(get("/products").param("q", "e").param("size", "4").param("page", "1"));

        assertThat(linkBefore(html, "&laquo; Previous")).contains("q=e", "page=0", "size=4");
        assertThat(linkBefore(html, "Next &raquo;")).contains("q=e", "page=2", "size=4");
    }

    @Test
    void sizeFormShouldKeepFiltersAndResetToFirstPage() throws Exception {
        String html = body(get("/products").param("q", "o").param("category", "electronics")
                .param("size", "4").param("page", "1"));

        Matcher form = Pattern.compile("(?s)<form class=\"page-size\".*?</form>").matcher(html);
        assertThat(form.find()).as("page size form").isTrue();
        assertThat(form.group())
                .containsPattern("name=\"q\"\\s+value=\"o\"")
                .containsPattern("name=\"category\"\\s+value=\"electronics\"")
                .doesNotContain("name=\"page\"");
    }

    @Test
    void headerShouldKeepCustomSizeForCategoryAndSearch() throws Exception {
        String html = body(get("/products").param("size", "4"));

        assertThat(html).contains("href=\"/products?size=4\"",
                "href=\"/products?category=books&amp;size=4\"");
        assertThat(html).containsPattern("name=\"size\"\\s+value=\"4\"");
    }

    @Test
    void headerShouldOmitDefaultSize() throws Exception {
        String html = body(get("/products").param("size", "12"));

        assertThat(html).contains("href=\"/products\"", "href=\"/products?category=books\"");
        assertThat(html).doesNotContain("type=\"hidden\" name=\"size\"");
    }

    @Test
    void pageBeyondLastShouldOfferFirstPageWithSameSize() throws Exception {
        String html = body(get("/products").param("size", "20").param("page", "3"));

        assertThat(html).contains("No products found");
        assertThat(linkBefore(html, "Back to first page")).contains("page=0", "size=20");
    }

    @Test
    void hugePageShouldShowEmptyResultInsteadOfFailing() throws Exception {
        String html = body(get("/products").param("page", "2147483647").param("size", "100"));

        assertThat(html).contains("No products found");
    }

    @Test
    void emptyResultShouldHideSizeControl() throws Exception {
        String html = body(get("/products").param("category", "no-such-thing"));

        assertThat(html).doesNotContain("class=\"page-size\"");
    }

    @Test
    void detailShouldShowProductWithAddToCartForm() throws Exception {
        long id = SeedData.idOf(jdbc, "ELEC-001");

        MvcResult result = mockMvc.perform(get("/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("products/detail"))
                .andReturn();
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Aurora 4K Laptop", "4K display", "$1,234.56", "Electronics", "In stock");
        assertThat(html).containsPattern("<form[^>]*action=\"/cart/items\"[^>]*method=\"post\"");
        assertThat(html).contains("name=\"_csrf\"");
        Matcher quantity = Pattern.compile("<input[^>]*name=\"quantity\"[^>]*>").matcher(html);
        assertThat(quantity.find()).isTrue();
        assertThat(quantity.group()).contains("value=\"1\"").contains("min=\"1\"");
        assertThat(html).containsPattern("<input[^>]*name=\"productId\"[^>]*value=\"" + id + "\"");
        assertThat(html).containsPattern("<button type=\"submit\">Add to cart</button>");
    }

    @Test
    void outOfStockDetailShouldShowDisabledButton() throws Exception {
        long id = SeedData.idOf(jdbc, "BOOK-OOS");

        String html = body(get("/products/{id}", id));

        assertThat(html).contains("Rare Poetry Collection");
        Matcher button = Pattern.compile("<button[^>]*>Out of stock</button>").matcher(html);
        assertThat(button.find()).as("out-of-stock button").isTrue();
        assertThat(button.group()).contains("disabled");
        assertThat(html).doesNotContain(">Add to cart</button>");
    }

    @Test
    void unknownProductShouldReturn404Page() throws Exception {
        assertNotFound("/products/999999");
    }

    @Test
    void inactiveProductShouldReturn404Page() throws Exception {
        assertNotFound("/products/" + SeedData.idOf(jdbc, "CLOTH-OFF"));
    }

    @Test
    void nonNumericProductIdShouldReturn404Page() throws Exception {
        assertNotFound("/products/abc");
    }

    private void assertNotFound(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Not found");
    }

    private static String linkBefore(String html, String text) {
        Matcher link = Pattern.compile("<a[^>]*href=\"([^\"]*)\"[^>]*>" + Pattern.quote(text)).matcher(html);
        assertThat(link.find()).as(text + " link present").isTrue();
        return link.group(1);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
