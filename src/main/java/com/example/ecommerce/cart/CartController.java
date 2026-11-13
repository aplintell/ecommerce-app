package com.example.ecommerce.cart;

import jakarta.validation.Valid;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CartController {

    private final CartService cartService;
    private final MessageSource messageSource;

    public CartController(CartService cartService, MessageSource messageSource) {
        this.cartService = cartService;
        this.messageSource = messageSource;
    }

    @GetMapping("/cart")
    public String view(Model model) {
        CartView view = cartService.view();
        model.addAttribute("cart", view);
        // The view may have dropped unavailable lines, so refresh the header count.
        model.addAttribute("cartItemCount", view.itemCount());
        return "cart/view";
    }

    @PostMapping("/cart/items")
    public String add(@Valid @ModelAttribute("addToCartForm") AddToCartForm form, BindingResult result,
                      RedirectAttributes redirectAttributes) {
        boolean productIdValid = form.getProductId() != null && !result.hasFieldErrors("productId");
        String failureTarget = productIdValid ? "redirect:/products/" + form.getProductId() : "redirect:/cart";

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", firstMessage(result));
            return failureTarget;
        }
        try {
            String name = cartService.add(form.getProductId(), form.getQuantity());
            redirectAttributes.addFlashAttribute("success", "Added " + name + " to your cart");
            return "redirect:/cart";
        } catch (CartException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return failureTarget;
        }
    }

    @PostMapping("/cart/items/{productId}")
    public String update(@PathVariable Long productId,
                         @RequestParam(required = false) String quantity,
                         RedirectAttributes redirectAttributes) {
        try {
            String name = cartService.update(productId, quantity);
            String label = name != null ? name : "item";
            String message = "0".equals(quantity == null ? null : quantity.trim())
                    ? "Removed " + label + " from your cart"
                    : "Updated " + label + " in your cart";
            redirectAttributes.addFlashAttribute("success", message);
        } catch (CartException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/items/{productId}/delete")
    public String delete(@PathVariable Long productId, RedirectAttributes redirectAttributes) {
        cartService.remove(productId);
        redirectAttributes.addFlashAttribute("success", "Item removed from your cart");
        return "redirect:/cart";
    }

    private String firstMessage(BindingResult result) {
        ObjectError error = result.hasFieldErrors("quantity")
                ? result.getFieldError("quantity")
                : result.getAllErrors().get(0);
        return messageSource.getMessage(error, LocaleContextHolder.getLocale());
    }
}
