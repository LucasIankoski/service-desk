package com.centralservicos.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
class UserDirectoryController {

    private final IdentityService identity;

    UserDirectoryController(IdentityService identity) {
        this.identity = identity;
    }

    @GetMapping("/assignees")
    List<AssigneeView> assignees() {
        return identity.activeAssignees();
    }

    @GetMapping("/managers")
    List<AssigneeView> managers() {
        return identity.activeManagers();
    }
}
