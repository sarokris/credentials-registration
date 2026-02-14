package com.credentials.bootstrap;

import com.credentials.entity.Credential;
import com.credentials.entity.Organization;
import com.credentials.entity.User;
import com.credentials.repo.CredentialRepository;
import com.credentials.repo.OrganizationRepository;
import com.credentials.repo.UserRepository;
import com.credentials.security.EncryptionUtils;
import com.credentials.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.data-initializer.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final OrganizationRepository orgRepo;
    private final UserRepository userRepo;
    private final CredentialRepository credRepo;

    @Override
    public void run(String... args) {
        log.info("DataInitializer started");

        // Check if data already exists
        if (orgRepo.count() > 0) {
            log.info("Data already initialized, skipping seed");
            return;
        }

        try {
            // Create 3 Organizations
            Organization org1 = createOrganization("DE123456789", "SAP-ORG-001");
            Organization org2 = createOrganization("DE987654321", "SAP-ORG-002");
            Organization org3 = createOrganization("DE456789123", "SAP-ORG-003");

            log.info("Created 3 organizations: {}, {}, {}", org1.getId(), org2.getId(), org3.getId());

            // Create Users
            User user1 = createUser("user-001", "john@techcorp.com", "John", "Developer", Set.of(org1));
            User user2 = createUser("user-002", "jane@innovate.com", "Jane", "Manager", Set.of(org2));
            User user3 = createUser("user-003", "bob@cloudsys.com", "Bob", "Admin", Set.of(org3));
            User user4 = createUser("user-004", "alice@example.com", "Alice", "Analyst", Set.of(org1, org2)); // Multi-org user

            log.info("Created 4 users with different org associations");

            // Create Credentials
            createCredential("client-id-001", "secret-001", org1, user1);
            createCredential("client-id-002", "secret-002", org1, user2);
            createCredential("client-id-003", "secret-003", org2, user3);
            createCredential("client-id-004", "secret-004", org3, user4);

            log.info("Created 4 credentials across organizations");
            log.info("DataInitializer completed successfully");
        } catch (Exception e) {
            log.error("Error during data initialization", e);
        }
    }

    private Organization createOrganization(String vatNumber, String sapId) {
        Organization org = new Organization();
        org.setName("Org " + RandomUtil.generateRandomSuffix(4));
        org.setVatNumber(vatNumber);
        org.setSapId(sapId);
        org.setUsers(new HashSet<>());
        org.setCredentials(new HashSet<>());
        return orgRepo.save(org);
    }

    private User createUser(String subjectId, String email, String firstName, String lastName, Set<Organization> organizations) {
        User user = new User();
        user.setSubjectId(subjectId);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setName(firstName);
        user.setLastName(lastName);
        user.setOrganizations(organizations);
        user.setCreatedCredentials(new java.util.ArrayList<>());
        return userRepo.save(user);
    }

    private void createCredential(String clientId, String clientSecret, Organization org, User createdBy)  {
        Credential credential = new Credential();
        String randomSuffix = RandomUtil.generateRandomSuffix(6);
        credential.setName(clientId + "-" + randomSuffix);
        credential.setClientId(clientId);
        credential.setClientSecret(EncryptionUtils.encrypt(clientSecret)); // Encrypt secret
        credential.setOrganization(org);
        credential.setCreatedBy(createdBy);
        credential.setCreationDate(OffsetDateTime.now());
        credential.setExpiryDate(OffsetDateTime.now().plusYears(1)); // Expires in 1 year
        credRepo.save(credential);
    }
}