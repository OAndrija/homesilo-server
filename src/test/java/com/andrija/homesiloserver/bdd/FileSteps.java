package com.andrija.homesiloserver.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class FileSteps {

    private final World world;
    private String renamedFrom;
    private String renamedTo;

    public FileSteps(World world) {
        this.world = world;
    }

    @Given("I have a folder {string}")
    public void iHaveAFolder(String name) {
        MvcResult result = world.perform(world.asMe(post("/api/v1/folders"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(World.json("name", name, "parentId", null)));
        assertThat(result.getResponse().getStatus()).as("create folder").isEqualTo(201);
        world.rememberFolder(name, World.read(result, "$.id"));
    }

    @Given("I have uploaded {string} containing {string}")
    public void iHaveUploaded(String fileName, String content) {
        assertUploaded(world.upload(fileName, content, null));
    }

    @Given("I have uploaded {string} containing {string} into {string}")
    public void iHaveUploadedInto(String fileName, String content, String folder) {
        assertUploaded(world.upload(fileName, content, folder));
    }

    @When("I upload {string} containing {string}")
    public void iUpload(String fileName, String content) {
        world.upload(fileName, content, null);
    }

    @When("I upload {string} containing {string} into {string}")
    public void iUploadInto(String fileName, String content, String folder) {
        world.upload(fileName, content, folder);
    }

    @When("I rename {string} to {string}")
    public void iRename(String from, String to) {
        renamedFrom = from;
        renamedTo = to;
        MvcResult result = world.perform(world.asMe(patch("/api/v1/files/{id}/rename", world.fileId(from)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(World.json("name", to)));
        if (result.getResponse().getStatus() == 200) world.renamedFile(from, to);
    }

    @Then("the rename is {word}")
    public void theRenameIs(String outcome) {
        int status = world.last().getResponse().getStatus();
        switch (outcome) {
            case "allowed" -> {
                assertThat(status).as("rename status").isEqualTo(200);
                assertThat(myFiles()).contains(renamedTo).doesNotContain(renamedFrom);
            }
            case "refused" -> {
                assertThat(status).as("rename status").isEqualTo(400);
                assertThat(myFiles()).contains(renamedFrom).doesNotContain(renamedTo);
            }
            default -> throw new IllegalArgumentException("Expected allowed or refused, got " + outcome);
        }
    }

    @Then("My Files contains {string}")
    public void myFilesContains(String name) {
        assertThat(myFiles()).contains(name);
    }

    @Then("My Files does not contain {string}")
    public void myFilesDoesNotContain(String name) {
        assertThat(myFiles()).doesNotContain(name);
    }

    @Then("My Files is empty")
    public void myFilesIsEmpty() {
        assertThat(myFiles()).isEmpty();
    }

    @Then("the folder {string} contains {string}")
    public void theFolderContains(String folder, String name) {
        MvcResult result = world.perform(world.asMe(get("/api/v1/folders/{id}/contents", world.folderId(folder))));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(World.namesIn(result)).contains(name);
    }

    @Then("downloading {string} gives {string}")
    public void downloadingGives(String name, String content) {
        MvcResult result = world.perform(world.asMe(get("/api/v1/files/{id}/download", world.fileId(name))));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(World.body(result)).isEqualTo(content);
    }

    @Then("{string} can't open my file {string}")
    public void cantOpenMyFile(String other, String name) {
        String id = world.fileId(name);
        World.Account stranger = world.account(other);
        for (String path : new String[] {"/api/v1/files/{id}/download", "/api/v1/files/{id}/metadata"}) {
            MvcResult result = world.perform(get(path, id).cookie(stranger.cookie()));
            // 404, not 403: a stranger can't even tell that the file exists.
            assertThat(result.getResponse().getStatus()).as(path).isEqualTo(404);
        }
    }

    private java.util.List<String> myFiles() {
        MvcResult result = world.perform(world.asMe(get("/api/v1/folders/root").param("size", "100")));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return World.namesIn(result);
    }

    private static void assertUploaded(MvcResult result) {
        assertThat(result.getResponse().getStatus()).as("upload: %s", World.body(result)).isEqualTo(201);
    }
}
