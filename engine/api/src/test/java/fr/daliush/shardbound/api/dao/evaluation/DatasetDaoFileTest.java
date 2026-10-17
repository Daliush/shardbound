package fr.daliush.shardbound.api.dao.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetDaoFileTest {

    @TempDir
    Path root;

    private DatasetDaoFile dao;

    @BeforeEach
    void startWithAnEmptyDatasetsFolder() {
        dao = new DatasetDaoFile(root.resolve("datasets"));
    }

    @Test
    void findsASplitWithItsLinesAsWritten() throws IOException {
        write("datasets/v2/dataset-dev.jsonl", "{\"id\": \"a\"}\n{\"id\": \"b\"}\n");
        write("datasets/v2/dataset-test.jsonl", "{\"id\": \"c\"}\n");

        assertThat(dao.find("v2", "dev")).hasValueSatisfying(dataset -> {
            assertThat(dataset.version()).isEqualTo("v2");
            assertThat(dataset.split()).isEqualTo("dev");
            assertThat(dataset.jsonl()).isEqualTo("{\"id\": \"a\"}\n{\"id\": \"b\"}\n");
        });
        assertThat(dao.find("v2", "test")).isPresent();
        assertThat(dao.find("v1", "dev")).isEmpty();
    }

    @Test
    void servesAVersionBuiltAfterTheFirstRead() throws IOException {
        assertThat(dao.find("v3", "dev")).isEmpty();

        write("datasets/v3/dataset-dev.jsonl", "{\"id\": \"a\"}\n");

        assertThat(dao.find("v3", "dev")).isPresent();
    }

    @Test
    void readsOnlyTheFinalSplitsOfAVersion() throws IOException {
        write("datasets/v2/questions.jsonl", "{\"id\": \"engine\"}\n");
        write("datasets/v2/hand-check.jsonl", "{\"id\": \"hand\"}\n");
        write("datasets/v2/nested/dataset-dev.jsonl", "{\"id\": \"deeper\"}\n");

        assertThat(dao.find("v2", "dev")).isEmpty();
        assertThat(dao.find("v2", "questions")).isEmpty();
    }

    @Test
    void neverReadsAFileOutsideTheDatasetsFolder() throws IOException {
        write("datasets/v2/dataset-dev.jsonl", "{\"id\": \"a\"}\n");
        write("secrets/dataset-key.jsonl", "{\"secret\": true}\n");

        assertThat(dao.find("..", "key")).isEmpty();
        assertThat(dao.find("../secrets", "key")).isEmpty();
    }

    private void write(String path, String jsonl) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, jsonl);
    }
}
