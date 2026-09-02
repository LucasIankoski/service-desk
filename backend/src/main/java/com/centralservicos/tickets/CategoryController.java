package com.centralservicos.tickets;

import com.centralservicos.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
class CategoryController {

    private final CategoryService categories;
    private final CurrentUser currentUser;

    CategoryController(CategoryService categories, CurrentUser currentUser) {
        this.categories = categories;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/categories")
    List<CategoryView> active() {
        return categories.active();
    }

    @GetMapping("/api/v1/admin/categories")
    List<CategoryView> all() {
        return categories.all();
    }

    @PostMapping("/api/v1/admin/categories")
    CategoryView create(@Valid @RequestBody CategoryRequest request) {
        return categories.create(request.name(), currentUser.id());
    }

    @PatchMapping("/api/v1/admin/categories/{id}")
    CategoryView update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categories.update(id, request.name(), request.active(), currentUser.id());
    }

    record CategoryRequest(@NotBlank @Size(max = 100) String name, boolean active) {
    }
}
