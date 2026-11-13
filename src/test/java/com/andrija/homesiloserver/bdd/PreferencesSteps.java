package com.andrija.homesiloserver.bdd;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

public class PreferencesSteps {

    private static final String PREFERENCES = "/api/v1/users/me/preferences";

    private final World world;

    public PreferencesSteps(World world) {
        this.world = world;
    }

    @When("I change my settings:")
    public void iChangeMySettings(DataTable table) {
        send(World.json(table.asMaps().stream()
                .flatMap(row -> Stream.of(row.get("setting"), row.get("value")))
                .toArray()));
    }

    @When("I reset the setting {string}")
    public void iResetTheSetting(String setting) {
        send(World.json(setting, null));
        assertThat(world.last().getResponse().getStatus()).isEqualTo(200);
    }

    @Then("my settings are:")
    public void mySettingsAre(DataTable table) {
        assertThat(values()).containsExactlyInAnyOrderEntriesOf(table.asMaps().stream()
                .collect(Collectors.toMap(row -> row.get("setting"), row -> row.get("value"))));
    }

    @Then("I have no changed settings")
    public void iHaveNoChangedSettings() {
        assertThat(values()).isEmpty();
    }

    /** Sends {@code values}, a JSON object of settings, as a change. */
    private void send(String values) {
        world.perform(world.asMe(patch(PREFERENCES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":" + values + "}")));
    }

    private Map<String, String> values() {
        MvcResult result = world.perform(world.asMe(get(PREFERENCES)));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(World.body(result), "$.values");
    }
}
