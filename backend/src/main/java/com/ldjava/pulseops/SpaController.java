package com.ldjava.pulseops;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Only known application routes forward to Vue; API/static misses never become HTML 200 responses. */
@Controller
class SpaController {
    @GetMapping({"/", "/incidents", "/about", "/login", "/admin", "/admin/{tab:monitors|incidents|audit|system}", "/monitors/{id:[0-9]+}"})
    String app() { return "forward:/index.html"; }
}
