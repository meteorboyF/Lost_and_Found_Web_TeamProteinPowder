package com.teamproteinpowder.lostfound.service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.teamproteinpowder.lostfound.domain.Item;
import com.teamproteinpowder.lostfound.domain.ItemKind;
import com.teamproteinpowder.lostfound.domain.ItemStatus;
import com.teamproteinpowder.lostfound.repo.ItemRepository;

/**
 * Smart matching.
 *
 * When someone posts a lost item we score it against every found item (and
 * vice versa) and surface the strong overlaps. The scoring is deliberately
 * explainable — each signal contributes a named, fixed weight, so the UI can
 * say *why* two things look like a match instead of showing an opaque number.
 *
 * Weights, summing to 1.0:
 *   category   0.30   the single strongest signal, and exact
 *   words      0.30   meaningful words shared between title and description
 *   location   0.20   overlap in where it happened
 *   colour     0.12   exact or contained colour name
 *   time       0.08   found shortly after lost is likelier than months apart
 */
@Service
public class MatchService {

    /** Below this, a suggestion is noise rather than a lead. */
    private static final double THRESHOLD = 0.35;

    private static final int MAX_RESULTS = 6;

    /**
     * Words too common to carry signal. Matching on "the" or "black bag" alone
     * would make every pair look plausible.
     */
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "a", "an", "and", "or", "but", "of", "in", "on", "at", "to", "for",
            "with", "my", "me", "i", "is", "was", "it", "its", "this", "that", "there",
            "near", "by", "from", "has", "have", "had", "been", "some", "any", "very",
            "lost", "found", "missing", "please", "help", "think", "maybe", "around",
            "somewhere", "item", "thing", "stuff", "left", "put", "took", "taken"));

    private final ItemRepository items;

    public MatchService(ItemRepository items) {
        this.items = items;
    }

    /** One scored candidate, with the reasons that produced the score. */
    public record Match(Item item, double score, List<String> reasons) {
    }

    @Transactional(readOnly = true)
    public List<Match> findMatches(Item source) {
        ItemKind opposite = source.getKind() == ItemKind.LOST ? ItemKind.FOUND : ItemKind.LOST;

        List<Match> scored = new ArrayList<>();

        for (Item candidate : items.findAll()) {
            if (candidate.getKind() != opposite) {
                continue;
            }
            if (candidate.getStatus() == ItemStatus.RESOLVED) {
                continue;
            }
            if (candidate.getId().equals(source.getId())) {
                continue;
            }

            List<String> reasons = new ArrayList<>();
            double score = score(source, candidate, reasons);

            if (score >= THRESHOLD) {
                scored.add(new Match(candidate, round(score), reasons));
            }
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return scored.size() > MAX_RESULTS ? scored.subList(0, MAX_RESULTS) : scored;
    }

    /* ---------------------------------------------------------------------
       Scoring
       --------------------------------------------------------------------- */

    private double score(Item a, Item b, List<String> reasons) {
        double total = 0;

        // -- category ------------------------------------------------------
        if (a.getCategory() == b.getCategory()) {
            total += 0.30;
            reasons.add("Both are " + a.getCategory().getLabel().toLowerCase());
        }

        // -- shared words --------------------------------------------------
        Set<String> wordsA = meaningfulWords(a.getTitle() + " " + a.getDescription());
        Set<String> wordsB = meaningfulWords(b.getTitle() + " " + b.getDescription());
        Set<String> shared = new HashSet<>(wordsA);
        shared.retainAll(wordsB);

        if (!shared.isEmpty()) {
            /* Jaccard-style, so a long description cannot inflate the score
               simply by containing more words. */
            int union = wordsA.size() + wordsB.size() - shared.size();
            double overlap = union == 0 ? 0 : (double) shared.size() / union;
            total += 0.30 * Math.min(1.0, overlap * 3);

            List<String> top = shared.stream().sorted((x, y) -> y.length() - x.length()).limit(3).toList();
            reasons.add("Both mention " + String.join(", ", top));
        }

        // -- location ------------------------------------------------------
        Set<String> placeA = meaningfulWords(a.getLocation());
        Set<String> placeB = meaningfulWords(b.getLocation());
        Set<String> placeShared = new HashSet<>(placeA);
        placeShared.retainAll(placeB);

        if (!placeShared.isEmpty()) {
            double ratio = (double) placeShared.size() / Math.max(1, Math.min(placeA.size(), placeB.size()));
            total += 0.20 * Math.min(1.0, ratio);
            reasons.add("Same area: " + String.join(", ", placeShared));
        }

        // -- colour --------------------------------------------------------
        String colourA = normalise(a.getColour());
        String colourB = normalise(b.getColour());
        if (!colourA.isEmpty() && !colourB.isEmpty()) {
            if (colourA.equals(colourB) || colourA.contains(colourB) || colourB.contains(colourA)) {
                total += 0.12;
                reasons.add("Both " + a.getColour().toLowerCase());
            }
        }

        // -- timing --------------------------------------------------------
        if (a.getHappenedOn() != null && b.getHappenedOn() != null) {
            long days = Math.abs(ChronoUnit.DAYS.between(a.getHappenedOn(), b.getHappenedOn()));
            if (days <= 14) {
                /* Full marks the same day, tapering to nothing after a
                   fortnight — an item found months later is a weaker lead. */
                double closeness = 1.0 - (days / 14.0);
                total += 0.08 * closeness;
                if (days == 0) {
                    reasons.add("Same day");
                } else if (days <= 3) {
                    reasons.add("Within " + days + (days == 1 ? " day" : " days"));
                }
            }
        } else {
            long hours = Math.abs(Duration.between(a.getCreatedAt(), b.getCreatedAt()).toHours());
            if (hours <= 72) {
                total += 0.08 * (1.0 - (hours / 72.0));
            }
        }

        return Math.min(1.0, total);
    }

    private static Set<String> meaningfulWords(String text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            /* Two-letter fragments carry no signal and match too easily. */
            if (raw.length() > 2 && !STOPWORDS.contains(raw)) {
                out.add(stem(raw));
            }
        }
        return out;
    }

    /**
     * Crude plural folding so "keys" matches "key" and "glasses" matches
     * "glass". Not a real stemmer, but enough for short item descriptions and
     * far more predictable than pulling in a linguistics library.
     */
    private static String stem(String word) {
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 4 && word.endsWith("es")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.length() > 3 && word.endsWith("s")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static String normalise(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
