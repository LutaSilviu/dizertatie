package ro.sluta.accessibility.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.domain.ArtifactReference;
import ro.sluta.accessibility.domain.ArtifactType;

@Component
public class AtomicArtifactStore {
    private final Path root;

    public AtomicArtifactStore(Path snapshotStorageRoot) {
        this.root = snapshotStorageRoot;
    }

    public ArtifactReference write(UUID runId, UUID snapshotId, String fileName,
                                   ArtifactType type, String mimeType, byte[] bytes,
                                   String extractorVersion) {
        String relative = "storage/snapshots/" + runId + "/" + snapshotId + "/" + fileName;
        return writeRelative(relative, fileName, type, mimeType, bytes, extractorVersion);
    }

    public ArtifactReference writeRunArtifact(UUID runId, String component, String fileName,
                                              ArtifactType type, String mimeType, byte[] bytes,
                                              String producerVersion) {
        if (component == null || !component.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Componenta artefactului nu este validă.");
        }
        String relative = "storage/runs/" + runId + "/" + component + "/" + fileName;
        return writeRelative(relative, fileName, type, mimeType, bytes, producerVersion);
    }

    private ArtifactReference writeRelative(String relative, String fileName, ArtifactType type,
                                            String mimeType, byte[] bytes, String producerVersion) {
        Path target = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Calea artefactului iese din storage.");
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, bytes);
            String hash = sha256(bytes);
            long size = Files.size(temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new ArtifactReference(type, relative, mimeType, size, hash, producerVersion);
        } catch (IOException exception) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            throw new ArtifactWriteException("Artefactul nu a putut fi publicat atomic: " + fileName, exception);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 nu este disponibil.", exception);
        }
    }
}
