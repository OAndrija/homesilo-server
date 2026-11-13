package com.andrija.homesiloserver.bdd;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class InviteSteps {

    private final World world;
    /** Invites by their note: id and the link's secret. */
    private final Map<String, String[]> invites = new HashMap<>();
    private String lastToken;

    public InviteSteps(World world) {
        this.world = world;
    }

    @When("I make an invite for {string}")
    public void iMakeAnInviteFor(String note) {
        MvcResult result = world.perform(world.asMe(post("/api/v1/admin/invites"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(World.json("note", note)));
        assertThat(result.getResponse().getStatus()).as("make invite").isEqualTo(201);
        lastToken = World.read(result, "$.token");
        invites.put(note, new String[]{World.read(result, "$.invite.id"), lastToken});
    }

    @When("I revoke the invite for {string}")
    public void iRevokeTheInviteFor(String note) {
        MvcResult result = world.perform(world.asMe(delete("/api/v1/admin/invites/{id}", invites.get(note)[0])));
        assertThat(result.getResponse().getStatus()).as("revoke").isEqualTo(204);
    }

    @When("{string} signs up with the invite")
    public void signsUpWithTheInvite(String name) {
        world.signUp(name, lastToken);
    }

    @Then("the invite for {string} has been used by {string}")
    public void theInviteHasBeenUsedBy(String note, String name) {
        MvcResult result = world.perform(world.asMe(get("/api/v1/admin/invites")));
        List<Map<String, Object>> matching = JsonPath.read(World.body(result),
                "$[?(@.id == '" + invites.get(note)[0] + "')]");
        assertThat(matching).hasSize(1);
        assertThat(matching.get(0)).containsEntry("status", "USED")
                .containsEntry("usedBy", world.account(name).username());
    }
}
