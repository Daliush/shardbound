package fr.daliush.shardbound.api.controller.rest;

import fr.daliush.shardbound.api.domain.services.evaluation.DatasetService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Arbiter's evaluation datasets for the benchmark explorer, which shows each question beside its results. */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasets;

    public DatasetController(DatasetService datasets) {
        this.datasets = datasets;
    }

    /** The split as the dataset wrote it: JSON Lines, one question per line, with its wording and answer key. */
    @GetMapping(value = "/{version}/{split}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public String findDataset(@PathVariable String version, @PathVariable String split) {
        return datasets.load(version, split).jsonl();
    }
}
