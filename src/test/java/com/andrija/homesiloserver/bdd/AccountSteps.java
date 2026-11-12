package com.andrija.homesiloserver.bdd;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class AccountSteps {

    private final World world;

    public AccountSteps(World world) {
        this.world = world;
    }

    @Given("I am signed in as {string}")
    public void iAmSignedInAs(String name) {
        world.signInAs(name);
    }

    @Given("{string} has an account")
    public void hasAnAccount(String name) {
        world.account(name);
    }

    @When("I sign up as {string}")
    public void iSignUpAs(String name) {
        assertThat(world.signUp(name)).as("sign-up").isNotNull();
        world.signInAs(name);
    }

    @When("someone else signs up as {string}")
    public void someoneElseSignsUpAs(String name) {
        String taken = world.account(name).username();
        world.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(World.json("username", taken, "email", "someone-else-" + taken + "@example.com",
                        "password", World.PASSWORD)));
    }

    @When("someone tries to sign in as {string} with a wrong password {int} times")
    public void wrongPasswordTimes(String name, int times) {
        String username = world.account(name).username();
        for (int i = 0; i < times; i++) {
            MvcResult result = signIn(username, "not-the-password");
            assertThat(result.getResponse().getStatus()).as("wrong password").isEqualTo(401);
        }
    }

    @Then("I am signed in to my own account")
    public void iAmSignedInToMyOwnAccount() {
        MvcResult result = world.perform(get("/api/v1/users/me").cookie(world.me().cookie()));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(World.read(result, "$.username")).isEqualTo(world.me().username());
    }

    @Then("{string} can't sign in for a while, even with the right password")
    public void cantSignInForAWhile(String name) {
        MvcResult result = signIn(world.account(name).username(), World.PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(result.getResponse().getHeader("Retry-After")).isNotBlank();
    }

    @Then("it fails with {string}")
    public void itFailsWith(String message) {
        MvcResult result = world.last();
        assertThat(result.getResponse().getStatus()).as("status").isBetween(400, 499);
        assertThat(World.read(result, "$.detail")).contains(message);
    }

    private MvcResult signIn(String username, String password) {
        return world.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(World.json("username", username, "password", password)));
    }
}
