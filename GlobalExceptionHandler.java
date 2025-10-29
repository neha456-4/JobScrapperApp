package org.example.jobscraperweb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model) {
        logger.error("Unexpected error occurred", ex);
        model.addAttribute("error", "An unexpected error occurred. Please try again later.");
        model.addAttribute("jobs", java.util.Collections.emptyList());
        model.addAttribute("sources", java.util.Collections.emptyList());
        model.addAttribute("totalItems", 0);
        model.addAttribute("totalPages", 0);
        model.addAttribute("currentPage", 0);
        return "index";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException ex, Model model) {
        logger.warn("Bad request: {}", ex.getMessage());
        model.addAttribute("error", "Invalid search parameters. Please try again.");
        model.addAttribute("jobs", java.util.Collections.emptyList());
        model.addAttribute("sources", java.util.Collections.emptyList());
        model.addAttribute("totalItems", 0);
        model.addAttribute("totalPages", 0);
        model.addAttribute("currentPage", 0);
        return "index";
    }
}
