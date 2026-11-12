Feature: Starred
  Starring files and folders keeps them one click away on the Starred page.

  Background:
    Given I am signed in as "ana"

  Scenario: Starred files and folders show up together
    Given I have a folder "Taxes"
    And I have uploaded "receipt.pdf" containing "paid"
    And I have uploaded "notes.txt" containing "not important"
    When I star "Taxes"
    And I star "receipt.pdf"
    Then Starred shows "Taxes" and "receipt.pdf"
    And Starred does not show "notes.txt"

  Scenario: Starring again removes the star
    Given I have a folder "Taxes"
    When I star "Taxes"
    And I star "Taxes"
    Then Starred is empty

  Scenario: A starred folder leaves Starred while it is in the trash
    Given I have a folder "Taxes"
    And I have starred "Taxes"
    When I move "Taxes" to the trash
    Then Starred is empty
    When I restore "Taxes"
    Then Starred shows "Taxes"
