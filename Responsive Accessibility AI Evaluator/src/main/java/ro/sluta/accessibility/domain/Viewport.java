package ro.sluta.accessibility.domain;

public enum Viewport {
    DESKTOP(1366, 768),
    MOBILE(390, 844),
    REFLOW_320(320, 800);

    private final int width;
    private final int height;

    Viewport(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() { return width; }
    public int height() { return height; }
}
