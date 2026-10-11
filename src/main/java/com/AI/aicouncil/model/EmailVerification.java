package com.AI.aicouncil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

/**
 * One row per email address, tracking both the email-OTP flow and the final
 * verdict. A row with {@code verified=true} means the address proved it can
 * receive mail (via a 6-digit code) or arrived through an already-verified
 * OAuth provider such as GitHub.
 *
 * <p>Only a SHA-256 hash of the code is ever stored — a database leak must
 * not hand out live login codes.</p>
 */
@Entity
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lower-cased email address. Unique: one verification state per address. */
    @Column(nullable = false, unique = true)
    private String email;

    /** SHA-256 hex of the pending 6-digit code, or null when none is active. */
    private String codeHash;

    private Instant codeExpiresAt;

    /** Wrong guesses against the current code. */
    private int attempts;

    /** Codes requested inside the current resend window. */
    private int requestCount;

    private Instant windowStart;

    private boolean verified;

    private Instant updatedAt;

    protected EmailVerification() {
    }

    public EmailVerification(String email) {
        this.email = email;
        this.verified = false;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Instant getCodeExpiresAt() {
        return codeExpiresAt;
    }

    public void setCodeExpiresAt(Instant codeExpiresAt) {
        this.codeExpiresAt = codeExpiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
