package com.andrija.homesiloserver.bdd;

import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** The trash and the storage it takes up. */
public class TrashSteps {

    private final World world;
    private final UserRepository users;

    public TrashSteps(World world, UserRepository users) {
        this.world = world;
        this.users = users;
    }

    @Given("my storage limit is {int} bytes")
    public void myStorageLimitIs(int bytes) {
        // Set directly: changing limits is an admin action, not part of these stories.
        User me = users.findById(world.me().id()).orElseThrow();
        me.setStorageQuotaBytes(bytes);
        users.save(me);
    }

    @Given("I have moved {string} to the trash")
    public void iHaveMovedToTheTrash(String name) {
        assertThat(moveToTrash(name).getResponse().getStatus()).as("trash").isEqualTo(200);
    }

    @When("I move {string} to the trash")
    public void iMoveToTheTrash(String name) {
        moveToTrash(name);
    }

    @When("I restore {string}")
    public void iRestore(String name) {
        MvcResult result = world.isFolder(name)
                ? world.perform(world.asMe(patch("/api/v1/folders/{id}/restore", world.folderId(name))))
                : world.perform(world.asMe(patch("/api/v1/files/{id}/restore", world.fileId(name))));
        assertThat(result.getResponse().getStatus()).as("restore").isEqualTo(200);
    }

    @When("I delete {string} forever")
    public void iDeleteForever(String name) {
        world.perform(world.asMe(delete("/api/v1/files/{id}", world.fileId(name))));
    }

    @When("I empty the trash")
    public void iEmptyTheTrash() {
        assertThat(world.perform(world.asMe(delete("/api/v1/trash"))).getResponse().getStatus())
                .as("empty trash").isEqualTo(200);
    }

    @Then("the trash contains {string}")
    public void theTrashContains(String name) {
        assertThat(trash()).contains(name);
    }

    @Then("the trash is empty")
    public void theTrashIsEmpty() {
        assertThat(trash()).isEmpty();
    }

    @Then("I am using {long} bytes of storage")
    public void iAmUsingBytes(long bytes) {
        MvcResult result = world.perform(world.asMe(get("/api/v1/dashboard/stats")));
        assertThat(Long.parseLong(World.read(result, "$.storageUsedBytes"))).isEqualTo(bytes);
    }

    private MvcResult moveToTrash(String name) {
        return world.isFolder(name)
                ? world.perform(world.asMe(patch("/api/v1/folders/{id}/trash", world.folderId(name))))
                : world.perform(world.asMe(patch("/api/v1/files/{id}/trash", world.fileId(name))));
    }

    /** What the Trash page lists: top-level trashed folders and loose trashed files. */
    private List<String> trash() {
        List<String> names = new ArrayList<>();
        names.addAll(JsonPath.<List<String>>read(
                World.body(world.perform(world.asMe(get("/api/v1/folders/trash")))), "$.content[*].name"));
        names.addAll(JsonPath.<List<String>>read(
                World.body(world.perform(world.asMe(get("/api/v1/files/trash")))), "$.content[*].originalFileName"));
        return names;
    }
}
