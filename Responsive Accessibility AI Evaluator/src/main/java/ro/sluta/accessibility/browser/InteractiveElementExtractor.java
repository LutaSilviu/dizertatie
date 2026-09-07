package ro.sluta.accessibility.browser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.domain.BoundingBox;
import ro.sluta.accessibility.domain.InteractiveElement;

@Component
public class InteractiveElementExtractor {
    private static final String SCRIPT = """
            () => JSON.stringify(Array.from(document.querySelectorAll(
              'a[href],button,input,select,textarea,summary,[tabindex],[contenteditable=true],[role]'
            )).map((el, index) => {
              const rect = el.getBoundingClientRect();
              const style = getComputedStyle(el);
              const labelledBy = (el.getAttribute('aria-labelledby') || '').split(/\\s+/)
                .filter(Boolean).map(id => document.getElementById(id)?.textContent?.trim() || '').join(' ').trim();
              const labels = el.labels ? Array.from(el.labels).map(label => label.textContent.trim()).join(' ').trim() : '';
              const roleMap = {A:'link', BUTTON:'button', INPUT: el.type === 'checkbox' ? 'checkbox' :
                (el.type === 'radio' ? 'radio' : 'textbox'), SELECT:'combobox', TEXTAREA:'textbox', SUMMARY:'button'};
              const testId = el.getAttribute('data-testid');
              const selector = testId ? `[data-testid="${CSS.escape(testId)}"]` :
                (el.id ? `#${CSS.escape(el.id)}` : `${el.tagName.toLowerCase()}:nth-of-type(${index + 1})`);
              const name = el.getAttribute('aria-label') || labelledBy || labels || el.getAttribute('alt') ||
                el.getAttribute('title') || el.getAttribute('placeholder') || el.textContent?.trim() || '';
              return {
                elementId: testId || el.id || `interactive-${index + 1}`,
                role: el.getAttribute('role') || roleMap[el.tagName] || 'generic',
                accessibleName: name.replace(/\\s+/g, ' ').slice(0, 500),
                tagName: el.tagName.toLowerCase(), selectorHint: selector,
                visible: rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none',
                enabled: !el.disabled && el.getAttribute('aria-disabled') !== 'true',
                focusable: el.tabIndex >= 0,
                boundingBox: {x:rect.x, y:rect.y, width:rect.width, height:rect.height}
              };
            }))
            """;

    private final ObjectMapper objectMapper;

    public InteractiveElementExtractor(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public List<InteractiveElement> extract(Page page) {
        try {
            String json = (String) page.evaluate(SCRIPT);
            List<Map<String, Object>> values = objectMapper.readValue(json, new TypeReference<>() { });
            return values.stream().map(this::map).toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Elementele interactive nu au putut fi serializate.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private InteractiveElement map(Map<String, Object> value) {
        Map<String, Object> box = (Map<String, Object>) value.get("boundingBox");
        return new InteractiveElement(
                string(value, "elementId"), string(value, "role"), string(value, "accessibleName"),
                string(value, "tagName"), string(value, "selectorHint"), bool(value, "visible"),
                bool(value, "enabled"), bool(value, "focusable"),
                new BoundingBox(number(box, "x"), number(box, "y"),
                        number(box, "width"), number(box, "height")));
    }

    private String string(Map<String, Object> map, String key) { return String.valueOf(map.getOrDefault(key, "")); }
    private boolean bool(Map<String, Object> map, String key) { return Boolean.TRUE.equals(map.get(key)); }
    private double number(Map<String, Object> map, String key) { return ((Number) map.getOrDefault(key, 0)).doubleValue(); }
}
