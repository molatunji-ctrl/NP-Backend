package org.admin.npapplication.identity.application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.springframework.stereotype.Service;

@Service
public class AdminCheckService {

    public boolean isAdmin(String email) {
        if (email == null || email.isBlank() || FirebaseApp.getApps().isEmpty()) {
            return false;
        }

        try {
            UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(email);
            Object adminClaim = userRecord.getCustomClaims().get("admin");
            return Boolean.TRUE.equals(adminClaim);
        } catch (FirebaseAuthException | IllegalStateException e) {
            return false;
        }
    }
}
