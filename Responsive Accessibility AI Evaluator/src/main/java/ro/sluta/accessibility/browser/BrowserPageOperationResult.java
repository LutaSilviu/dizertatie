package ro.sluta.accessibility.browser;

import ro.sluta.accessibility.domain.PageSnapshot;

public record BrowserPageOperationResult<T>(PageSnapshot snapshot, T operationResult) {
}
