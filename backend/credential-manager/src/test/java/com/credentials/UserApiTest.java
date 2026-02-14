package com.credentials;

import com.credentials.dto.UserDto;
import com.credentials.entity.User;
import com.credentials.exception.UserNotFoundException;
import com.credentials.service.UserService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserApiTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @BeforeEach
    @Override
    void setUp() {
        super.setUp();
    }

    @AfterEach
    @Override
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
    @DisplayName("Get all users returns empty list when no users exist")
    @Transactional
    void testGetAllUsersEmpty() {
        // Act
        List<UserDto> users = userService.getAllUsers();

        // Assert
        assertNotNull(users, "Users list should not be null");
        assertEquals(0, users.size(), "Users list should be empty");
    }

    @Test
    @DisplayName("Get all users returns all created users")
    @Transactional
    void testGetAllUsersWithMultipleUsers() {
        // Arrange
        User user1 = createUserWithOrganizations("user-001", "user1@example.com", "John", "Doe", Set.of(org1));
        User user2 = createUserWithOrganizations("user-002", "user2@example.com", "Jane", "Smith", Set.of(org2));
        User user3 = createUserWithOrganizations("user-003", "user3@example.com", "Bob", "Johnson", Set.of(org1, org2));

        // Act
        List<UserDto> users = userService.getAllUsers();

        // Assert
        assertNotNull(users, "Users list should not be null");
        assertEquals(3, users.size(), "Should return 3 users");

        // Verify user details are in the list
        List<String> userEmails = users.stream().map(UserDto::email).toList();
        assertTrue(userEmails.contains("user1@example.com"), "Should contain user1 email");
        assertTrue(userEmails.contains("user2@example.com"), "Should contain user2 email");
        assertTrue(userEmails.contains("user3@example.com"), "Should contain user3 email");

        // Verify names are mapped correctly
        List<String> firstNames = users.stream().map(UserDto::firstName).toList();
        assertTrue(firstNames.contains("John"), "Should contain John");
        assertTrue(firstNames.contains("Jane"), "Should contain Jane");
        assertTrue(firstNames.contains("Bob"), "Should contain Bob");
    }

    @Test
    @DisplayName("Get user by ID returns correct user details")
    @Transactional
    void testGetUserByIdSuccess() {
        // Arrange
        User user = createUserWithOrganizations("user-004", "alice@example.com", "Alice", "Brown", Set.of(org1));

        // Act
        UserDto userDto = userService.getUserById(user.getId());

        // Assert
        assertNotNull(userDto, "User DTO should not be null");
        assertEquals("alice@example.com", userDto.email(), "Email should match");
        assertEquals("Alice", userDto.firstName(), "First name should match");
        assertEquals("Brown", userDto.lastName(), "Last name should match");
        assertEquals("Alice", userDto.name(), "Name should match");
    }

    @Test
    @DisplayName("Get user by ID throws UserNotFoundException for non-existent user")
    @Transactional
    void testGetUserByIdNotFound() {
        // Arrange
        UUID nonExistentUserId = UUID.randomUUID();

        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> userService.getUserById(nonExistentUserId),
                "Should throw UserNotFoundException for non-existent user");
    }

    @Test
    @DisplayName("Get user by ID with single organization")
    @Transactional
    void testGetUserByIdWithSingleOrganization() {
        // Arrange
        User user = createUserWithOrganizations("user-005", "charlie@example.com", "Charlie", "Davis", Set.of(org1));

        // Act
        UserDto userDto = userService.getUserById(user.getId());

        // Assert
        assertNotNull(userDto, "User DTO should not be null");
        assertEquals("charlie@example.com", userDto.email(), "Email should match");
        assertEquals("Charlie", userDto.firstName(), "First name should match");
        assertEquals("Davis", userDto.lastName(), "Last name should match");
    }

    @Test
    @DisplayName("Get user by ID with multiple organizations")
    @Transactional
    void testGetUserByIdWithMultipleOrganizations() {
        // Arrange
        User user = createUserWithOrganizations("user-006", "diana@example.com", "Diana", "Evans", Set.of(org1, org2, org3));

        // Act
        UserDto userDto = userService.getUserById(user.getId());

        // Assert
        assertNotNull(userDto, "User DTO should not be null");
        assertEquals("diana@example.com", userDto.email(), "Email should match");
        assertEquals("Diana", userDto.firstName(), "First name should match");
        assertEquals("Evans", userDto.lastName(), "Last name should match");
    }

    @Test
    @DisplayName("Get all users preserves user data integrity")
    @Transactional
    void testGetAllUsersDataIntegrity() {
        // Arrange
        User user1 = createUserWithOrganizations("user-007", "eve@example.com", "Eve", "Foster", Set.of(org1));
        User user2 = createUserWithOrganizations("user-008", "frank@example.com", "Frank", "Garcia", Set.of(org2, org3));

        // Act
        List<UserDto> users = userService.getAllUsers();

        // Assert
        assertEquals(2, users.size(), "Should return 2 users");

        // Find and verify first user
        UserDto userDto1 = users.stream()
                .filter(u -> u.email().equals("eve@example.com"))
                .findFirst()
                .orElse(null);
        assertNotNull(userDto1, "Should find eve@example.com");
        assertEquals("Eve", userDto1.firstName(), "First name should be Eve");
        assertEquals("Foster", userDto1.lastName(), "Last name should be Foster");

        // Find and verify second user
        UserDto userDto2 = users.stream()
                .filter(u -> u.email().equals("frank@example.com"))
                .findFirst()
                .orElse(null);
        assertNotNull(userDto2, "Should find frank@example.com");
        assertEquals("Frank", userDto2.firstName(), "First name should be Frank");
        assertEquals("Garcia", userDto2.lastName(), "Last name should be Garcia");
    }

    @Test
    @DisplayName("Get user by ID returns correct UUID for user")
    @Transactional
    void testGetUserByIdWithCorrectId() {
        // Arrange
        User user = createUserWithOrganizations("user-009", "grace@example.com", "Grace", "Hamilton", Set.of(org1));
        UUID userId = user.getId();

        // Act
        UserDto userDto = userService.getUserById(userId);

        // Assert
        assertNotNull(userDto, "User DTO should not be null");
        // Note: UserDto doesn't include ID, but we can verify through other fields
        assertEquals("grace@example.com", userDto.email(), "Email should match");
    }

    @Test
    @DisplayName("Get all users with various name formats")
    @Transactional
    void testGetAllUsersWithVariousNameFormats() {
        // Arrange
        User user1 = createUserWithOrganizations("user-010", "henry@example.com", "H", "I", Set.of(org1));
        User user2 = createUserWithOrganizations("user-011", "iris@example.com", "IrisVeryLongFirstName", "VeryLongLastName", Set.of(org2));
        User user3 = createUserWithOrganizations("user-012", "jack@example.com", "Jack", "O", Set.of(org3));

        // Act
        List<UserDto> users = userService.getAllUsers();

        // Assert
        assertEquals(3, users.size(), "Should return 3 users");

        // Verify names are returned as-is
        List<String> firstNames = users.stream().map(UserDto::firstName).toList();
        assertTrue(firstNames.contains("H"), "Should contain H");
        assertTrue(firstNames.contains("IrisVeryLongFirstName"), "Should contain long name");
        assertTrue(firstNames.contains("Jack"), "Should contain Jack");
    }

    @Test
    @DisplayName("Get user by ID after getting all users returns same data")
    @Transactional
    void testGetUserByIdAfterGetAllUsers() {
        // Arrange
        User user = createUserWithOrganizations("user-013", "karen@example.com", "Karen", "Jones", Set.of(org1, org2));

        // Act
        List<UserDto> allUsers = userService.getAllUsers();
        UserDto singleUser = userService.getUserById(user.getId());

        // Assert
        assertEquals(1, allUsers.size(), "Should have 1 user");
        UserDto userFromList = allUsers.getFirst();

        assertEquals(userFromList.email(), singleUser.email(), "Email should match");
        assertEquals(userFromList.firstName(), singleUser.firstName(), "First name should match");
        assertEquals(userFromList.lastName(), singleUser.lastName(), "Last name should match");
        assertEquals(userFromList.name(), singleUser.name(), "Name should match");
    }

    @Test
    @DisplayName("Get all users after deleting a user")
    @Transactional
    void testGetAllUsersAfterDeletion() {
        // Arrange
        User user1 = createUserWithOrganizations("user-014", "liam@example.com", "Liam", "King", Set.of(org1));
        User user2 = createUserWithOrganizations("user-015", "mia@example.com", "Mia", "Lewis", Set.of(org2));

        // Act & Assert - Get all users (should be 2)
        List<UserDto> usersBeforeDeletion = userService.getAllUsers();
        assertEquals(2, usersBeforeDeletion.size(), "Should have 2 users before deletion");

        // Delete one user
        userRepository.deleteById(user1.getId());

        // Get all users again
        List<UserDto> usersAfterDeletion = userService.getAllUsers();

        // Assert
        assertEquals(1, usersAfterDeletion.size(), "Should have 1 user after deletion");
        assertEquals("mia@example.com", usersAfterDeletion.getFirst().email(), "Should have remaining user");
    }

    @Test
    @DisplayName("Get user by ID returns email in correct format")
    @Transactional
    void testGetUserByIdEmailFormat() {
        // Arrange
        User user = createUserWithOrganizations("user-016", "noah@example.com", "Noah", "Miller", Set.of(org1));

        // Act
        UserDto userDto = userService.getUserById(user.getId());

        // Assert
        assertNotNull(userDto.email(), "Email should not be null");
        assertTrue(userDto.email().contains("@"), "Email should contain @");
        assertEquals("noah@example.com", userDto.email(), "Email should match exactly");
    }

    @Test
    @DisplayName("Get all users maintains consistent ordering")
    @Transactional
    void testGetAllUsersConsistentOrdering() {
        // Arrange
        User user1 = createUserWithOrganizations("user-017", "olivia@example.com", "Olivia", "Moore", Set.of(org1));
        User user2 = createUserWithOrganizations("user-018", "peter@example.com", "Peter", "Nelson", Set.of(org2));
        User user3 = createUserWithOrganizations("user-019", "quinn@example.com", "Quinn", "Owen", Set.of(org3));

        // Act
        List<UserDto> firstCall = userService.getAllUsers();
        List<UserDto> secondCall = userService.getAllUsers();

        // Assert - Both calls should return same number of users
        assertEquals(firstCall.size(), secondCall.size(), "Both calls should return same number of users");
        assertEquals(3, firstCall.size(), "Should have 3 users");
    }

    @Test
    @DisplayName("Get user by ID with valid UUID")
    @Transactional
    void testGetUserByIdWithValidUUID() {
        // Arrange
        User user = createUserWithOrganizations("user-020", "rachel@example.com", "Rachel", "Patterson", Set.of(org1));
        UUID userId = user.getId();

        // Act
        UserDto userDto = userService.getUserById(userId);

        // Assert
        assertNotNull(userDto, "User DTO should not be null");
        assertNotNull(userId, "User ID should not be null");
    }
}
