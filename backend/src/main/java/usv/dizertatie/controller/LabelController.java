package usv.dizertatie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import usv.dizertatie.service.LabelService;

import java.util.List;

@RestController
@RequestMapping("/labels")
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @GetMapping
    public List<LabelResponse> getLabels(@RequestParam(required = false) String url) {
        if (url != null) {
            return labelService.findByUrl(url);
        }
        return labelService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabelResponse createLabel(@RequestBody LabelRequest request) {
        return labelService.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLabel(@PathVariable Long id) {
        labelService.delete(id);
    }
}
