package com.teamproteinpowder.lostfound.web.dto;

import java.util.List;

import com.teamproteinpowder.lostfound.service.MatchService;

/**
 * A suggested match, with the reasons behind it.
 *
 * The reasons are the point: a bare percentage is not actionable, but "both
 * are keys, same area, within 2 days" tells someone whether it is worth
 * opening.
 */
public record MatchResponse(ItemResponse item, double score, int percent, List<String> reasons) {

    public static MatchResponse from(MatchService.Match match) {
        return new MatchResponse(
                ItemResponse.from(match.item()),
                match.score(),
                (int) Math.round(match.score() * 100),
                match.reasons());
    }
}
