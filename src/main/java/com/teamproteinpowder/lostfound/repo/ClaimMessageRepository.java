package com.teamproteinpowder.lostfound.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teamproteinpowder.lostfound.domain.ClaimMessage;

public interface ClaimMessageRepository extends JpaRepository<ClaimMessage, Long> {
}
