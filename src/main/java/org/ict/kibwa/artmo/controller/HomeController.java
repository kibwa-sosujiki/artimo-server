package org.ict.kibwa.artmo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
public class HomeController {

    @GetMapping("")
    public RedirectView home() {
        return new RedirectView("/diary");
    }

}
