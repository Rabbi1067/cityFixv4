package bd.cityv1.errorPage;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ErrorPageController {

    @GetMapping("/error/403")
    public String accessDenied() {
        return "error/403";
    }

    @GetMapping("/error/404")
    public String notFound() {
        return "error/404";
    }
}