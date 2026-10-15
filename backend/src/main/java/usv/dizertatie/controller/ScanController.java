package usv.dizertatie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import usv.dizertatie.service.ScanService;

@RestController
@RequestMapping("/scans")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    @GetMapping
    public ScanHistoryResponse getScans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scanService.findAll(page, size);
    }
}
