package org.admin.npapplication.identity.application;

import org.admin.npapplication.identity.contract.ChangePasswordRequest;
import org.admin.npapplication.identity.contract.UpdateProfileRequest;
import org.admin.npapplication.identity.contract.UserResponse;
import org.admin.npapplication.identity.domain.User;
import org.admin.npapplication.identity.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserResponse updateProfile(User user, UpdateProfileRequest request) {
        User existingUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        existingUser.setFullname(request.getFullname());
        // Phone and address would need to be added to User entity
        userRepository.save(existingUser);

        return new UserResponse(
                existingUser.getId(),
                existingUser.getFullname(),
                existingUser.getEmail(),
                existingUser.getRole()
        );
    }

    public void changePassword(User user, ChangePasswordRequest request) {
        User existingUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), existingUser.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        existingUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(existingUser);
    }
}
