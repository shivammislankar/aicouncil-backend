package com.AI.aicouncil.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends email through Brevo's HTTPS API (port 443).
 *
 * <p>Render's free tier blocks outbound SMTP (ports 25, 465 and 587) since
 * September 2025, so Gmail SMTP silently times out there. An HTTPS API is not
 * affected. When {@code BREVO_API_KEY} is set, {@link OtpService} sends through
 * this client; otherwise it falls back to SMTP (fine for local development).</p>
 *
 * <p>The sender address ({@code MAIL_FROM}) must be a verified sender in the
 * Brevo dashboard, or Brevo rejects the request.</p>
 */
@Component
public class BrevoEmailClient {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailClient.class);
    private static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public BrevoEmailClient(
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${otp.from:}") String fromEmail,
            @Value("${otp.from-name:Veritas}") String fromName) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.fromName = fromName;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** True when an API key and a sender address are both configured. */
    public boolean isConfigured() {
        return !apiKey.isEmpty() && !fromEmail.isEmpty();
    }

    /**
     * Sends one plain-text email.
     *
     * @throws EmailSendException when Brevo refuses the message or can't be reached
     */
    public void send(String to, String subject, String text) {
        String body;
        try {
            body = json.writeValueAsString(Map.of(
                    "sender", Map.of("name", fromName, "email", fromEmail),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "textContent", text));
        } catch (IOException e) {
            throw new EmailSendException("Could not encode email", e);
        }

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(15))
                .header("api-key", apiKey)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EmailSendException("Brevo unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Interrupted while sending email", e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            // Brevo's error body names the problem (e.g. "sender not valid",
            // "unauthorized"); it never contains the API key or the code.
            String detail = response.body() == null ? "" : response.body();
            if (detail.length() > 300) detail = detail.substring(0, 300);
            log.warn("Brevo rejected email (HTTP {}): {}", status, detail);
            throw new EmailSendException("Brevo returned HTTP " + status, null);
        }
    }

    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
