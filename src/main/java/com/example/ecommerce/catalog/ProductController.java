package com.example.ecommerce.catalog;

import java.util.List;
import java.util.stream.Stream;

import com.example.ecommerce.cart.AddToCartForm;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProductController {

    private final CatalogService catalogService;

    public ProductController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/products";
    }

    @GetMapping("/products")
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String page,
                       @RequestParam(required = false) String size,
                       Model model) {
        Page<Product> result = catalogService.listProducts(category, q, page, size);
        int pageSize = result.getSize();
        model.addAttribute("page", result);
        model.addAttribute("pageSizeOptions", pageSizeOptions(pageSize));
        // Only set for a non-default size, so that header links and the search form stay clean otherwise.
        model.addAttribute("customPageSize", pageSize == CatalogService.DEFAULT_PAGE_SIZE ? null : pageSize);
        model.addAttribute("selectedCategory", CatalogService.normalize(category));
        model.addAttribute("q", CatalogService.normalize(q));
        return "products/list";
    }

    @GetMapping("/products/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Product product = catalogService.getActiveProduct(id);
        model.addAttribute("product", product);
        AddToCartForm form = new AddToCartForm();
        form.setProductId(product.getId());
        form.setQuantity(1);
        model.addAttribute("addToCartForm", form);
        return "products/detail";
    }

    /** The standard options plus the current size, so that the control always shows what is on screen. */
    private static List<Integer> pageSizeOptions(int currentSize) {
        return Stream.concat(CatalogService.PAGE_SIZE_OPTIONS.stream(), Stream.of(currentSize))
                .distinct()
                .sorted()
                .toList();
    }
}
