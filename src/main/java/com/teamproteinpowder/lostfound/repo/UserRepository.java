package com.teamproteinpowder.lostfound.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teamproteinpowder.lostfound.domain.ApprovalStatus;
import com.teamproteinpowder.lostfound.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> lockForVerification(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    /** Locks every active admin row, so two admins demoting each other at once cannot leave none. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "SELECT u FROM User u WHERE u.role = com.teamproteinpowder.lostfound.domain.Role.ADMIN "
                    + "AND u.approvalStatus = com.teamproteinpowder.lostfound.domain.ApprovalStatus.APPROVED")
    java.util.List<User> lockActiveAdmins();

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByStudentIdIgnoreCase(String studentId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByStudentIdIgnoreCase(String studentId);

    List<User> findAllByOrderByCreatedAtDesc();

    long countByApprovalStatus(ApprovalStatus approvalStatus);

    long countByRoleAndApprovalStatus(com.teamproteinpowder.lostfound.domain.Role role, ApprovalStatus approvalStatus);
}
