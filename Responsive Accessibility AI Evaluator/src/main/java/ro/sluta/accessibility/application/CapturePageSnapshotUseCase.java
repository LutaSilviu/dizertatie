package ro.sluta.accessibility.application;

import ro.sluta.accessibility.domain.PageSnapshot;

public interface CapturePageSnapshotUseCase {
    PageSnapshot capture(CapturePageSnapshotCommand command);
}
