package ro.sluta.accessibility.security;

import java.net.URI;

public interface NetworkAccessPolicy {
    void verify(URI uri, boolean mainDocument);
    boolean allowsLoopbackFixtures();
}
