package fr.daliush.shardbound.api.dao.art;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CardArtDaoFileTest {

    @TempDir
    Path root;

    private CardArtDaoFile dao;

    @BeforeEach
    void startWithAnEmptyArtFolder() {
        dao = new CardArtDaoFile(root.resolve("art"));
    }

    @Test
    void findsACardsImageByItsIdWithItsMediaTypeAndBytes() throws IOException {
        write("art/ember.ash-warden.webp", "ash");
        write("art/tide.moonpull.png", "moon");

        assertThat(dao.find("ember.ash-warden")).hasValueSatisfying(art -> {
            assertThat(art.cardId()).isEqualTo("ember.ash-warden");
            assertThat(art.mediaType()).isEqualTo("image/webp");
        });
        assertThat(dao.read("tide.moonpull")).hasValueSatisfying(image -> {
            assertThat(image.art().mediaType()).isEqualTo("image/png");
            assertThat(image.bytes()).isEqualTo("moon".getBytes());
        });
        assertThat(dao.find("root.sprout")).isEmpty();
    }

    @Test
    void changesTheVersionWhenTheImageChanges() throws IOException {
        Path image = write("art/ember.ash-warden.webp", "first");
        String first = dao.find("ember.ash-warden").orElseThrow().version();

        Files.writeString(image, "second, longer");
        Files.setLastModifiedTime(image, FileTime.from(Instant.now().plusSeconds(60)));

        assertThat(dao.find("ember.ash-warden").orElseThrow().version()).isNotEqualTo(first);
    }

    @Test
    void servesAnImagePublishedAfterTheFirstRead() throws IOException {
        assertThat(dao.find("ember.ash-warden")).isEmpty();

        write("art/ember.ash-warden.webp", "ash");

        assertThat(dao.find("ember.ash-warden")).isPresent();
    }

    @Test
    void readsOnlyImagesAtTheTopOfTheFolder() throws IOException {
        write("art/ember.ash-warden.txt", "a prompt");
        write("art/ember/ember.cinderling.webp", "deeper");

        assertThat(dao.find("ember.ash-warden")).isEmpty();
        assertThat(dao.find("ember.cinderling")).isEmpty();
    }

    @Test
    void neverReadsAFileOutsideTheArtFolder() throws IOException {
        write("art/ember.ash-warden.webp", "ash");
        write("secrets/key.webp", "secret");

        assertThat(dao.read("../secrets/key")).isEmpty();
        assertThat(dao.read("..")).isEmpty();
    }

    private Path write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
