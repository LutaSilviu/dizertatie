package usv.dizertatie.export;

import org.springframework.stereotype.Component;

/** Applies the field-quoting rules shared by the CSV exports. */
@Component
public class CsvFieldEscaper {

    public String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
