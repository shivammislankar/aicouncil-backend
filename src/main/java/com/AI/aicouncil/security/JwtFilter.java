package com.AI.aicouncil.security;

import com.AI.aicouncil.context.RequestContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public class JwtFilter implements Filter {

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String header = req.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String identity = JwtUtil.validateAndGetIdentity(token);
            RequestContext.setIdentity(identity);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }
}
