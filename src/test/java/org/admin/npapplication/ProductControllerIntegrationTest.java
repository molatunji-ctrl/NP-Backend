package org.admin.npapplication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.admin.npapplication.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin@nugespharmacy.com", roles = "ADMIN")
    void shouldCreateAndListProductsForAdmin() throws Exception {
        Product product = new Product();
        product.setName("Paracetamol 500mg");
        product.setDescription("Pain relief");
        product.setPrice(new BigDecimal("2500.00"));
        product.setStock(25);
        product.setCategory("General");
        product.setBadge("Popular");
        product.setFeatured(false);
        product.setActive(true);

        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Paracetamol 500mg"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").exists());
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void shouldRejectProductCreationForNonAdmin() throws Exception {
        Product product = new Product();
        product.setName("Blocked product");
        product.setDescription("Should not create");
        product.setPrice(new BigDecimal("1000.00"));
        product.setStock(10);

        mockMvc.perform(post("/api/admin/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isForbidden());
    }
}
