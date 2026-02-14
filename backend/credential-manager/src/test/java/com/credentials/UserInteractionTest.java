package com.credentials;

import com.credentials.bootstrap.RequestContextHolder;
import com.credentials.dto.LoginResponse;
import com.credentials.dto.RequestUserContext;
import com.credentials.dto.UserLoginRequest;
import com.credentials.entity.User;
import com.credentials.service.UserService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInteractionTest extends BaseIntegrationTest {


    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @AfterEach
    void cleanUp() {
        super.cleanUp();
    }

    @Test
    @DisplayName("Context loads successfully with TestContainers")
    void contextLoads() {
        assertNotNull(userService);
        assertNotNull(userRepository);
        assertNotNull(organizationRepository);
    }

    @Test
    @DisplayName("First-time user login: User is created and associated with selected organizations")
    @Transactional
    void testFirstTimeUserLoginWithOrganizationSelection() {
        // Arrange
        String subjectId = "user-001-first-time";
        String email = "john@example.com";
        String firstName = "John";
        String lastName = "Doe";

        RequestUserContext context = RequestUserContext.builder()
                .subjectId(subjectId)
                .email(email)
                .build();
        RequestContextHolder.set(context);

        UserLoginRequest request = new UserLoginRequest(firstName, lastName, List.of(org1.getId(), org2.getId()));

        // Act
        LoginResponse response = userService.processUserLogin(request);

        // Assert - User created successfully
        assertNotNull(response);
        assertEquals(email, response.getEmail());
        assertTrue(response.isFirstLogin());
        assertTrue(response.isRequiresOrgSelection());

        // Verify user is persisted in database
        Optional<User> createdUser = userRepository.findBySubjectId(subjectId);
        assertTrue(createdUser.isPresent(), "User should be created in database");

        User user = createdUser.get();
        assertEquals(firstName, user.getFirstName());
        assertEquals(lastName, user.getLastName());
        assertEquals(email, user.getEmail());

        // Verify user is associated with selected organizations
        assertEquals(2, user.getOrganizations().size(), "User should be associated with 2 organizations");
        assertTrue(user.getOrganizations().contains(org1), "User should be associated with org1");
        assertTrue(user.getOrganizations().contains(org2), "User should be associated with org2");
        assertFalse(user.getOrganizations().contains(org3), "User should NOT be associated with org3");
    }

    @Test
    @DisplayName("First-time user login: User selects single organization")
    @Transactional
    void testFirstTimeUserLoginWithSingleOrganization() {
        // Arrange
        String subjectId = "user-002-single-org";
        String email = "jane@example.com";

        RequestUserContext context = RequestUserContext.builder()
                .subjectId(subjectId)
                .email(email)
                .build();
        RequestContextHolder.set(context);

        UserLoginRequest request = new UserLoginRequest("Jane", "Smith", List.of(org1.getId()));

        // Act
        LoginResponse response = userService.processUserLogin(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isFirstLogin());

        // Verify user is associated with only one organization
        Optional<User> createdUser = userRepository.findBySubjectId(subjectId);
        assertTrue(createdUser.isPresent());
        assertEquals(1, createdUser.get().getOrganizations().size());
        assertEquals(org1.getId(), createdUser.get().getOrganizations().iterator().next().getId());
    }

    @Test
    @DisplayName("Returning user with single organization: Seamless login without selection prompt")
    @Transactional
    void testReturningUserWithSingleOrganizationSeamlessLogin() {
        // Arrange - Create user with single organization
        User existingUser = createUserWithOrganizations("user-003-returning", "bob@example.com", "Bob", "Johnson", Set.of(org1));

        RequestUserContext context = RequestUserContext.builder()
                .subjectId(existingUser.getSubjectId())
                .email(existingUser.getEmail())
                .build();
        RequestContextHolder.set(context);

        UserLoginRequest request = new UserLoginRequest("Bob", "Johnson", List.of());

        // Act
        LoginResponse response = userService.processUserLogin(request);

        // Assert - No org selection required for seamless login
        assertNotNull(response);
        assertFalse(response.isFirstLogin(), "Should not be first login");
        assertFalse(response.isRequiresOrgSelection(), "Should not require org selection for single org user");
        assertNotNull(response.getAssociatedOrgs(), "Should return associated organizations");
        assertEquals(1, response.getAssociatedOrgs().size());
        assertEquals(org1.getId().toString(), response.getAssociatedOrgs().getFirst().id());
    }

    @Test
    @DisplayName("Returning user with multiple organizations: Requires selection")
    @Transactional
    void testReturningUserWithMultipleOrganizationsRequiresSelection() {
        // Arrange - Create user with multiple organizations
        User existingUser = createUserWithOrganizations("user-004-multi-org", "alice@example.com", "Alice", "Brown",
                Set.of(org1, org2, org3));

        RequestUserContext context = RequestUserContext.builder()
                .subjectId(existingUser.getSubjectId())
                .email(existingUser.getEmail())
                .build();
        RequestContextHolder.set(context);

        UserLoginRequest request = new UserLoginRequest("Alice", "Brown", List.of());

        // Act
        LoginResponse response = userService.processUserLogin(request);

        // Assert - Org selection should be required
        assertNotNull(response);
        assertFalse(response.isFirstLogin(), "Should not be first login");
        assertTrue(response.isRequiresOrgSelection(), "Should require org selection for multi-org user");
        assertNotNull(response.getAvailableOrgs(), "Should return available organizations");
        assertEquals(3, response.getAvailableOrgs().size(), "User should have access to 3 organizations");
    }

    @Test
    @DisplayName("Multiple users with different organization associations")
    @Transactional
    void testMultipleUsersWithDifferentOrgAssociations() {
        // Arrange - Create multiple users with different org associations
        User user1 = createUserWithOrganizations("user-multi-1", "user1@example.com", "User", "One", Set.of(org1));
        User user2 = createUserWithOrganizations("user-multi-2", "user2@example.com", "User", "Two", Set.of(org1, org2));
        User user3 = createUserWithOrganizations("user-multi-3", "user3@example.com", "User", "Three", Set.of(org2, org3));

        // Act & Assert - Verify each user has correct org associations
        assertEquals(1, user1.getOrganizations().size());
        assertEquals(2, user2.getOrganizations().size());
        assertEquals(2, user3.getOrganizations().size());

        // Verify org1 has 2 users
        long org1UserCount = userRepository.findAll().stream()
                .filter(u -> u.getOrganizations().contains(org1))
                .count();
        assertEquals(2, org1UserCount);

        // Verify org2 has 2 users
        long org2UserCount = userRepository.findAll().stream()
                .filter(u -> u.getOrganizations().contains(org2))
                .count();
        assertEquals(2, org2UserCount);

        // Verify org3 has 1 user
        long org3UserCount = userRepository.findAll().stream()
                .filter(u -> u.getOrganizations().contains(org3))
                .count();
        assertEquals(1, org3UserCount);
    }
}
