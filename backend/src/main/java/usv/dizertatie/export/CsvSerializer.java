package usv.dizertatie.export;

import org.springframework.stereotype.Component;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;

import java.util.List;

/**
 * Serializes a {@link Scan} and its {@link Finding} list to CSV format.
 */
@Component
public class CsvSerializer {

    private static final String HEADER = "sourceMethod,wcagCriterion,wcagLevel,severity,elementSelector,description";

    /**
     * Serializes the findings of a scan to a CSV string.
     *
     * @param scan     the scan entity (used for metadata if needed)
     * @param findings the list of findings to serialize
     * @return a CSV string with a header row followed by one row per finding
     */
    public String serialize(Scan scan, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append("\n");

        for (Finding f : findings) {
            sb.append(escapeCsv(f.getSourceMethod())).append(",");
            sb.append(escapeCsv(f.getWcagCriterion())).append(",");
            sb.append(escapeCsv(f.getWcagLevel())).append(",");
            sb.append(escapeCsv(f.getSeverity())).append(",");
            sb.append(escapeCsv(f.getElementSelector())).append(",");
            sb.append(escapeCsv(f.getDescription())).append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, double-quote, or newline — wrap in double quotes and escape inner quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
