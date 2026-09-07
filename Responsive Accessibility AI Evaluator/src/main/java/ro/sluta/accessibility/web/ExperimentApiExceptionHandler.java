package ro.sluta.accessibility.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes={ExperimentController.class,EvaluationController.class})
public class ExperimentApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException error){return ResponseEntity.badRequest().body(Map.of("error",error.getMessage()));}
    @ExceptionHandler(IllegalStateException.class) ResponseEntity<?> conflict(IllegalStateException error){return ResponseEntity.status(409).body(Map.of("error",error.getMessage()));}
}
