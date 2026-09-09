package org.admin.npapplication.controller;

import org.admin.npapplication.dto.UserResponse;
import org.admin.npapplication.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/customers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

    private final UserRepository userRepository;

    public AdminCustomerController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Page<UserResponse>> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int pageSize = Math.min(Math.max(size, 1), 50);
        Page<UserResponse> customers = userRepository.findByRole(
                "ROLE_USER",
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by("fullname").ascending())
        ).map(user -> new UserResponse(
                user.getId(),
                user.getFullname(),
                user.getEmail(),
                user.getRole()
        ));
        return ResponseEntity.ok(customers);
    }
}
