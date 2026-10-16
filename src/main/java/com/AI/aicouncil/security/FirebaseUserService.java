package com.AI.aicouncil.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes verification results into Firebase Auth itself.
 *
 * <p>The local H2 database lives on Render's ephemeral disk and is wiped on
 * every redeploy or restart. Firebase's own {@code emailVerified} flag is
 * durable and travels inside every ID token, so once it is set the user stays
 * verified no matter what happens to this instance.</p>
 */
@Component
public class FirebaseUserService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseUserService.class);

    /**
     * Marks the account's email as verified in Firebase.
     *
     * @return true when Firebase accepted the update
     */
    public boolean markEmailVerified(String uid) {
        if (uid == null) return false;
        try {
            FirebaseAuth.getInstance().updateUser(
                    new UserRecord.UpdateRequest(uid).setEmailVerified(true));
            return true;
        } catch (FirebaseAuthException | RuntimeException e) {
            // Not fatal: the local verification row still lets the user in
            // until the next restart, and the next login will retry.
            log.warn("Could not set emailVerified in Firebase for uid {}: {}", uid, e.getMessage());
            return false;
        }
    }

    /** True when the account signed in through GitHub or Google. */
    public boolean hasVerifiedOAuthProvider(String uid) throws FirebaseAuthException {
        UserRecord record = FirebaseAuth.getInstance().getUser(uid);
        for (var provider : record.getProviderData()) {
            String id = provider.getProviderId();
            if ("github.com".equals(id) || "google.com".equals(id)) {
                return true;
            }
        }
        return false;
    }
}
