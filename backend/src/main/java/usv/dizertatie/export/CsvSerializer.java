package usv.dizertatie.export;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import usv.dizertatie.domain.Finding;
import usv.dizertatie.domain.Scan;

import java.time.ZoneOffset;
import java.util.List;

/**
 * Serializes a {@link Scan} and its {@link Finding} list to CSV format.
 */
@Component
@RequiredArgsConstructor
public class CsvSerializer {

    private static final String HEADER =
            "scanId,url,method,timestamp,sourceMethod,wcagCriterion,wcagLevel,severity,elementSelector,description,ruleId";

    private final CsvFieldEscaper csvFieldEscaper;

    public String serialize(Scan scan, List<Finding> findings) {
        StringBuilder sb = new StringBuilder(HEADER);
        for (Finding f : findings) {
            sb.append('\n');
            sb.append(csvFieldEscaper.escape(scan.getId() == null ? "" : scan.getId().toString()));
            sb.append(',');
            sb.append(csvFieldEscaper.escape(scan.getUrl()));
            sb.append(',');
            sb.append(csvFieldEscaper.escape(scan.getMethod()));
            sb.append(',');
            sb.append(csvFieldEscaper.escape(scan.getTimestamp() == null ? "" : scan.getTimestamp().toInstant(ZoneOffset.UTC).toString()));
            sb.append(',');
            sb.append(csvFieldEscaper.escape(f.getSourceMethod()));
            sb.append(',');
            sb.append(csvFieldEscaper.escape(f.getWcagCriterion()));
            sb.append(',');
            sb.append(csvFieldEscaper.escape(f.getWcagLevel()));
            sb.append(',');
            sb.append(csvFieldEscaper.escape(f.getSeverity()));
            sb.append(',');
            sb.append(csvFieldEscaper.escape(f.getElementSelector()));
            sb.append(',');
            sb.append(csvFieldEscaper.escape(f.getDescription()));
            sb.append(',');
            // Last column so existing column positions stay stable; empty for AI findings.
            sb.append(csvFieldEscaper.escape(f.getRuleId()));
        }
        return sb.toString();
    }
}
