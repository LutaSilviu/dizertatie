package usv.dizertatie.axe;

import java.util.List;

/** A rule axe-core could not conclusively evaluate and the nodes that need human review. */
public record ManualReviewItem(
        String ruleId,
        String description,
        String impact,
        String help,
        String helpUrl,
        List<ManualReviewElement> elements
) {
}
