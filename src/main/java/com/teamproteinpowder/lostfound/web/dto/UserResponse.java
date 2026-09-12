package com.teamproteinpowder.lostfound.web.dto;

import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.domain.User;

public record UserResponse(Long id, String username, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
