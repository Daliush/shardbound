package fr.daliush.shardbound.api.controller.rest;

import fr.daliush.shardbound.api.domain.services.evaluation.EvaluationService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Arbiter's evaluations for the benchmark explorer: a dataset split's timestamps, then one evaluation. */
@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluations;

    public EvaluationController(EvaluationService evaluations) {
        this.evaluations = evaluations;
    }

    /** Oldest first, as {@code 2026-10-15T10-42-07Z}. */
    @GetMapping("/{version}/{split}")
    public List<String> listTimestamps(@PathVariable String version, @PathVariable String split) {
        return evaluations.listTimestamps(version, split);
    }

    /** The document as the evaluation wrote it: its runs by layer, their scores and each question's result. */
    @GetMapping(value = "/{version}/{split}/{timestamp}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String findEvaluation(@PathVariable String version, @PathVariable String split,
                                 @PathVariable String timestamp) {
        return evaluations.load(version, split, timestamp).json();
    }
}
