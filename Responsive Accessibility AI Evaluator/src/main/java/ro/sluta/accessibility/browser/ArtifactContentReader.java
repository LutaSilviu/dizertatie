package ro.sluta.accessibility.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.domain.ArtifactReference;

@Component
public class ArtifactContentReader {
    private final Path root;

    public ArtifactContentReader(Path snapshotStorageRoot) {
        this.root = snapshotStorageRoot;
    }

    public byte[] readVerified(ArtifactReference reference) {
        Path path = root.resolve(reference.relativePath().replace('/', java.io.File.separatorChar)).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Calea artefactului iese din storage.");
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (!AtomicArtifactStore.sha256(bytes).equals(reference.sha256())) {
                throw new IllegalStateException("Hashul artefactului nu corespunde metadatelor.");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("Artefactul nu poate fi citit: " + reference.relativePath(), exception);
        }
    }
}
