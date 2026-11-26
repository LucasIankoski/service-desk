package com.centralservicos.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/units")
class AdminUnitController {

    private final UnitService units;
    private final CurrentUser currentUser;

    AdminUnitController(UnitService units, CurrentUser currentUser) {
        this.units = units;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<UnitView> list() {
        return units.all();
    }

    @PostMapping
    UnitView create(@Valid @RequestBody CreateUnitRequest request) {
        return units.create(request.code(), request.name(), request.supportEmail(), request.supportPhone(),
                request.copyConfigurationFrom(), currentUser.id());
    }

    @PatchMapping("/{id}")
    UnitView update(@PathVariable UUID id, @Valid @RequestBody UpdateUnitRequest request) {
        return units.update(id, request.name(), request.supportEmail(), request.supportPhone(), request.active(),
                request.version(), currentUser.id());
    }

    record CreateUnitRequest(@NotBlank @Size(max = 8) String code,
                             @NotBlank @Size(max = 160) String name,
                             @Size(max = 254) String supportEmail,
                             @Size(max = 40) String supportPhone,
                             UUID copyConfigurationFrom) { }

    record UpdateUnitRequest(@NotBlank @Size(max = 160) String name,
                             @Size(max = 254) String supportEmail,
                             @Size(max = 40) String supportPhone,
                             boolean active,
                             Long version) { }
}
