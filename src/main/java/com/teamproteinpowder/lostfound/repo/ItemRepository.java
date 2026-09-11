package com.teamproteinpowder.lostfound.repo;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamproteinpowder.lostfound.domain.Category;
import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.ItemKind;
import com.teamproteinpowder.lostfound.domain.ItemStatus;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByReference(String reference);

    boolean existsByReference(String reference);

    long countByKind(ItemKind kind);

    long countByStatus(ItemStatus status);

    /**
     * The browse query. Every filter is optional: a null parameter drops out of
     * the predicate rather than matching nothing, which lets one query back the
     * whole faceted search instead of hand-assembling a Specification.
     *
     * The keyword search is a case-insensitive LIKE across the fields a person
     * would actually type into a search box.
     */
    @Query("""
            SELECT i FROM Item i
            WHERE (:kind IS NULL OR i.kind = :kind)
              AND (:status IS NULL OR i.status = :status)
              AND (:category IS NULL OR i.category = :category)
              AND (:q IS NULL OR
                   LOWER(i.title)       LIKE LOWER(CONCAT('%', :q, '%')) OR
                   LOWER(i.description) LIKE LOWER(CONCAT('%', :q, '%')) OR
                   LOWER(i.location)    LIKE LOWER(CONCAT('%', :q, '%')) OR
                   LOWER(COALESCE(i.colour, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Item> search(@Param("kind") ItemKind kind,
                      @Param("status") ItemStatus status,
                      @Param("category") Category category,
                      @Param("q") String q,
                      Pageable pageable);
}
