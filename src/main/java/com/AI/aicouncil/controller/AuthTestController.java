package com.AI.aicouncil.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthTestController {

    @GetMapping("/auth/check")
    public String check() {
        return "You are authenticated!";
    }
}
