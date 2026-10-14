package fr.daliush.shardbound.api.dao.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationDaoInMemoryTest {

    @TempDir
    Path reports;

    @Test
    void listsASplitsEvaluationsOldestFirst() throws IOException {
        write("v1/dev/2026-10-15T18-00-00Z.json", "{\"code\": \"b\"}");
        write("v1/dev/2026-10-15T09-00-00Z.json", "{\"code\": \"a\"}");
        write("v1/test/2026-10-16T09-00-00Z.json", "{\"code\": \"c\"}");

        EvaluationDaoInMemory dao = new EvaluationDaoInMemory(reports);

        assertThat(dao.listTimestamps("v1", "dev")).containsExactly("2026-10-15T09-00-00Z", "2026-10-15T18-00-00Z");
        assertThat(dao.listTimestamps("v1", "test")).containsExactly("2026-10-16T09-00-00Z");
        assertThat(dao.listTimestamps("v2", "dev")).isEmpty();
    }

    @Test
    void findsAnEvaluationWithItsDocumentAsWritten() throws IOException {
        write("v1/dev/2026-10-15T09-00-00Z.json", "{\"code\": \"a\"}");

        EvaluationDaoInMemory dao = new EvaluationDaoInMemory(reports);

        assertThat(dao.find("v1", "dev", "2026-10-15T09-00-00Z")).hasValueSatisfying(evaluation -> {
            assertThat(evaluation.version()).isEqualTo("v1");
            assertThat(evaluation.split()).isEqualTo("dev");
            assertThat(evaluation.json()).isEqualTo("{\"code\": \"a\"}");
        });
        assertThat(dao.find("v1", "dev", "2026-10-15T10-00-00Z")).isEmpty();
    }

    @Test
    void readsOnlyTheFilesOfASplitAndAcceptsAFolderThatDoesNotExistYet() throws IOException {
        write("v1/dev.json", "{\"an\": \"older format\"}");

        assertThat(new EvaluationDaoInMemory(reports).listTimestamps("v1", "dev")).isEmpty();
        assertThat(new EvaluationDaoInMemory(reports.resolve("missing")).listTimestamps("v1", "dev")).isEmpty();
    }

    private void write(String path, String json) throws IOException {
        Path file = reports.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json);
    }
}
