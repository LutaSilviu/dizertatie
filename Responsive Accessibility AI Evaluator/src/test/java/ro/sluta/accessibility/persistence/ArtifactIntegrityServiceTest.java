package ro.sluta.accessibility.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ro.sluta.accessibility.browser.AtomicArtifactStore;
import ro.sluta.accessibility.domain.ArtifactReference;
import ro.sluta.accessibility.domain.ArtifactType;

class ArtifactIntegrityServiceTest {
    @TempDir Path root;

    @Test void verifiesContainedPathSizeAndHash() {
        ArtifactReference reference = new AtomicArtifactStore(root).write(UUID.randomUUID(), UUID.randomUUID(),
                "metadata.json", ArtifactType.METADATA_JSON, "application/json",
                "{}".getBytes(StandardCharsets.UTF_8), "test-v1");
        assertThat(new ArtifactIntegrityService(root).verify(reference)).isRegularFile().startsWith(root);
    }

    @Test void rejectsTraversalAndChangedContent() throws Exception {
        ArtifactIntegrityService integrity = new ArtifactIntegrityService(root);
        ArtifactReference traversal = new ArtifactReference(ArtifactType.METADATA_JSON, "../outside.json",
                "application/json", 2, "a".repeat(64), "test-v1");
        assertThatThrownBy(() -> integrity.verify(traversal)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage root");

        ArtifactReference reference = new AtomicArtifactStore(root).write(UUID.randomUUID(), UUID.randomUUID(),
                "metadata.json", ArtifactType.METADATA_JSON, "application/json",
                "{}".getBytes(StandardCharsets.UTF_8), "test-v1");
        Path artifact = root.resolve(reference.relativePath().replace('/', java.io.File.separatorChar));
        Files.writeString(artifact, "[]", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> integrity.verify(reference)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hashul");
    }
}
