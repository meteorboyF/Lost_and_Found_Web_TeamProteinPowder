package com.teamproteinpowder.lostfound.repo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.teamproteinpowder.lostfound.domain.ClaimAudit;
public interface ClaimAuditRepository extends JpaRepository<ClaimAudit, Long> {
    List<ClaimAudit> findByClaimReferenceOrderByIdAsc(String reference);
    List<ClaimAudit> findTop500ByOrderByIdDesc();
}
