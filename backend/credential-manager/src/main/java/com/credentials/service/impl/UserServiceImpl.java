package com.credentials.service.impl;

import com.credentials.bootstrap.RequestContextHolder;
import com.credentials.dto.LoginResponse;
import com.credentials.dto.RequestUserContext;
import com.credentials.dto.UserDto;
import com.credentials.dto.UserLoginRequest;
import com.credentials.dto.UserSessionData;
import com.credentials.entity.Organization;
import com.credentials.entity.User;
import com.credentials.exception.UserNotFoundException;
import com.credentials.mapper.OrganizationMapper;
import com.credentials.mapper.UserMapper;
import com.credentials.repo.OrganizationRepository;
import com.credentials.repo.UserRepository;
import com.credentials.service.SessionService;
import com.credentials.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepo;
    private final OrganizationRepository orgRepo;
    private final OrganizationMapper organizationMapper;
    private final UserMapper userMapper;
    private final SessionService sessionService;

    @Transactional
    public LoginResponse processUserLogin(UserLoginRequest request) {
        RequestUserContext reqUserCtx = RequestContextHolder.get();
        String email = reqUserCtx.getEmail();
        String subjectId = reqUserCtx.getSubjectId();

        Optional<User> userOpt = userRepo.findBySubjectId(subjectId);

        if (userOpt.isEmpty()) {
            return handleFirstTimeLogin(request, subjectId, email);
        }

        return handleUserRelogin(userOpt.get(), email);
    }

    private LoginResponse handleUserRelogin(User user, String email) {
        // CASE 2: Returning User
        Set<Organization> userOrgs = user.getOrganizations();
        List<UUID> orgIds = userOrgs.stream().map(Organization::getId).toList();

        // Create session in Redis
        UserSessionData sessionData = sessionService.createSession(
                user.getId(),
                user.getSubjectId(),
                email,
                orgIds
        );

        if (userOrgs.size() == 1) {
            // CASE 2A: User has only one organization - auto-selected in session
            Organization singleOrg = userOrgs.iterator().next();
            return LoginResponse.builder()
                    .email(email)
                    .isFirstLogin(false)
                    .requiresOrgSelection(false)
                    .message("Welcome back! Organization '" + singleOrg.getName() + "' set for this session")
                    .associatedOrgs(userOrgs.stream().map(organizationMapper::toDto).toList())
                    .build();
        } else {
            // CASE 2B: User has multiple organizations - needs to select via /session/select-org
            return LoginResponse.builder()
                    .email(email)
                    .isFirstLogin(false)
                    .requiresOrgSelection(true)
                    .message("Please select an organization for this session via POST /api/v1/session/select-org")
                    .availableOrgs(userOrgs.stream().map(organizationMapper::toDto).toList())
                    .build();
        }
    }

    private LoginResponse handleFirstTimeLogin(UserLoginRequest request, String subjectId, String email) {
        // CASE 1: First Time Login - New user not seen before

        // CASE 1A: No org association provided - return available orgs for selection
        if (request == null || request.associateWithOrgIds() == null || request.associateWithOrgIds().isEmpty()) {
            return LoginResponse.builder()
                    .email(email)
                    .isFirstLogin(true)
                    .requiresOrgSelection(true)
                    .message("Please select one or more organizations to associate with your account")
                    .availableOrgs(orgRepo.findAll().stream().map(organizationMapper::toDto).toList())
                    .build();
        }

        // CASE 1B: Org association provided - create user and associate orgs
        Set<Organization> orgsToAssociate = new HashSet<>(orgRepo.findAllById(request.associateWithOrgIds()));

        // Validate all provided org IDs exist
        if (orgsToAssociate.size() != request.associateWithOrgIds().size()) {
            throw new IllegalArgumentException("One or more organization IDs are invalid");
        }

        // Create and save new user
        User newUser = new User();
        newUser.setSubjectId(subjectId);
        newUser.setEmail(email);
        newUser.setName(request.firstName());
        newUser.setFirstName(request.firstName());
        newUser.setLastName(request.lastName());
        newUser.setOrganizations(orgsToAssociate);
        User savedUser = userRepo.save(newUser);

        // Create session in Redis
        List<UUID> orgIds = orgsToAssociate.stream().map(Organization::getId).toList();
        sessionService.createSession(savedUser.getId(), subjectId, email, orgIds);

        // Determine if session org selection is needed (user associated with multiple orgs)
        boolean requiresOrgSelection = orgsToAssociate.size() > 1;

        return LoginResponse.builder()
                .email(email)
                .isFirstLogin(true)
                .requiresOrgSelection(requiresOrgSelection)
                .message(requiresOrgSelection
                        ? "Account created. Please select an organization for this session via POST /api/v1/session/select-org"
                        : "Account created and organization set for session")
                .associatedOrgs(orgsToAssociate.stream().map(organizationMapper::toDto).toList())
                .build();
    }

    @Override
    public UserDto getUserById(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
        return userMapper.toDto(user);
    }

    @Override
    public List<UserDto> getAllUsers() {
        List<User> users = userRepo.findAll();
        return userMapper.toDtoList(users);
    }
}
