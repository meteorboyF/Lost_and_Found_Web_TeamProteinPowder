package com.teamproteinpowder.lostfound.web.dto;

import java.time.Instant;

import com.teamproteinpowder.lostfound.domain.ApprovalStatus;
import com.teamproteinpowder.lostfound.domain.Role;
import com.teamproteinpowder.lostfound.domain.User;

public record UserResponse(
        Long id,
        String username,
        String email,
        String studentId,
        String studentEmail,
        Role role,
        ApprovalStatus approvalStatus,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStudentId(),
                user.getStudentEmail() != null ? user.getStudentEmail() : user.getEmail(),
                user.getRole(),
                user.getApprovalStatus(),
                user.getCreatedAt()
        );
    }
}
