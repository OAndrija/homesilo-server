package com.andrija.homesiloserver.bdd;

import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import com.andrija.homesiloserver.user.UserRole;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class AdminSteps {

    private final World world;
    private final UserRepository users;

    public AdminSteps(World world, UserRepository users) {
        this.world = world;
        this.users = users;
    }

    @Given("I am signed in as {string}, an admin")
    public void iAmSignedInAsAnAdmin(String name) {
        world.signInAs(name);
        // Promoting directly: only the server's very first account starts out as admin.
        User me = users.findById(world.me().id()).orElseThrow();
        me.setRole(UserRole.ADMIN);
        users.save(me);
    }

    @Given("{string} has uploaded {string} containing {string}")
    public void someoneHasUploaded(String name, String fileName, String content) {
        World.Account owner = world.account(name);
        MvcResult result = world.perform(multipart("/api/v1/files/upload")
                .file(new MockMultipartFile("file", fileName, "application/octet-stream",
                        content.getBytes(StandardCharsets.UTF_8)))
                .cookie(owner.cookie()).with(csrf()));
        assertThat(result.getResponse().getStatus()).as("upload").isEqualTo(201);
    }

    @When("I add a user {string}")
    public void iAddAUser(String name) {
        String username = World.uniqueUsername(name);
        MvcResult result = world.perform(world.asMe(post("/api/v1/admin/users"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(World.json("username", username, "email", username + "@example.com",
                        "password", World.PASSWORD, "role", "USER")));
        assertThat(result.getResponse().getStatus()).as("add user: %s", World.body(result)).isEqualTo(201);
        world.remember(new World.Account(name, username, UUID.fromString(World.read(result, "$.id")), null));
    }

    @When("I disable the account of {string}")
    public void iDisable(String name) {
        setEnabled(name, false);
    }

    @When("I enable the account of {string}")
    public void iEnable(String name) {
        setEnabled(name, true);
    }

    @When("I delete the account of {string}")
    public void iDelete(String name) {
        MvcResult result = world.perform(world.asMe(delete("/api/v1/admin/users/{id}", world.account(name).id())));
        assertThat(result.getResponse().getStatus()).as("delete").isEqualTo(204);
    }

    @When("I try to disable my own account")
    public void iTryToDisableMyself() {
        world.perform(world.asMe(patch("/api/v1/admin/users/{id}", world.me().id()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"));
    }

    @When("I open the user list")
    public void iOpenTheUserList() {
        world.perform(world.asMe(get("/api/v1/admin/users")));
    }

    @Then("{string} can sign in with the password I gave them")
    @Then("{string} can sign in")
    public void canSignIn(String name) {
        assertThat(signIn(name).getResponse().getStatus()).isEqualTo(200);
    }

    @Then("{string} can't sign in because {string}")
    public void cantSignIn(String name, String reason) {
        MvcResult result = signIn(name);
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(World.read(result, "$.detail")).isEqualTo(reason);
    }

    @Then("the user list doesn't include {string}")
    public void theUserListDoesntInclude(String name) {
        String username = world.account(name).username();
        MvcResult result = world.perform(world.asMe(get("/api/v1/admin/users").param("query", username)));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<String> usernames = JsonPath.read(World.body(result), "$.content[*].username");
        assertThat(usernames).doesNotContain(username);
    }

    private void setEnabled(String name, boolean enabled) {
        MvcResult result = world.perform(world.asMe(patch("/api/v1/admin/users/{id}", world.account(name).id()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + "}"));
        assertThat(result.getResponse().getStatus()).as("update").isEqualTo(200);
    }

    private MvcResult signIn(String name) {
        return world.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(World.json("username", world.account(name).username(), "password", World.PASSWORD)));
    }
}
