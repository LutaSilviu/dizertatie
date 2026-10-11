package usv.dizertatie.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
}
