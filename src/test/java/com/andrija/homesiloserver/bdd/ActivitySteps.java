package com.andrija.homesiloserver.bdd;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ActivitySteps {

    /** The verbs scenarios use, and the log action each one means. */
    private static final Map<String, String> VERBS = Map.of(
            "uploaded", "FILE_UPLOADED",
            "renamed", "FILE_RENAMED",
            "trashed", "FILE_TRASHED",
            "disabled", "USER_DISABLED",
            "enabled", "USER_ENABLED");

    private final World world;

    public ActivitySteps(World world) {
        this.world = world;
    }

    @When("I look at the activity of {string}")
    public void iLookAtTheActivityOf(String name) {
        MvcResult result = world.perform(world.asMe(get("/api/v1/admin/activity")
                .param("userId", world.account(name).id().toString())));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Then("the latest entry says {string} {word} {string}")
    public void theLatestEntrySays(String actor, String verb, String target) {
        String body = World.body(world.last());
        assertThat(VERBS).as("known verb").containsKey(verb);
        assertThat(JsonPath.<String>read(body, "$.content[0].action")).isEqualTo(VERBS.get(verb));
        assertThat(JsonPath.<String>read(body, "$.content[0].actorUsername")).isEqualTo(world.account(actor).username());
        String expectedTarget = world.hasAccount(target) ? world.account(target).username() : target;
        assertThat(JsonPath.<String>read(body, "$.content[0].targetName")).isEqualTo(expectedTarget);
    }

    @Then("my recent activity starts with {int} failed sign-ins")
    public void myRecentActivityStartsWithFailedSignIns(int count) {
        MvcResult result = world.perform(world.asMe(get("/api/v1/users/me/activity")));
        List<String> actions = JsonPath.read(World.body(result), "$.content[*].action");
        assertThat(actions).hasSizeGreaterThan(count);
        assertThat(actions.subList(0, count)).containsOnly("SIGN_IN_FAILED");
    }
}
