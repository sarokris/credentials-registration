package com.credentials.service.impl;

import com.credentials.bootstrap.RequestContextHolder;
import com.credentials.dto.LoginResponse;
import com.credentials.dto.RequestUserContext;
import com.credentials.dto.UserDto;
import com.credentials.dto.UserLoginRequest;
import com.credentials.entity.Organization;
import com.credentials.entity.User;
import com.credentials.exception.UserNotFoundException;
import com.credentials.mapper.OrganizationMapper;
import com.credentials.mapper.UserMapper;
import com.credentials.repo.OrganizationRepository;
import com.credentials.repo.UserRepository;
import com.credentials.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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

        if (userOrgs.size() == 1) {
            // CASE 2A: User has only one organization - use it seamlessly
            Organization singleOrg = userOrgs.iterator().next();
            RequestContextHolder.get().setSelectedOrgId(singleOrg.getId().toString());
            return LoginResponse.builder()
                    .email(email)
                    .isFirstLogin(false)
                    .requiresOrgSelection(false)
                    .message("Welcome back! Organization '" + singleOrg.getName() + "' set for this session")
                    .associatedOrgs(userOrgs.stream().map(organizationMapper::toDto).toList())
                    .build();
        } else {
            // CASE 2B: User has multiple organizations - check if org is already selected via header
            String selectedOrgId = RequestContextHolder.get().getSelectedOrgId();
            boolean orgIdSelectionRequired = StringUtils.isBlank(selectedOrgId);

            if (orgIdSelectionRequired) {
                // User needs to select an organization
                return LoginResponse.builder()
                        .email(email)
                        .isFirstLogin(false)
                        .requiresOrgSelection(true)
                        .message("Please select an organization for this session via 'x-org-id' header")
                        .availableOrgs(userOrgs.stream().map(organizationMapper::toDto).toList())
                        .build();
            } else {
                // Organization already selected via header - validate and proceed
                return LoginResponse.builder()
                        .email(email)
                        .isFirstLogin(false)
                        .requiresOrgSelection(false)
                        .message("Welcome back! Organization set for this session")
                        .associatedOrgs(userOrgs.stream().map(organizationMapper::toDto).toList())
                        .build();
            }
        }
    }

    private LoginResponse handleFirstTimeLogin(UserLoginRequest request, String subjectId, String email) {
        // CASE 1: First Time Login - New user not seen before

        // CASE 1A: No org selection provided - return available orgs for selection
        if (request == null || request.selectedOrgIds() == null || request.selectedOrgIds().isEmpty()) {
            return LoginResponse.builder()
                    .email(email)
                    .isFirstLogin(true)
                    .requiresOrgSelection(true)
                    .message("Please select one or more organizations to associate with your account")
                    .availableOrgs(orgRepo.findAll().stream().map(organizationMapper::toDto).toList())
                    .build();
        }

        // CASE 1B: Org selection provided - create user and associate orgs
        Set<Organization> selectedOrgs = new HashSet<>(orgRepo.findAllById(request.selectedOrgIds()));

        // Validate all provided org IDs exist
        if (selectedOrgs.size() != request.selectedOrgIds().size()) {
            throw new IllegalArgumentException("One or more organization IDs are invalid");
        }

        // Create and save new user
        User newUser = new User();
        newUser.setSubjectId(subjectId);
        newUser.setEmail(email);
        newUser.setName(request.firstName());
        newUser.setFirstName(request.firstName());
        newUser.setLastName(request.lastName());
        newUser.setOrganizations(selectedOrgs);
        userRepo.save(newUser);

        // If user selected only one org, set it as session org automatically
        boolean requiresOrgSelection = selectedOrgs.size() > 1;
        if (!requiresOrgSelection) {
            RequestContextHolder.get().setSelectedOrgId(selectedOrgs.iterator().next().getId().toString());
        }

        return LoginResponse.builder()
                .email(email)
                .isFirstLogin(true)
                .requiresOrgSelection(requiresOrgSelection)
                .message(requiresOrgSelection
                        ? "Account created. Please select an organization for this session via 'x-org-id' header"
                        : "Account created and organization set for session")
                .associatedOrgs(selectedOrgs.stream().map(organizationMapper::toDto).toList())
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
