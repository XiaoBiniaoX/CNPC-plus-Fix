package bin.cnpcplus.recipe;

import noppes.npcs.controllers.data.RecipeCarpentry;

/**
 * Matcher output. Extra fields reserved for Phase4 (offset/mirror); unused in Phase2 logic.
 */
public final class MatchResult {
    public final RecipeCarpentry recipe;
    public final int offsetX;
    public final int offsetY;
    public final boolean mirrored;
    public final int score;

    public static final MatchResult MISS = new MatchResult(null, 0, 0, false, 0);

    public MatchResult(RecipeCarpentry recipe, int offsetX, int offsetY, boolean mirrored, int score) {
        this.recipe = recipe;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.mirrored = mirrored;
        this.score = score;
    }

    public boolean hit() {
        return recipe != null;
    }

    public static MatchResult of(RecipeCarpentry recipe) {
        return recipe == null ? MISS : new MatchResult(recipe, 0, 0, false, 0);
    }
}
