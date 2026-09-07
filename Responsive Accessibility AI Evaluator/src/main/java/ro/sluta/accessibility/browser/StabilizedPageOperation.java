package ro.sluta.accessibility.browser;

import com.microsoft.playwright.Page;
import ro.sluta.accessibility.domain.PageSnapshot;

@FunctionalInterface
public interface StabilizedPageOperation<T> {
    T execute(Page page, PageSnapshot snapshot);
}
