package com.gerenciadorrural.modules.identity.api;

import com.gerenciadorrural.modules.identity.application.GetCurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class CurrentUserController {

    private final GetCurrentUser getCurrentUser;

    public CurrentUserController(GetCurrentUser getCurrentUser) {
        this.getCurrentUser = getCurrentUser;
    }

    @GetMapping
    public CurrentUserResponse get() {
        return CurrentUserResponse.from(getCurrentUser.execute());
    }
}
