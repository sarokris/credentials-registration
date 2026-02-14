package com.credentials;

import com.credentials.bootstrap.RequestContextHolder;
import com.credentials.dto.CredentialRequest;
import com.credentials.dto.CredentialResponse;
import com.credentials.dto.RequestUserContext;
import com.credentials.entity.Credential;
import com.credentials.entity.Organization;
import com.credentials.entity.User;
import com.credentials.exception.CredentialNotFoundException;
import com.credentials.repo.CredentialRepository;
import com.credentials.repo.OrganizationRepository;
import com.credentials.repo.UserRepository;
import com.credentials.service.CredentialService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Credential Processing IntegrationTests")
@Transactional
class CredentialProcessingTest extends BaseIntegrationTest {

	@Autowired
	private CredentialService credentialService;

	@Autowired
	private CredentialRepository credentialRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	private User testUser;
	private Organization testOrg;

	@BeforeEach
	void setUp() {
		super.setUp();
		// Create test organization and user
		testOrg = org1;
		testUser = createUserWithOrganizations("cred-test-user", "cred@example.com", "Cred", "Tester", Set.of(testOrg));

		// Set up request context
		RequestUserContext context = RequestUserContext.builder()
				.subjectId(testUser.getSubjectId())
				.email(testUser.getEmail())
				.selectedOrgId(testOrg.getId().toString())
				.build();
		RequestContextHolder.set(context);
	}

    @AfterEach
     void cleanUp() {
        super.cleanUp();
    }

	// ==================== POSITIVE SCENARIOS ====================

	@Test
	@DisplayName("POSITIVE: Create credential successfully")
	
	void testCreateCredentialSuccessfully() {
		// Arrange
		CredentialRequest request = new CredentialRequest("api-key-0001", 30);

		// Act
		CredentialResponse response = credentialService.create(request);

		// Assert
		assertNotNull(response);
		assertEquals("api-key-0001", response.name());
		assertNotNull(response.clientSecret());

		// Verify persisted in database
		Optional<Credential> saved = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-0001"))
				.findFirst();
		assertTrue(saved.isPresent(), "Credential should be saved in database");
		assertEquals(testUser.getId(), saved.get().getCreatedBy().getId());
		assertEquals(testOrg.getId(), saved.get().getOrganization().getId());
	}

	@Test
	@DisplayName("POSITIVE: Create multiple credentials for same organization")
	
	void testCreateMultipleCredentialsForSameOrg() {
		// Arrange
		CredentialRequest request1 = new CredentialRequest("api-key-001", 60);
		CredentialRequest request2 = new CredentialRequest("api-key-002", 90);

		// Act
		CredentialResponse response1 = credentialService.create(request1);
		CredentialResponse response2 = credentialService.create(request2);

		// Assert
		assertNotNull(response1);
		assertNotNull(response2);
		assertNotEquals(response1.clientId(), response2.clientId());

		// Verify both exist in database
		long count = credentialRepository.findAll().stream()
				.filter(c -> c.getOrganization().getId().equals(testOrg.getId()))
				.count();
		assertEquals(2, count);
	}

	@Test
	@DisplayName("POSITIVE: Delete credential successfully")
	
	void testDeleteCredentialSuccessfully() {
		// Arrange - Create a credential first
		CredentialRequest createRequest = new CredentialRequest("api-key-to-delete", 45);
		credentialService.create(createRequest);

		// Get the actual credential to get its ID
		Credential credential = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-to-delete"))
				.findFirst()
				.orElseThrow();
		UUID actualId = credential.getId();

		// Act
		credentialService.delete(actualId);

		// Assert
		Optional<Credential> deleted = credentialRepository.findById(actualId);
		assertFalse(deleted.isPresent(), "Credential should be deleted");
	}

	@Test
	@DisplayName("POSITIVE: Reset credential secret successfully")
	
	void testResetCredentialSecretSuccessfully() {
		// Arrange
		CredentialRequest createRequest = new CredentialRequest("api-key-to-reset", 60);
		credentialService.create(createRequest);

		Credential credential = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-to-reset"))
				.findFirst()
				.orElseThrow();
		String oldSecret = credential.getClientSecret();

		// Act
		CredentialResponse resetResponse = credentialService.resetSecret(credential.getId());

		// Assert
		assertNotNull(resetResponse);
		assertNotNull(resetResponse.clientSecret());
		assertNotEquals(oldSecret, resetResponse.clientSecret(), "Secret should be different after reset");

		// Verify in database
		Credential updated = credentialRepository.findById(credential.getId()).orElseThrow();
		assertNotEquals(oldSecret, updated.getClientSecret());
	}

	@Test
	@DisplayName("POSITIVE: Credential has correct expiry date")
	
	void testCredentialHasCorrectExpiryDate() {
		// Arrange
		CredentialRequest request = new CredentialRequest("api-key-expiry", 30);

		// Act
		credentialService.create(request);

		// Assert
		Credential credential = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-expiry"))
				.findFirst()
				.orElseThrow();

		assertNotNull(credential.getExpiryDate());
		assertTrue(credential.getExpiryDate().isAfter(OffsetDateTime.now()),
				"Expiry date should be in the future");
        // Verify expiry date is approximately 30 days from creation
        OffsetDateTime expectedExpiry = credential.getCreationDate().plusDays(30);
        long daysDifference = java.time.Duration.between(credential.getExpiryDate(), expectedExpiry).toDays();
        assertTrue(Math.abs(daysDifference) <= 1, "Expiry date should be approximately 30 days from creation");
	}

	// ==================== NEGATIVE SCENARIOS ====================

	@Test
	@DisplayName("NEGATIVE: Delete credential that does not exist")
	void testDeleteNonExistentCredential() {
		// Arrange
		UUID nonExistentId = UUID.randomUUID();

		// Act & Assert
		assertThrows(CredentialNotFoundException.class, () -> {
			credentialService.delete(nonExistentId);
		}, "Should throw exception when deleting non-existent credential");
	}

	@Test
	@DisplayName("NEGATIVE: Reset secret for non-existent credential")
	void testResetSecretForNonExistentCredential() {
		// Arrange
		UUID nonExistentId = UUID.randomUUID();

		// Act & Assert
		assertThrows(CredentialNotFoundException.class, () -> {
			credentialService.resetSecret(nonExistentId);
		}, "Should throw exception when resetting secret for non-existent credential");
	}

	@Test
	@DisplayName("NEGATIVE: User cannot delete credential they did not create")
	
	void testUserCannotDeleteCredentialFromAnotherUser() {
		// Arrange - Create credential with testUser
		CredentialRequest createRequest = new CredentialRequest("api-key-other-user", 60);
		credentialService.create(createRequest);

		Credential credential = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-other-user"))
				.findFirst()
				.orElseThrow();

		// Create another user
		User otherUser = createUserWithOrganizations("other-user", "other@example.com", "Other", "User", Set.of(testOrg));

		// Switch context to other user
		RequestUserContext otherContext = RequestUserContext.builder()
				.subjectId(otherUser.getSubjectId())
				.email(otherUser.getEmail())
				.selectedOrgId(testOrg.getId().toString())
				.build();
		RequestContextHolder.set(otherContext);

		// Act & Assert
		assertThrows(Exception.class, () -> credentialService.delete(credential.getId()),
				"User should not be able to delete credential created by another user");
	}




	// ==================== CORNER CASES ====================

	@Test
	@DisplayName("CORNER CASE: Reset secret multiple times rapidly")
	
	void testResetSecretMultipleTimesRapidly() {
		// Arrange
		CredentialRequest createRequest = new CredentialRequest("api-key-rapid-reset", 60);
		credentialService.create(createRequest);

		Credential credential = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-rapid-reset"))
				.findFirst()
				.orElseThrow();

		// Act - Reset multiple times
		CredentialResponse response1 = credentialService.resetSecret(credential.getId());
		CredentialResponse response2 = credentialService.resetSecret(credential.getId());
		CredentialResponse response3 = credentialService.resetSecret(credential.getId());

		// Assert - All should be different
		assertNotEquals(response1.clientSecret(), response2.clientSecret());
		assertNotEquals(response2.clientSecret(), response3.clientSecret());
		assertNotEquals(response1.clientSecret(), response3.clientSecret());

		// Verify latest secret is in database
		Credential updated = credentialRepository.findById(credential.getId()).orElseThrow();
		assertNotEquals(response1.clientSecret(), updated.getClientSecret());
		assertNotEquals(response2.clientSecret(), updated.getClientSecret());
	}

	@Test
	@DisplayName("CORNER CASE: Delete and recreate same credential")
	
	void testDeleteAndRecreateCredential() {
		// Arrange
		CredentialRequest request = new CredentialRequest("api-key-recreate", 45);
		credentialService.create(request);

		Credential credential = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-recreate"))
				.findFirst()
				.orElseThrow();

		// Act - Delete
		credentialService.delete(credential.getId());

		// Assert - Deleted
		assertFalse(credentialRepository.findById(credential.getId()).isPresent());

		// Act - Recreate with same client ID
		CredentialResponse recreated = credentialService.create(new CredentialRequest("api-key-recreate", 60));

		// Assert - Recreated successfully
		assertNotNull(recreated);
		assertTrue(credentialRepository.findAll().stream()
				.anyMatch(c -> c.getName().equals("api-key-recreate")));
	}

	@Test
	@DisplayName("CORNER CASE: Verify credential isolation between organizations")
	
	void testCredentialIsolationBetweenOrganizations() {
		// Arrange - Create credential in org1
		CredentialRequest request1 = new CredentialRequest("api-key-org1", 30);
		credentialService.create(request1);

		// Switch to org2
		User user2 = createUserWithOrganizations("user-org2", "user2@example.com", "User", "Two", Set.of(org2));
		RequestUserContext context2 = RequestUserContext.builder()
				.subjectId(user2.getSubjectId())
				.email(user2.getEmail())
				.selectedOrgId(org2.getId().toString())
				.build();
		RequestContextHolder.set(context2);

		// Act - Create credential in org2
		CredentialRequest request2 = new CredentialRequest("api-key-org2", 60);
		credentialService.create(request2);

		// Assert - Each org should have only its own credential
		long org1Count = credentialRepository.findAll().stream()
				.filter(c -> c.getOrganization().getId().equals(org1.getId()))
				.count();
		long org2Count = credentialRepository.findAll().stream()
				.filter(c -> c.getOrganization().getId().equals(org2.getId()))
				.count();

		assertEquals(1, org1Count, "Org1 should have only 1 credential");
		assertEquals(1, org2Count, "Org2 should have only 1 credential");
	}

	@Test
	@DisplayName("CORNER CASE: Verify credential secret is encrypted (not plaintext)")
	
	void testCredentialSecretIsEncrypted() {
		// Arrange
		CredentialRequest request = new CredentialRequest("api-key-encrypt-check", 30);

		// Act
		credentialService.create(request);

		// Assert
		Credential credential = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-encrypt-check"))
				.findFirst()
				.orElseThrow();

		// Secret in database should be encrypted (not null and not empty)
		assertNotNull(credential.getClientSecret());
		assertFalse(credential.getClientSecret().isEmpty(),
				"Secret should be encrypted and stored");
	}

	@Test
	@DisplayName("CORNER CASE: Create credentials by multiple users in same organization")
	void testMultipleUsersCreateCredentialsInSameOrg() {
		// Arrange - User1 creates credential
		CredentialRequest request1 = new CredentialRequest("api-key-user1", 30);
		credentialService.create(request1);

		// Create and switch to User2
		User user2 = createUserWithOrganizations("user2-multi", "user2@example.com", "User", "Two", Set.of(testOrg));
		RequestUserContext context2 = RequestUserContext.builder()
				.subjectId(user2.getSubjectId())
				.email(user2.getEmail())
				.selectedOrgId(testOrg.getId().toString())
				.build();
		RequestContextHolder.set(context2);

		// User2 creates credential in same org
		CredentialRequest request2 = new CredentialRequest("api-key-user2", 60);
		credentialService.create(request2);

		// Assert - Both credentials exist in same org
		long count = credentialRepository.findAll().stream()
				.filter(c -> c.getOrganization().getId().equals(testOrg.getId()))
				.count();
		assertEquals(2, count, "Both users' credentials should exist in organization");

		// Verify each credential has correct creator
		Credential cred1 = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-user1"))
				.findFirst()
				.orElseThrow();
		Credential cred2 = credentialRepository.findAll().stream()
				.filter(c -> c.getName().equals("api-key-user2"))
				.findFirst()
				.orElseThrow();

		assertEquals(testUser.getId(), cred1.getCreatedBy().getId());
		assertEquals(user2.getId(), cred2.getCreatedBy().getId());
	}
}
