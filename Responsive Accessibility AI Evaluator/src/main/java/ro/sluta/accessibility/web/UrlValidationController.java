package ro.sluta.accessibility.web;

import ro.sluta.accessibility.security.PublicUrlValidator;
import ro.sluta.accessibility.security.UrlValidationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UrlValidationController {
    private final PublicUrlValidator urlValidator;
    private final HomeController homeController;
    public UrlValidationController(PublicUrlValidator urlValidator, HomeController homeController) {
        this.urlValidator = urlValidator;
        this.homeController = homeController;
    }

    @PostMapping("/validate-url")
    public String validateUrl(@RequestParam String url, Model model) {
        try {
            urlValidator.validate(url);
            model.addAttribute("success", "URL-ul este acceptat pentru etapa de analiză.");
        } catch (UrlValidationException exception) { model.addAttribute("error", exception.getMessage()); }
        model.addAttribute("url", url);
        homeController.prepare(model);
        return "home";
    }
}
