package com.teamproteinpowder.lostfound.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamproteinpowder.lostfound.domain.Claim;
import com.teamproteinpowder.lostfound.domain.ClaimStatus;
import com.teamproteinpowder.lostfound.domain.Item;

/**
 * Every read that feeds a response fetch-joins the item.
 *
 * open-in-view is off, so the persistence context closes before the DTO is
 * built; a lazy Item would throw LazyInitializationException at serialisation
 * time. Fetching explicitly also avoids an N+1 on the list endpoints.
 */
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    boolean existsByReference(String reference);

    long countByItemAndStatus(Item item, ClaimStatus status);

    boolean existsByItemAndClaimantEmailIgnoreCaseAndStatus(Item item, String email, ClaimStatus status);

    /** Plain lookup for the mutating paths, which run inside a transaction. */
    Optional<Claim> findByReference(String reference);

    /** One conversation with its item and every message already loaded. */
    @Query("""
            SELECT c FROM Claim c
            JOIN FETCH c.item
            LEFT JOIN FETCH c.messages
            WHERE c.reference = :reference
            """)
    Optional<Claim> findDetailByReference(@Param("reference") String reference);

    @Query("""
            SELECT c FROM Claim c
            JOIN FETCH c.item
            WHERE c.item = :item
            ORDER BY c.createdAt DESC
            """)
    List<Claim> findForItem(@Param("item") Item item);

    /** Everything one person is involved in, either side of the conversation. */
    @Query("""
            SELECT c FROM Claim c
            JOIN FETCH c.item i
            WHERE LOWER(c.claimantEmail) = LOWER(:email)
               OR LOWER(i.reporterEmail) = LOWER(:email)
            ORDER BY c.updatedAt DESC
            """)
    List<Claim> findInvolving(@Param("email") String email);

    @Query("""
            SELECT c FROM Claim c
            JOIN FETCH c.item
            WHERE c.reference IN :refs
            ORDER BY c.updatedAt DESC
            """)
    List<Claim> findByReferences(@Param("refs") List<String> refs);
}
