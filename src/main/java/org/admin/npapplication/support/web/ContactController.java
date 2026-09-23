package org.admin.npapplication.support.web;

import org.admin.npapplication.platform.web.ApiResponse;
import org.admin.npapplication.support.contract.ContactMessageDto;
import org.admin.npapplication.support.contract.ContactMessageRequest;
import org.admin.npapplication.support.domain.ContactMessage;
import org.admin.npapplication.support.domain.MessageStatus;
import org.admin.npapplication.support.persistence.ContactMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    @Autowired
    private ContactMessageRepository messageRepository;

    @PostMapping
    public ResponseEntity<ApiResponse> sendMessage(@Valid @RequestBody ContactMessageRequest request) {
        ContactMessage message = new ContactMessage();
        message.setName(request.getName());
        message.setEmail(request.getEmail());
        message.setSubject(request.getSubject());
        message.setMessage(request.getMessage());
        messageRepository.save(message);
        return ResponseEntity.ok(new ApiResponse("Message sent successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ContactMessageDto>> getMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ContactMessage> messages;

        if (status != null) {
            try {
                MessageStatus messageStatus = MessageStatus.valueOf(status.toUpperCase());
                messages = messageRepository.findByStatus(messageStatus, pageable);
            } catch (IllegalArgumentException e) {
                messages = messageRepository.findAll(pageable);
            }
        } else {
            messages = messageRepository.findAll(pageable);
        }

        Page<ContactMessageDto> dtos = messages.map(this::mapToDto);
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContactMessageDto> updateStatus(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        ContactMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        try {
            message.setStatus(MessageStatus.valueOf(status.toUpperCase()));
            message = messageRepository.save(message);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }

        return ResponseEntity.ok(mapToDto(message));
    }

    private ContactMessageDto mapToDto(ContactMessage message) {
        return ContactMessageDto.builder()
                .id(message.getId())
                .name(message.getName())
                .email(message.getEmail())
                .subject(message.getSubject())
                .message(message.getMessage())
                .status(message.getStatus().name())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
