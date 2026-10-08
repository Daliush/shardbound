package fr.daliush.shardbound.api.controller.rest;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RestApiTest {

    private static final String SECRET = "[A-Za-z0-9_-]{43}";
    private static final String BOT_GAME = """
            { "deck": "ember-starter", "opponent": { "type": "bot", "bot": "random", "deck": "root-starter" }, "seed": 42 }""";
    private static final String HUMAN_GAME = """
            { "deck": "ember-starter", "opponent": { "type": "human" } }""";

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Test
    void listsTheCardsWithTheirTextAndOptionalFieldsLeftOut() throws Exception {
        mvc.perform(get("/api/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'ember.ash-warden')].cost").value(3))
                .andExpect(jsonPath("$[?(@.id == 'ember.ash-warden')].defense").value(6))
                .andExpect(jsonPath("$[?(@.id == 'ember.ash-warden')].flavor").value("The ashes remember."))
                .andExpect(jsonPath("$[?(@.id == 'root.sprout')].token").value(true))
                .andExpect(jsonPath("$[?(@.id == 'root.sprout')].cost").isEmpty())
                .andExpect(jsonPath("$[?(@.id == 'tide.moonpull')].fracture[*].cost").value(
                        contains(1, 2, 3)))
                .andExpect(jsonPath("$[?(@.id == 'ember.ash-warden')].text[*].text").value(contains(
                        "Cinder Bite (2 Shards): Deal 4 damage to the target. Echo 50.",
                        "Kindle (1 Shard): Give all your units +2/+0 until end of turn.")))
                .andExpect(jsonPath("$[?(@.id == 'ember.ash-warden')].text[*].kind").value(contains("attack", "attack")))
                .andExpect(jsonPath("$[?(@.id == 'ember.cinderling')].text[1].kind").value("ability"))
                .andExpect(jsonPath("$[?(@.id == 'tide.moonpull')].text[0].text").value("Fracture 3."));
    }

    @Test
    void listsTheDecksAndTheBots() throws Exception {
        mvc.perform(get("/api/decks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains("ember-starter", "root-starter", "tide-starter")))
                .andExpect(jsonPath("$[0].faction").value("ember"))
                .andExpect(jsonPath("$[0].cards", hasSize(15)));
        mvc.perform(get("/api/bots"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"random\"]"));
    }

    @Test
    void createsAGameAgainstABotWithoutAJoinCodeOrTheSeed() throws Exception {
        create(BOT_GAME)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value(matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.playerToken").value(matchesPattern(SECRET)))
                .andExpect(jsonPath("$.joinCode").doesNotExist())
                .andExpect(jsonPath("$.seed").doesNotExist())
                .andExpect(jsonPath("$.websocketPath").value(matchesPattern("/ws/games/[0-9a-f-]{36}")));
    }

    @Test
    void createsAGameAgainstAHumanWhoJoinsWithTheCodeOnce() throws Exception {
        JsonNode created = body(create(HUMAN_GAME).andExpect(status().isCreated())
                .andExpect(jsonPath("$.joinCode").value(matchesPattern(SECRET))));
        String id = created.get("gameId").asString();
        String join = "{ \"joinCode\": \"" + created.get("joinCode").asString() + "\", \"deck\": \"root-starter\" }";

        join(id, "{ \"joinCode\": \"wrong\", \"deck\": \"root-starter\" }")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Wrong join code, or the game is already full."));
        join(id, join)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(id))
                .andExpect(jsonPath("$.playerToken").value(matchesPattern(SECRET)))
                .andExpect(jsonPath("$.joinCode").doesNotExist());
        join(id, join).andExpect(status().isConflict());
    }

    @Test
    void reportsErrorsAsProblemDetails() throws Exception {
        create(BOT_GAME.replace("ember-starter", "emberr-starter"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Unknown deck: emberr-starter"))
                .andExpect(jsonPath("$.instance").value("/api/games"));
        create(BOT_GAME.replace("\"random\"", "\"genius\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown bot: genius"));
        create("{ \"deck\": \"ember-starter\" }").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        create("{ \"deck\": \"ember-starter\", \"opponent\": { \"type\": \"alien\" } }")
                .andExpect(status().isBadRequest());
        join("8f2c6d1e-3b7a-4c59-9e10-5a4b2d7f9c31", "{ \"joinCode\": \"x\", \"deck\": \"root-starter\" }")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.instance").value("/api/games/8f2c6d1e-3b7a-4c59-9e10-5a4b2d7f9c31/join"));
        join("not-a-game", "{ \"joinCode\": \"x\", \"deck\": \"root-starter\" }").andExpect(status().isNotFound());
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/games").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions join(String id, String body) throws Exception {
        return mvc.perform(post("/api/games/" + id + "/join").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
