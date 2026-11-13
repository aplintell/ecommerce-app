package com.example.ecommerce.checkout;

import com.example.ecommerce.cart.Cart;
import com.example.ecommerce.cart.CartService;
import com.example.ecommerce.cart.CartView;
import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.order.PlacedOrders;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CheckoutController {

    static final String DECLINED_MESSAGE = "Your card was declined. Please use a different card.";
    static final String PROCESSING_ERROR_MESSAGE = "Payment could not be processed. Please try again.";

    private final Cart cart;
    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final PlacedOrders placedOrders;

    public CheckoutController(Cart cart, CartService cartService, CheckoutService checkoutService,
                              PlacedOrders placedOrders) {
        this.cart = cart;
        this.cartService = cartService;
        this.checkoutService = checkoutService;
        this.placedOrders = placedOrders;
    }

    /** Allow-list: extra parameters such as {@code price} or {@code total} are never bound. */
    @InitBinder("checkoutForm")
    void initBinder(WebDataBinder binder) {
        binder.setAllowedFields("fullName", "email", "addressLine1", "addressLine2", "city", "postalCode",
                "country", "cardholderName", "cardNumber", "expiryMonth", "expiryYear", "cvv");
    }

    @GetMapping("/checkout")
    public String form(Model model) {
        if (cart.isEmpty()) {
            return "redirect:/cart";
        }
        CartView view = cartService.view();
        if (!view.checkoutAllowed()) {
            return "redirect:/cart";
        }
        model.addAttribute("checkoutForm", new CheckoutForm());
        model.addAttribute("cart", view);
        return "checkout/form";
    }

    @PostMapping("/checkout")
    public String submit(@Valid @ModelAttribute("checkoutForm") CheckoutForm form, BindingResult result,
                         Model model, RedirectAttributes redirectAttributes) {
        // Checked first, before looking at the form.
        if (cart.isEmpty()) {
            return "redirect:/cart";
        }
        if (result.hasErrors()) {
            return rerender(form, model);
        }
        try {
            CustomerOrder order = checkoutService.placeOrder(form, cart.lines());
            cart.clear();
            placedOrders.add(order.getOrderNumber());
            return "redirect:/orders/" + order.getOrderNumber() + "/confirmation";
        } catch (PaymentDeclinedException e) {
            result.reject("payment.declined", DECLINED_MESSAGE);
            return rerender(form, model);
        } catch (PaymentProcessingException e) {
            result.reject("payment.error", PROCESSING_ERROR_MESSAGE);
            return rerender(form, model);
        } catch (InsufficientStockException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cart";
        } catch (EmptyCartException e) {
            return "redirect:/cart";
        }
    }

    private String rerender(CheckoutForm form, Model model) {
        form.clearSensitiveFields();
        model.addAttribute("cart", cartService.view());
        return "checkout/form";
    }
}
