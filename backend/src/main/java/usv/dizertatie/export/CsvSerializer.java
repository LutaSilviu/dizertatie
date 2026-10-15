package usv.dizertatie.export;

import org.springframework.stereotype.Component;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;

import java.util.List;

@Component
public class CsvSerializer {

    private static final String HEADER =
            "scanId,url,method,timestamp,sourceMethod,wcagCriterion,wcagLevel,severity,elementSelector,description";

    public String serialize(Scan scan, List<Finding> findings) {
        StringBuilder sb = new StringBuilder(HEADER);
        for (Finding f : findings) {
            sb.append('\n');
            sb.append(escapeCsv(scan.getId() == null ? "" : scan.getId().toString()));
            sb.append(',');
            sb.append(escapeCsv(scan.getUrl()));
            sb.append(',');
            sb.append(escapeCsv(scan.getMethod()));
            sb.append(',');
            sb.append(escapeCsv(scan.getTimestamp() == null ? "" : scan.getTimestamp().toString()));
            sb.append(',');
            sb.append(escapeCsv(f.getSourceMethod()));
            sb.append(',');
            sb.append(escapeCsv(f.getWcagCriterion()));
            sb.append(',');
            sb.append(escapeCsv(f.getWcagLevel()));
            sb.append(',');
            sb.append(escapeCsv(f.getSeverity()));
            sb.append(',');
            sb.append(escapeCsv(f.getElementSelector()));
            sb.append(',');
            sb.append(escapeCsv(f.getDescription()));
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
