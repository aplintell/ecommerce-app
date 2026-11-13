package com.example.ecommerce.catalog;

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
                       Model model) {
        Page<Product> result = catalogService.listProducts(category, q, page);
        model.addAttribute("page", result);
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
}
