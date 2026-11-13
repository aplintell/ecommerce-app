package com.example.ecommerce.web;

import com.example.ecommerce.catalog.ProductNotFoundException;
import com.example.ecommerce.order.OrderNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ProductNotFoundException.class, OrderNotFoundException.class})
    public ModelAndView notFound() {
        return view("error/404", HttpStatus.NOT_FOUND);
    }

    /**
     * A non-numeric path id (e.g. /products/abc) is a missing resource, so 404.
     * Other type mismatches are bad requests.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView typeMismatch(MethodArgumentTypeMismatchException ex) {
        if (ex.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return view("error/404", HttpStatus.NOT_FOUND);
        }
        return view("error/error", HttpStatus.BAD_REQUEST);
    }

    private static ModelAndView view(String name, HttpStatus status) {
        ModelAndView mav = new ModelAndView(name);
        mav.setStatus(status);
        mav.addObject("status", status.value());
        return mav;
    }
}
