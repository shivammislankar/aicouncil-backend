package com.AI.aicouncil.security;

import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public FirebaseToken get() {
        return (FirebaseToken) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }

    public String uid() {
        return get().getUid();
    }

    public String email() {
        return get().getEmail();
    }
}
