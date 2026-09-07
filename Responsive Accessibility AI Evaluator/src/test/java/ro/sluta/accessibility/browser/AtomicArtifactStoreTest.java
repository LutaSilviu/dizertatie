package ro.sluta.accessibility.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ro.sluta.accessibility.domain.ArtifactReference;
import ro.sluta.accessibility.domain.ArtifactType;

class AtomicArtifactStoreTest {
    @TempDir Path tempDirectory;

    @Test
    void publishesArtifactAtomicallyWithRelativePathSizeAndSha256() throws Exception {
        AtomicArtifactStore store = new AtomicArtifactStore(tempDirectory);
        UUID runId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        byte[] content = "<html lang=\"ro\"></html>".getBytes(StandardCharsets.UTF_8);

        ArtifactReference reference = store.write(runId, snapshotId, "page.html",
                ArtifactType.PAGE_HTML, "text/html; charset=UTF-8", content, "f2-v1");

        assertThat(reference.relativePath()).isEqualTo(
                "storage/snapshots/" + runId + "/" + snapshotId + "/page.html");
        assertThat(reference.byteSize()).isEqualTo(content.length);
        assertThat(reference.sha256()).hasSize(64).isEqualTo(AtomicArtifactStore.sha256(content));
        assertThat(reference.extractorVersion()).isEqualTo("f2-v1");
        assertThat(Files.readAllBytes(tempDirectory.resolve(reference.relativePath()))).isEqualTo(content);
        try (var files = Files.list(tempDirectory.resolve(reference.relativePath()).getParent())) {
            assertThat(files.map(path -> path.getFileName().toString())).noneMatch(name -> name.contains(".tmp-"));
        }
    }

    @Test
    void publishesRunArtifactInTheApprovedComponentDirectory() throws Exception {
        AtomicArtifactStore store = new AtomicArtifactStore(tempDirectory);
        UUID runId = UUID.randomUUID();
        byte[] content = "{\"violations\":[]}".getBytes(StandardCharsets.UTF_8);

        ArtifactReference reference = store.writeRunArtifact(runId, "axe", "axe-results.json",
                ArtifactType.AXE_RESULTS_JSON, "application/json", content, "4.13.0");

        assertThat(reference.relativePath()).isEqualTo(
                "storage/runs/" + runId + "/axe/axe-results.json");
        assertThat(reference.sha256()).isEqualTo(AtomicArtifactStore.sha256(content));
        assertThat(Files.readAllBytes(tempDirectory.resolve(reference.relativePath()))).isEqualTo(content);
    }
}
