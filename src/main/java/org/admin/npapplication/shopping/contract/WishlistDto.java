package org.admin.npapplication.shopping.contract;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistDto {
    private Long id;
    private List<WishlistItemDto> items;
    private Integer totalItems;
}