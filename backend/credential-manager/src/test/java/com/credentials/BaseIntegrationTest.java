package com.credentials;

import com.credentials.entity.Organization;
import com.credentials.entity.User;
import com.credentials.repo.CredentialRepository;
import com.credentials.repo.OrganizationRepository;
import com.credentials.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.Set;

@TestConfiguration
@SpringBootTest(properties = {
		"spring.sql.init.mode=never",
		"app.data-initializer.enabled=false"
})
public abstract class BaseIntegrationTest {

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

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected OrganizationRepository organizationRepository;

	@Autowired
	protected CredentialRepository credentialRepository;

	protected Organization org1;
	protected Organization org2;
	protected Organization org3;

	@BeforeEach
	void setUp() {
		// Clean up before each test - delete in correct order to respect foreign key constraints
		credentialRepository.deleteAll();
		userRepository.deleteAll();
		organizationRepository.deleteAll();

		// Create test organizations
		org1 = createOrganization("DE134456789", "ORG-001");
		org2 = createOrganization("DE7784321", "ORG-002");
		org3 = createOrganization("DE465449123", "ORG-003");
	}


     void cleanUp() {
        // Clean up before each test - delete in correct order to respect foreign key constraints
        credentialRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
    }



	// Helper methods
	protected Organization createOrganization(String vatNumber, String sapId) {
		Organization org = new Organization();
		org.setName(sapId);
		org.setVatNumber(vatNumber);
		org.setSapId(sapId);
		return organizationRepository.save(org);
	}

	protected User createUserWithOrganizations(String subjectId, String email, String firstName, String lastName, Set<Organization> organizations) {
		User user = new User();
		user.setSubjectId(subjectId);
		user.setEmail(email);
        user.setName(firstName);
		user.setFirstName(firstName);
		user.setLastName(lastName);
		user.setOrganizations(organizations);
		return userRepository.save(user);
	}
}
