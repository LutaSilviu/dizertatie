package ro.sluta.accessibility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ViewportTest {
    @Test
    void usesExactlyTheApprovedDimensions() {
        assertThat(Viewport.DESKTOP.width()).isEqualTo(1366);
        assertThat(Viewport.DESKTOP.height()).isEqualTo(768);
        assertThat(Viewport.MOBILE.width()).isEqualTo(390);
        assertThat(Viewport.MOBILE.height()).isEqualTo(844);
        assertThat(Viewport.REFLOW_320.width()).isEqualTo(320);
        assertThat(Viewport.REFLOW_320.height()).isEqualTo(800);
    }
}
