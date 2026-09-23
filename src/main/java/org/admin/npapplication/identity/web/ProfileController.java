package org.admin.npapplication.identity.web;

import org.admin.npapplication.identity.application.ProfileService;
import org.admin.npapplication.identity.contract.ChangePasswordRequest;
import org.admin.npapplication.identity.contract.UpdateProfileRequest;
import org.admin.npapplication.identity.contract.UserResponse;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.platform.web.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    @GetMapping
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal User user) {
        UserResponse profile = new UserResponse(
                user.getId(),
                user.getFullname(),
                user.getEmail(),
                user.getRole()
        );
        return ResponseEntity.ok(profile);
    }

    @PutMapping
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UserResponse updated = profileService.updateProfile(user, request);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        profileService.changePassword(user, request);
        return ResponseEntity.ok(new ApiResponse("Password changed successfully"));
    }
}
