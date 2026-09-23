package org.admin.npapplication.identity.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {
    @NotBlank
    @Size(max = 100)
    private String fullname;

    @Size(max = 20)
    private String phone;

    @Size(max = 500)
    private String address;
}