package com.teamproteinpowder.lostfound.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.teamproteinpowder.lostfound.domain.Comment;
import com.teamproteinpowder.lostfound.domain.Item;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Visible thread, oldest first — a conversation reads top to bottom. */
    @Query("""
            SELECT c FROM Comment c
            WHERE c.item = :item AND c.hidden = false
            ORDER BY c.createdAt ASC
            """)
    List<Comment> findVisibleForItem(@Param("item") Item item);

    /** Everything, including hidden, for the moderation queue. */
    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.item
            ORDER BY c.createdAt DESC
            """)
    List<Comment> findAllForModeration();

    long countByItemAndHiddenFalse(Item item);
}
