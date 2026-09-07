package ro.sluta.accessibility.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.domain.ArtifactReference;

@Component
public class ArtifactIntegrityService {
    private final Path root;
    public ArtifactIntegrityService(Path snapshotStorageRoot) { this.root = snapshotStorageRoot; }

    public Path verify(ArtifactReference reference) {
        Path path = root.resolve(reference.relativePath().replace('/', java.io.File.separatorChar)).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Calea artefactului iese din storage root.");
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length != reference.byteSize()) throw new IllegalStateException("Dimensiunea artefactului nu corespunde.");
            if (!AtomicArtifactStore.sha256(bytes).equals(reference.sha256())) throw new IllegalStateException("Hashul artefactului nu corespunde.");
            return path;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Artefactul nu poate fi verificat.", exception);
        }
    }
}
