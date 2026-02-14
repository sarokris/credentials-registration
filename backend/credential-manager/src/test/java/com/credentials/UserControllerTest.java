package com.credentials;

import com.credentials.dto.UserDto;
import com.credentials.entity.Organization;
import com.credentials.entity.User;
import com.credentials.repo.CredentialRepository;
import com.credentials.repo.OrganizationRepository;
import com.credentials.repo.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "app.data-initializer.enabled=false"
})
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private CredentialRepository credentialRepository;

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("credentials_test")
            .withUsername("test")
            .withPassword("test")
            .withStartupTimeout(Duration.ofSeconds(60))
            .waitingFor(Wait.forListeningPort());

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.hikari.max-lifetime", () -> "30000");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "60000");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "2");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    private Organization org1;
    private Organization org2;
    private Organization org3;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        credentialRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();

        // Create test organizations
        org1 = createOrganization("DE134456789", "ORG-001");
        org2 = createOrganization("DE7784321", "ORG-002");
        org3 = createOrganization("DE465449123", "ORG-003");
    }

    @AfterEach
    void cleanUp() {
        credentialRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    private Organization createOrganization(String vatNumber, String sapId) {
        Organization org = new Organization();
        org.setName(sapId);
        org.setVatNumber(vatNumber);
        org.setSapId(sapId);
        return organizationRepository.save(org);
    }

    private User createUserWithOrganizations(String subjectId, String email, String firstName, String lastName, Set<Organization> organizations) {
        User user = new User();
        user.setSubjectId(subjectId);
        user.setEmail(email);
        user.setName(firstName);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setOrganizations(organizations);
        return userRepository.save(user);
    }

    @Test
    @DisplayName("GET /api/v1/users returns 200 with empty list when no users exist")
    @Transactional
    void testGetAllUsersEmptyList() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/users returns all users with correct data")
    @Transactional
    void testGetAllUsersWithData() throws Exception {
        // Arrange
        createUserWithOrganizations("user-001", "user1@example.com", "John", "Doe", Set.of(org1));
        createUserWithOrganizations("user-002", "user2@example.com", "Jane", "Smith", Set.of(org2));

        // Act & Assert
        MvcResult result = mockMvc.perform(get("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<UserDto> users = Arrays.asList(objectMapper.readValue(responseBody, UserDto[].class));

        assertEquals(2, users.size(), "Should have 2 users");
        assertTrue(users.stream().anyMatch(u -> u.email().equals("user1@example.com")), "Should contain user1");
        assertTrue(users.stream().anyMatch(u -> u.email().equals("user2@example.com")), "Should contain user2");
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId} returns 200 with user details")
    @Transactional
    void testGetUserByIdSuccess() throws Exception {
        // Arrange
        User user = createUserWithOrganizations("user-003", "user3@example.com", "Bob", "Johnson", Set.of(org1));

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}", user.getId())
                .header("x-user-sub", "user-003")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user3@example.com"))
                .andExpect(jsonPath("$.firstName").value("Bob"))
                .andExpect(jsonPath("$.lastName").value("Johnson"));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId} returns 404 for non-existent user")
    @Transactional
    void testGetUserByIdNotFound() throws Exception {
        // Arrange
        UUID nonExistentUserId = UUID.randomUUID();

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}", nonExistentUserId)
                .header("x-user-sub", "test-user-sub")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/users returns users with multiple organizations")
    @Transactional
    void testGetAllUsersWithMultipleOrganizations() throws Exception {
        // Arrange
        createUserWithOrganizations("user-004", "user4@example.com", "Alice", "Brown", Set.of(org1, org2, org3));

        // Act & Assert
        mockMvc.perform(get("/api/v1/users")
                .header("x-user-sub", "user-004")
                .header("x-org-id", org1.getId().toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("user4@example.com"))
                .andExpect(jsonPath("$[0].firstName").value("Alice"));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId} returns correct name fields")
    @Transactional
    void testGetUserByIdNameFields() throws Exception {
        // Arrange
        User user = createUserWithOrganizations("user-005", "user5@example.com", "Charlie", "Davis", Set.of(org1));

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}", user.getId())
                .header("x-user-sub", "user-005")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Charlie"))
                .andExpect(jsonPath("$.firstName").value("Charlie"))
                .andExpect(jsonPath("$.lastName").value("Davis"));
    }

    @Test
    @DisplayName("GET /api/v1/users returns correct content type")
    @Transactional
    void testGetAllUsersContentType() throws Exception {
        // Arrange
        createUserWithOrganizations("user-006", "user6@example.com", "Diana", "Evans", Set.of(org1));

        // Act & Assert
        mockMvc.perform(get("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId} returns correct content type")
    @Transactional
    void testGetUserByIdContentType() throws Exception {
        // Arrange
        User user = createUserWithOrganizations("user-007", "user7@example.com", "Eve", "Foster", Set.of(org1));

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}", user.getId())
                .header("x-user-sub", "user-007")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/users returns multiple users in consistent format")
    @Transactional
    void testGetAllUsersMultipleUsersFormat() throws Exception {
        // Arrange
        createUserWithOrganizations("user-008", "user8@example.com", "Frank", "Garcia", Set.of(org1));
        createUserWithOrganizations("user-009", "user9@example.com", "Grace", "Hamilton", Set.of(org2));
        createUserWithOrganizations("user-010", "user10@example.com", "Henry", "Ingram", Set.of(org3));

        // Act & Assert
        mockMvc.perform(get("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].email").exists())
                .andExpect(jsonPath("$[0].firstName").exists())
                .andExpect(jsonPath("$[0].lastName").exists())
                .andExpect(jsonPath("$[1].email").exists())
                .andExpect(jsonPath("$[2].email").exists());
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId} with valid UUID format")
    @Transactional
    void testGetUserByIdValidUUIDFormat() throws Exception {
        // Arrange
        User user = createUserWithOrganizations("user-011", "user11@example.com", "Iris", "Jones", Set.of(org1));
        String uuidString = user.getId().toString();

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}", uuidString)
                .header("x-user-sub", "user-011")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user11@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/users after deleting a user")
    @Transactional
    void testGetAllUsersAfterUserDeletion() throws Exception {
        // Arrange
        User user1 = createUserWithOrganizations("user-012", "user12@example.com", "Jack", "King", Set.of(org1));
        createUserWithOrganizations("user-013", "user13@example.com", "Karen", "Lewis", Set.of(org2));

        // Act - Delete one user
        userRepository.deleteById(user1.getId());

        // Assert
        mockMvc.perform(get("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("user13@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/users/{userId} returns email in correct format")
    @Transactional
    void testGetUserByIdEmailFormat() throws Exception {
        // Arrange
        User user = createUserWithOrganizations("user-014", "liam@example.com", "Liam", "Miller", Set.of(org1));

        // Act & Assert
        mockMvc.perform(get("/api/v1/users/{userId}", user.getId())
                .header("x-user-sub", "user-014")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("liam@example.com"));
    }
}
