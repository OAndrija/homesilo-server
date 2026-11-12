package com.andrija.homesiloserver.bdd;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

public class DashboardSteps {

    private static final String LAYOUT = "/api/v1/dashboard/layout";

    private final World world;

    public DashboardSteps(World world) {
        this.world = world;
    }

    @When("I arrange my dashboard as:")
    public void iArrangeMyDashboardAs(DataTable table) {
        StringBuilder body = new StringBuilder("{\"tiles\":[");
        List<Map<String, String>> rows = table.asMaps();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) body.append(',');
            body.append(World.json("id", rows.get(i).get("tile"), "size", rows.get(i).get("size")));
        }
        world.perform(world.asMe(put(LAYOUT).contentType(MediaType.APPLICATION_JSON).content(body.append("]}").toString())));
    }

    @When("I reset my dashboard")
    public void iResetMyDashboard() {
        assertThat(world.perform(world.asMe(delete(LAYOUT))).getResponse().getStatus()).isEqualTo(204);
    }

    @Then("my dashboard shows:")
    public void myDashboardShows(DataTable table) {
        String body = World.body(layout());
        assertThat(JsonPath.<Boolean>read(body, "$.customized")).isTrue();
        List<Map<String, String>> rows = table.asMaps();
        assertThat(JsonPath.<List<String>>read(body, "$.tiles[*].id"))
                .containsExactlyElementsOf(rows.stream().map(r -> r.get("tile")).toList());
        assertThat(JsonPath.<List<String>>read(body, "$.tiles[*].size"))
                .containsExactlyElementsOf(rows.stream().map(r -> r.get("size")).toList());
    }

    @Then("my dashboard is the default one")
    public void myDashboardIsTheDefaultOne() {
        String body = World.body(layout());
        assertThat(JsonPath.<Boolean>read(body, "$.customized")).isFalse();
        assertThat(JsonPath.<List<Object>>read(body, "$.tiles")).isEmpty();
    }

    private MvcResult layout() {
        MvcResult result = world.perform(world.asMe(get(LAYOUT)));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result;
    }
}
