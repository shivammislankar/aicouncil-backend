package com.AI.aicouncil.controller;

import com.AI.aicouncil.security.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> body) {

        String email = body.get("email");
        String password = body.get("password");

        // TEMP: replace later with DB
        if (!"test@user.com".equals(email) || !"password".equals(password)) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = JwtUtil.generateToken(email);

        return Map.of("token", token);
    }
}
