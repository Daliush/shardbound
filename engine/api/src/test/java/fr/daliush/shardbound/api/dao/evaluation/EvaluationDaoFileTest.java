package fr.daliush.shardbound.api.dao.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationDaoFileTest {

    @TempDir
    Path root;

    private Path reports;
    private EvaluationDaoFile dao;

    @BeforeEach
    void startWithAnEmptyReportsFolder() {
        reports = root.resolve("reports");
        dao = new EvaluationDaoFile(reports);
    }

    @Test
    void listsASplitsEvaluationsOldestFirst() throws IOException {
        write("reports/v1/dev/2026-10-15T18-00-00Z.json", "{\"code\": \"b\"}");
        write("reports/v1/dev/2026-10-15T09-00-00Z.json", "{\"code\": \"a\"}");
        write("reports/v1/test/2026-10-16T09-00-00Z.json", "{\"code\": \"c\"}");

        assertThat(dao.listTimestamps("v1", "dev")).containsExactly("2026-10-15T09-00-00Z", "2026-10-15T18-00-00Z");
        assertThat(dao.listTimestamps("v1", "test")).containsExactly("2026-10-16T09-00-00Z");
        assertThat(dao.listTimestamps("v2", "dev")).isEmpty();
    }

    @Test
    void findsAnEvaluationWithItsDocumentAsWritten() throws IOException {
        write("reports/v1/dev/2026-10-15T09-00-00Z.json", "{\"code\": \"a\"}");

        assertThat(dao.find("v1", "dev", "2026-10-15T09-00-00Z")).hasValueSatisfying(evaluation -> {
            assertThat(evaluation.version()).isEqualTo("v1");
            assertThat(evaluation.split()).isEqualTo("dev");
            assertThat(evaluation.json()).isEqualTo("{\"code\": \"a\"}");
        });
        assertThat(dao.find("v1", "dev", "2026-10-15T10-00-00Z")).isEmpty();
    }

    @Test
    void servesAnEvaluationSavedAfterItsFirstRead() throws IOException {
        write("reports/v1/dev/2026-10-15T09-00-00Z.json", "{\"code\": \"a\"}");
        assertThat(dao.listTimestamps("v1", "dev")).containsExactly("2026-10-15T09-00-00Z");

        write("reports/v1/dev/2026-10-15T18-00-00Z.json", "{\"code\": \"b\"}");

        assertThat(dao.listTimestamps("v1", "dev")).containsExactly("2026-10-15T09-00-00Z", "2026-10-15T18-00-00Z");
        assertThat(dao.find("v1", "dev", "2026-10-15T18-00-00Z")).isPresent();
    }

    @Test
    void readsOnlyTheFilesOfASplitAndAcceptsAFolderThatDoesNotExistYet() throws IOException {
        assertThat(dao.listTimestamps("v1", "dev")).isEmpty();

        write("reports/v1/dev.json", "{\"an\": \"older format\"}");

        assertThat(dao.listTimestamps("v1", "dev")).isEmpty();
    }

    @Test
    void neverReadsAFileOutsideTheReportsFolder() throws IOException {
        write("reports/v1/dev/2026-10-15T09-00-00Z.json", "{\"code\": \"a\"}");
        write("secrets/key.json", "{\"secret\": true}");

        assertThat(dao.find("..", "secrets", "key")).isEmpty();
        assertThat(dao.listTimestamps("..", "secrets")).isEmpty();
    }

    private void write(String path, String json) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }
}
