package com.andrija.homesiloserver.bdd;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

public class StarredSteps {

    private final World world;

    public StarredSteps(World world) {
        this.world = world;
    }

    @Given("I have starred {string}")
    public void iHaveStarred(String name) {
        assertThat(star(name).getResponse().getStatus()).as("star").isEqualTo(200);
    }

    @When("I star {string}")
    public void iStar(String name) {
        star(name);
    }

    @Then("Starred shows {string}")
    public void starredShows(String name) {
        assertThat(starred()).contains(name);
    }

    @Then("Starred shows {string} and {string}")
    public void starredShowsBoth(String first, String second) {
        assertThat(starred()).contains(first, second);
    }

    @Then("Starred does not show {string}")
    public void starredDoesNotShow(String name) {
        assertThat(starred()).doesNotContain(name);
    }

    @Then("Starred is empty")
    public void starredIsEmpty() {
        assertThat(starred()).isEmpty();
    }

    private MvcResult star(String name) {
        return world.isFolder(name)
                ? world.perform(world.asMe(patch("/api/v1/folders/{id}/star", world.folderId(name))))
                : world.perform(world.asMe(patch("/api/v1/files/{id}/star", world.fileId(name))));
    }

    /** What the Starred page lists: starred folders, then starred files. */
    private List<String> starred() {
        List<String> names = new ArrayList<>(JsonPath.<List<String>>read(
                World.body(world.perform(world.asMe(get("/api/v1/folders/starred")))), "$[*].name"));
        names.addAll(JsonPath.<List<String>>read(
                World.body(world.perform(world.asMe(get("/api/v1/files/starred")))), "$.content[*].originalFileName"));
        return names;
    }
}
