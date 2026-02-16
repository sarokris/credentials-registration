#!/bin/bash

##############################################################################
# Integration Test Script
# Purpose: Simulate a complete user login flow using Authorization Code Flow
#          and test API access through Envoy gateway
#
# Flow:
#   1. User visits /authorize endpoint (simulated)
#   2. User authenticates and consents (mock-oidc auto-approves)
#   3. Callback receives authorization code
#   4. Exchange code for tokens
#   5. Call protected API with access token
#
# Usage:
#   ./integration-test.sh                    # Uses admin-user
#   ./integration-test.sh admin-user         # Admin user
#   ./integration-test.sh regular-user       # Regular user
#   ./integration-test.sh saravanan          # Saravanan
##############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
MOCK_OIDC_URL="http://localhost:8081"
ENVOY_URL="http://localhost:10000"
CONTEXT_PATH="/credential-manager"
ISSUER_ID="default"
REDIRECT_URI="http://localhost:10000/callback"
CLIENT_SECRET="secret"
SCOPE="openid"
STATE="random-state-$(date +%s)"

# Get username from command line argument, default to "admin-user"
USERNAME="${1:-admin-user}"

echo -e "${BLUE}============================================================================${NC}"
echo -e "${BLUE}   Integration Test - Authorization Code Flow Simulation${NC}"
echo -e "${BLUE}============================================================================${NC}"
echo ""

echo -e "${YELLOW}Configuration:${NC}"
echo "  Mock OIDC URL:  $MOCK_OIDC_URL"
echo "  Envoy URL:      $ENVOY_URL"
echo "  Context Path:   $CONTEXT_PATH"
echo "  Username:       $USERNAME"
echo "  Redirect URI:   $REDIRECT_URI"
echo "  State:          $STATE"
echo ""

# Define expected claims based on username
case "$USERNAME" in
  "admin-user")
    EXPECTED_SUB="admin-123"
    EXPECTED_EMAIL="admin@test.com"
    EXPECTED_NAME="Admin User"
    ;;
  "regular-user")
    EXPECTED_SUB="user-456"
    EXPECTED_EMAIL="user@test.com"
    EXPECTED_NAME="Regular User"
    ;;
  "saravanan")
    EXPECTED_SUB="saravanan-789"
    EXPECTED_EMAIL="saravanan@test.com"
    EXPECTED_NAME="Saravanan"
    ;;
  *)
    EXPECTED_SUB="default-user"
    EXPECTED_EMAIL="default@test.com"
    EXPECTED_NAME="Default User"
    ;;
esac

echo -e "${YELLOW}Expected Claims for '$USERNAME':${NC}"
echo "  sub:   $EXPECTED_SUB"
echo "  email: $EXPECTED_EMAIL"
echo "  name:  $EXPECTED_NAME"
echo ""

##############################################################################
# Step 1: Simulate User Visiting Authorization Endpoint
##############################################################################
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}Step 1: User visits Authorization Endpoint${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

AUTHORIZE_URL="$MOCK_OIDC_URL/$ISSUER_ID/authorize"
echo "  Browser would redirect to:"
echo "  $AUTHORIZE_URL?client_id=$USERNAME&response_type=code&redirect_uri=$REDIRECT_URI&scope=$SCOPE&state=$STATE"
echo ""

# The mock-oauth2-server with interactiveLogin=false will auto-approve
# We simulate this by calling the authorize endpoint and following redirects
echo "  Simulating user authentication and consent..."

# Use the debugger endpoint to get an authorization code directly
# This simulates what happens after user logs in and consents
AUTH_RESPONSE=$(curl -s -X POST "$MOCK_OIDC_URL/$ISSUER_ID/authorize" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=$USERNAME&response_type=code&redirect_uri=$REDIRECT_URI&scope=$SCOPE&state=$STATE" \
  -D - 2>/dev/null)

# Try to extract the authorization code from the Location header
AUTH_CODE=$(echo "$AUTH_RESPONSE" | grep -i "location:" | grep -oE 'code=[^&]+' | cut -d'=' -f2 | tr -d '\r')

# If not found in header, try the response body
if [ -z "$AUTH_CODE" ]; then
  AUTH_CODE=$(echo "$AUTH_RESPONSE" | grep -oE 'code=[^&"]+' | head -1 | cut -d'=' -f2)
fi

# If still not found, try using GET with follow redirects
if [ -z "$AUTH_CODE" ]; then
  echo "  Trying alternative method with GET request..."
  CALLBACK_URL=$(curl -s -o /dev/null -w "%{redirect_url}" \
    "$MOCK_OIDC_URL/$ISSUER_ID/authorize?client_id=$USERNAME&response_type=code&redirect_uri=$REDIRECT_URI&scope=$SCOPE&state=$STATE")
  AUTH_CODE=$(echo "$CALLBACK_URL" | grep -oE 'code=[^&]+' | cut -d'=' -f2)
fi

if [ -z "$AUTH_CODE" ]; then
  echo -e "  ${RED}❌ Failed to get authorization code${NC}"
  echo ""
  echo "  Falling back to client_credentials flow for testing..."

  # Fallback to client_credentials for testing purposes
  TOKEN_RESPONSE=$(curl -s -X POST "$MOCK_OIDC_URL/$ISSUER_ID/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&client_id=$USERNAME&client_secret=$CLIENT_SECRET&scope=$SCOPE")

  ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token' 2>/dev/null)

  if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
    echo -e "  ${RED}❌ Failed to get token via fallback method${NC}"
    exit 1
  fi

  echo -e "  ${GREEN}✓ Token obtained via client_credentials fallback${NC}"
  echo ""
else
  echo -e "  ${GREEN}✓ Authorization code received${NC}"
  echo "  Code: ${AUTH_CODE:0:30}..."
  echo ""

  ##############################################################################
  # Step 2: Callback - Exchange Authorization Code for Tokens
  ##############################################################################
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${YELLOW}Step 2: Callback - Exchange Authorization Code for Tokens${NC}"
  echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo ""

  echo "  Simulating callback to: $REDIRECT_URI?code=$AUTH_CODE&state=$STATE"
  echo ""
  echo "  Exchanging authorization code for tokens..."
  echo "  POST $MOCK_OIDC_URL/$ISSUER_ID/token"
  echo "    grant_type=authorization_code"
  echo "    code=${AUTH_CODE:0:20}..."
  echo "    redirect_uri=$REDIRECT_URI"
  echo "    client_id=$USERNAME"
  echo ""

  TOKEN_RESPONSE=$(curl -s -X POST "$MOCK_OIDC_URL/$ISSUER_ID/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=authorization_code&code=$AUTH_CODE&redirect_uri=$REDIRECT_URI&client_id=$USERNAME&client_secret=$CLIENT_SECRET")

  ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token' 2>/dev/null)
  ID_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.id_token' 2>/dev/null)
  REFRESH_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.refresh_token' 2>/dev/null)

  if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
    echo -e "  ${RED}❌ Failed to exchange code for tokens${NC}"
    echo "  Response: $TOKEN_RESPONSE"
    exit 1
  fi

  echo -e "  ${GREEN}✓ Tokens received${NC}"
  echo "  Access Token:  ${ACCESS_TOKEN:0:50}..."
  [ "$ID_TOKEN" != "null" ] && echo "  ID Token:      ${ID_TOKEN:0:50}..."
  [ "$REFRESH_TOKEN" != "null" ] && echo "  Refresh Token: ${REFRESH_TOKEN:0:50}..."
  echo ""
fi

##############################################################################
# Step 3: Decode and Verify Token Claims
##############################################################################
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}Step 3: Decode and Verify Token Claims${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Decode the JWT payload
PAYLOAD_B64=$(echo "$ACCESS_TOKEN" | cut -d'.' -f2)
PAYLOAD_PADDED=$(echo "$PAYLOAD_B64" | tr '_-' '/+' | awk '{l=length($0); if (l%4!=0) {for(i=1;i<=4-l%4;i++) $0=$0"="} print}')
PAYLOAD_JSON=$(echo "$PAYLOAD_PADDED" | base64 -d 2>/dev/null || echo "$PAYLOAD_PADDED" | base64 -D 2>/dev/null)

echo "  Token Claims:"
echo "$PAYLOAD_JSON" | jq . 2>/dev/null || echo "$PAYLOAD_JSON"
echo ""

# Extract actual claims
ACTUAL_SUB=$(echo "$PAYLOAD_JSON" | jq -r '.sub' 2>/dev/null)
ACTUAL_EMAIL=$(echo "$PAYLOAD_JSON" | jq -r '.email' 2>/dev/null)
ACTUAL_NAME=$(echo "$PAYLOAD_JSON" | jq -r '.name' 2>/dev/null)

echo "  Claim Verification:"
if [ "$ACTUAL_SUB" = "$EXPECTED_SUB" ]; then
  echo -e "    sub:   $ACTUAL_SUB ${GREEN}✓${NC}"
else
  echo -e "    sub:   $ACTUAL_SUB ${RED}✗ (expected: $EXPECTED_SUB)${NC}"
fi

if [ "$ACTUAL_EMAIL" = "$EXPECTED_EMAIL" ]; then
  echo -e "    email: $ACTUAL_EMAIL ${GREEN}✓${NC}"
else
  echo -e "    email: $ACTUAL_EMAIL ${RED}✗ (expected: $EXPECTED_EMAIL)${NC}"
fi

if [ "$ACTUAL_NAME" = "$EXPECTED_NAME" ]; then
  echo -e "    name:  $ACTUAL_NAME ${GREEN}✓${NC}"
else
  echo -e "    name:  $ACTUAL_NAME ${RED}✗ (expected: $EXPECTED_NAME)${NC}"
fi
echo ""

##############################################################################
# Step 3.1: Security Tests - JWT Validation
##############################################################################
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}Step 3.1: Security Tests - JWT Validation${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

TEST_API_URL="$ENVOY_URL$CONTEXT_PATH/api/v1/users"

# ─────────────────────────────────────────────────────────────────────────
# Test 3.1.1: Missing JWT Token (No Authorization header)
# ─────────────────────────────────────────────────────────────────────────
echo -e "  ${YELLOW}Test 3.1.1: Missing JWT Token (No Authorization header)${NC}"
echo ""
echo "    Calling: GET $TEST_API_URL"
echo "    Authorization: (none)"
echo "    Expected: HTTP 401 Unauthorized"
echo ""

NO_TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$TEST_API_URL" \
  -H "Content-Type: application/json")

NO_TOKEN_HTTP_CODE=$(echo "$NO_TOKEN_RESPONSE" | tail -1)
NO_TOKEN_BODY=$(echo "$NO_TOKEN_RESPONSE" | sed '$d')

echo "    HTTP Status: $NO_TOKEN_HTTP_CODE"

if [ "$NO_TOKEN_HTTP_CODE" = "401" ]; then
  echo -e "    ${GREEN}✓ Correctly returned 401 Unauthorized${NC}"
  echo "    Response: $NO_TOKEN_BODY"
  NO_TOKEN_TEST_PASSED=true
else
  echo -e "    ${RED}✗ Expected 401, got $NO_TOKEN_HTTP_CODE${NC}"
  echo "    Response: $NO_TOKEN_BODY"
  NO_TOKEN_TEST_PASSED=false
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# Test 3.1.2: Invalid JWT Token (Malformed token)
# ─────────────────────────────────────────────────────────────────────────
echo -e "  ${YELLOW}Test 3.1.2: Invalid JWT Token (Malformed token)${NC}"
echo ""

INVALID_TOKEN="invalid.token.here"
echo "    Calling: GET $TEST_API_URL"
echo "    Authorization: Bearer $INVALID_TOKEN"
echo "    Expected: HTTP 401 Unauthorized"
echo ""

INVALID_TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$TEST_API_URL" \
  -H "Authorization: Bearer $INVALID_TOKEN" \
  -H "Content-Type: application/json")

INVALID_TOKEN_HTTP_CODE=$(echo "$INVALID_TOKEN_RESPONSE" | tail -1)
INVALID_TOKEN_BODY=$(echo "$INVALID_TOKEN_RESPONSE" | sed '$d')

echo "    HTTP Status: $INVALID_TOKEN_HTTP_CODE"

if [ "$INVALID_TOKEN_HTTP_CODE" = "401" ]; then
  echo -e "    ${GREEN}✓ Correctly returned 401 Unauthorized${NC}"
  echo "    Response: $INVALID_TOKEN_BODY"
  INVALID_TOKEN_TEST_PASSED=true
else
  echo -e "    ${RED}✗ Expected 401, got $INVALID_TOKEN_HTTP_CODE${NC}"
  echo "    Response: $INVALID_TOKEN_BODY"
  INVALID_TOKEN_TEST_PASSED=false
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# Test 3.1.3: Expired JWT Token (Simulated)
# ─────────────────────────────────────────────────────────────────────────
echo -e "  ${YELLOW}Test 3.1.3: Expired/Tampered JWT Token${NC}"
echo ""

# Create a tampered token by modifying the signature
TAMPERED_TOKEN="${ACCESS_TOKEN%??}xx"
echo "    Calling: GET $TEST_API_URL"
echo "    Authorization: Bearer ${TAMPERED_TOKEN:0:50}... (tampered)"
echo "    Expected: HTTP 401 Unauthorized"
echo ""

TAMPERED_TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$TEST_API_URL" \
  -H "Authorization: Bearer $TAMPERED_TOKEN" \
  -H "Content-Type: application/json")

TAMPERED_TOKEN_HTTP_CODE=$(echo "$TAMPERED_TOKEN_RESPONSE" | tail -1)
TAMPERED_TOKEN_BODY=$(echo "$TAMPERED_TOKEN_RESPONSE" | sed '$d')

echo "    HTTP Status: $TAMPERED_TOKEN_HTTP_CODE"

if [ "$TAMPERED_TOKEN_HTTP_CODE" = "401" ]; then
  echo -e "    ${GREEN}✓ Correctly returned 401 Unauthorized${NC}"
  echo "    Response: $TAMPERED_TOKEN_BODY"
  TAMPERED_TOKEN_TEST_PASSED=true
else
  echo -e "    ${RED}✗ Expected 401, got $TAMPERED_TOKEN_HTTP_CODE${NC}"
  echo "    Response: $TAMPERED_TOKEN_BODY"
  TAMPERED_TOKEN_TEST_PASSED=false
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────
# Test 3.1.4: Wrong Authorization Scheme (Basic instead of Bearer)
# ─────────────────────────────────────────────────────────────────────────
echo -e "  ${YELLOW}Test 3.1.4: Wrong Authorization Scheme (Basic instead of Bearer)${NC}"
echo ""

BASIC_AUTH=$(echo -n "user:password" | base64)
echo "    Calling: GET $TEST_API_URL"
echo "    Authorization: Basic $BASIC_AUTH"
echo "    Expected: HTTP 401 Unauthorized"
echo ""

WRONG_SCHEME_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$TEST_API_URL" \
  -H "Authorization: Basic $BASIC_AUTH" \
  -H "Content-Type: application/json")

WRONG_SCHEME_HTTP_CODE=$(echo "$WRONG_SCHEME_RESPONSE" | tail -1)
WRONG_SCHEME_BODY=$(echo "$WRONG_SCHEME_RESPONSE" | sed '$d')

echo "    HTTP Status: $WRONG_SCHEME_HTTP_CODE"

if [ "$WRONG_SCHEME_HTTP_CODE" = "401" ]; then
  echo -e "    ${GREEN}✓ Correctly returned 401 Unauthorized${NC}"
  echo "    Response: $WRONG_SCHEME_BODY"
  WRONG_SCHEME_TEST_PASSED=true
else
  echo -e "    ${RED}✗ Expected 401, got $WRONG_SCHEME_HTTP_CODE${NC}"
  echo "    Response: $WRONG_SCHEME_BODY"
  WRONG_SCHEME_TEST_PASSED=false
fi
echo ""

##############################################################################
# Step 4: Call Protected API Through Envoy
##############################################################################
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}Step 4: Call Protected API Through Envoy Gateway${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

API_URL="$ENVOY_URL$CONTEXT_PATH/api/v1/users"
echo "  Calling: GET $API_URL"
echo "  Authorization: Bearer ${ACCESS_TOKEN}..."
echo ""

API_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$API_URL" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json")

HTTP_CODE=$(echo "$API_RESPONSE" | tail -1)
RESPONSE_BODY=$(echo "$API_RESPONSE" | sed '$d')

echo "  HTTP Status: $HTTP_CODE"
echo ""

if [ "$HTTP_CODE" = "200" ]; then
  echo -e "  ${GREEN}✓ API call successful${NC}"
  echo ""
  echo "  Response:"
  echo "$RESPONSE_BODY" | jq . 2>/dev/null || echo "$RESPONSE_BODY"
else
  echo -e "  ${RED}✗ API call failed${NC}"
  echo ""
  echo "  Response:"
  echo "$RESPONSE_BODY"
fi
echo ""

##############################################################################
# Step 4.1: Login Flow with Session Management
##############################################################################
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}Step 4.1: Login Flow with Session Management (Redis)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

LOGIN_API_URL="$ENVOY_URL$CONTEXT_PATH/api/v1/users/login"
SESSION_API_URL="$ENVOY_URL$CONTEXT_PATH/api/v1/session"
COOKIE_FILE="/tmp/integration_test_cookies_$$"

# Clean up old cookie file
rm -f "$COOKIE_FILE"

# Step 4.1.1: First login (get available organizations)
echo -e "  ${YELLOW}Step 4.1.1: First login (get available organizations)${NC}"
echo "  Calling: POST $LOGIN_API_URL"
echo ""

LOGIN_REQUEST_BODY='{
  "firstName": "Test",
  "lastName": "User"
}'

echo "  Request Body:"
echo "$LOGIN_REQUEST_BODY" | jq . 2>/dev/null
echo ""

# Login and capture session cookie
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$LOGIN_API_URL" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -c "$COOKIE_FILE" \
  -d "$LOGIN_REQUEST_BODY")

LOGIN_HTTP_CODE=$(echo "$LOGIN_RESPONSE" | tail -1)
LOGIN_RESPONSE_BODY=$(echo "$LOGIN_RESPONSE" | sed '$d')

echo "  HTTP Status: $LOGIN_HTTP_CODE"
echo ""

# Check both field names (isFirstLogin and firstLogin) as backend may use either
IS_FIRST_LOGIN=$(echo "$LOGIN_RESPONSE_BODY" | jq -r '.isFirstLogin // .firstLogin' 2>/dev/null)
REQUIRES_ORG_SELECTION=$(echo "$LOGIN_RESPONSE_BODY" | jq -r '.requiresOrgSelection' 2>/dev/null)
AVAILABLE_ORGS=$(echo "$LOGIN_RESPONSE_BODY" | jq -r '.availableOrgs' 2>/dev/null)
AVAILABLE_ORGS_COUNT=$(echo "$LOGIN_RESPONSE_BODY" | jq -r '.availableOrgs | length' 2>/dev/null)
ASSOCIATED_ORGS_COUNT=$(echo "$LOGIN_RESPONSE_BODY" | jq -r '.associatedOrgs | length' 2>/dev/null)

if [ "$LOGIN_HTTP_CODE" = "200" ]; then
  echo -e "  ${GREEN}✓ Login API call successful${NC}"
  echo ""
  echo "  Response:"
  echo "$LOGIN_RESPONSE_BODY" | jq . 2>/dev/null
  echo ""
  echo "  Analysis:"
  echo "    isFirstLogin: $IS_FIRST_LOGIN"
  echo "    requiresOrgSelection: $REQUIRES_ORG_SELECTION"
  echo "    availableOrgs count: $AVAILABLE_ORGS_COUNT"
  echo "    associatedOrgs count: $ASSOCIATED_ORGS_COUNT"

  # Show session cookie
  if [ -f "$COOKIE_FILE" ]; then
    SESSION_COOKIE=$(grep -i "SESSION" "$COOKIE_FILE" | awk '{print $NF}' | head -1)
    echo "    Session Cookie: ${SESSION_COOKIE:0:20}..."
  fi
else
  echo -e "  ${RED}✗ Login API call failed${NC}"
  echo "  Response: $LOGIN_RESPONSE_BODY"
fi
echo ""

# Step 4.1.2: Select Organization and Complete Registration (if first-time user)
echo -e "  ${YELLOW}Step 4.1.2: Select Organization and Complete Registration${NC}"
echo ""

SELECTED_ORG_ID=""
SELECTED_ORG_NAME=""
SKIP_REGISTRATION=false

# Check if user already has associated orgs (returning user)
if [ "$ASSOCIATED_ORGS_COUNT" -gt 0 ] 2>/dev/null; then
  SELECTED_ORG_ID=$(echo "$LOGIN_RESPONSE_BODY" | jq -r '.associatedOrgs[0].id' 2>/dev/null)
  SELECTED_ORG_NAME=$(echo "$LOGIN_RESPONSE_BODY" | jq -r '.associatedOrgs[0].name' 2>/dev/null)
  SKIP_REGISTRATION=true
  echo -e "  ${GREEN}✓ User already registered${NC}"
  echo "  Using existing associated organization"
# Otherwise, get from available orgs (first-time user)
elif [ "$AVAILABLE_ORGS" != "null" ] && [ "$AVAILABLE_ORGS_COUNT" -gt 0 ] 2>/dev/null; then
  SELECTED_ORG_ID=$(echo "$LOGIN_RESPONSE_BODY" | jq -r '.availableOrgs[0].id' 2>/dev/null)
  SELECTED_ORG_NAME=$(echo "$LOGIN_RESPONSE_BODY" | jq -r '.availableOrgs[0].name' 2>/dev/null)
  echo "  First-time user, will register with selected organization"
fi

if [ -z "$SELECTED_ORG_ID" ] || [ "$SELECTED_ORG_ID" = "null" ]; then
  echo -e "  ${RED}✗ No organizations available${NC}"
  SKIP_REMAINING_TESTS=true
else
  echo "  Selected Organization:"
  echo "    ID:   $SELECTED_ORG_ID"
  echo "    Name: $SELECTED_ORG_NAME"
  echo ""

  # Complete registration if first-time login with available orgs
  if [ "$SKIP_REGISTRATION" != "true" ] && [ "$IS_FIRST_LOGIN" = "true" ] && [ "$AVAILABLE_ORGS_COUNT" -gt 0 ] 2>/dev/null; then
    REGISTER_REQUEST='{
      "firstName": "Test",
      "lastName": "User",
      "associateWithOrgIds": ["'$SELECTED_ORG_ID'"]
    }'

    echo "  Completing registration with org association..."
    echo "  Request Body:"
    echo "$REGISTER_REQUEST" | jq . 2>/dev/null
    echo ""

    REGISTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$LOGIN_API_URL" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -c "$COOKIE_FILE" \
      -d "$REGISTER_REQUEST")

    REGISTER_HTTP_CODE=$(echo "$REGISTER_RESPONSE" | tail -1)
    REGISTER_BODY=$(echo "$REGISTER_RESPONSE" | sed '$d')

    echo "  HTTP Status: $REGISTER_HTTP_CODE"
    if [ "$REGISTER_HTTP_CODE" = "200" ]; then
      echo -e "  ${GREEN}✓ Registration successful${NC}"
      echo "  Response:"
      echo "$REGISTER_BODY" | jq . 2>/dev/null

      # Update org selection status after registration
      REQUIRES_ORG_SELECTION=$(echo "$REGISTER_BODY" | jq -r '.requiresOrgSelection' 2>/dev/null)
    else
      echo -e "  ${RED}✗ Registration failed${NC}"
      echo "  Response: $REGISTER_BODY"
      SKIP_REMAINING_TESTS=true
    fi
  else
    echo "  User already registered, skipping registration step"
  fi
fi
echo ""

# Step 4.1.3: Select Organization for Session (via /api/v1/session/org)
echo -e "  ${YELLOW}Step 4.1.3: Select Organization for Session (POST /api/v1/session/org)${NC}"
echo ""

if [ "$SKIP_REMAINING_TESTS" != "true" ] && [ "$REQUIRES_ORG_SELECTION" = "true" ]; then
  ORG_SELECT_REQUEST='{"organizationId": "'$SELECTED_ORG_ID'"}'

  echo "  Calling: POST $SESSION_API_URL/org"
  echo "  Request Body: $ORG_SELECT_REQUEST"
  echo "  Using session cookie from login"
  echo ""

  ORG_SELECT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$SESSION_API_URL/org" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -b "$COOKIE_FILE" \
    -c "$COOKIE_FILE" \
    -d "$ORG_SELECT_REQUEST")

  ORG_SELECT_HTTP_CODE=$(echo "$ORG_SELECT_RESPONSE" | tail -1)
  ORG_SELECT_BODY=$(echo "$ORG_SELECT_RESPONSE" | sed '$d')

  echo "  HTTP Status: $ORG_SELECT_HTTP_CODE"
  if [ "$ORG_SELECT_HTTP_CODE" = "200" ]; then
    echo -e "  ${GREEN}✓ Organization selected for session${NC}"
    echo "  Response:"
    echo "$ORG_SELECT_BODY" | jq . 2>/dev/null
    ORG_SELECT_TEST_PASSED=true

    # Verify session has org selected
    SESSION_ORG_ID=$(echo "$ORG_SELECT_BODY" | jq -r '.selectedOrgId' 2>/dev/null)
    SESSION_ORG_NAME=$(echo "$ORG_SELECT_BODY" | jq -r '.selectedOrgName' 2>/dev/null)
    SESSION_ORG_REQUIRED=$(echo "$ORG_SELECT_BODY" | jq -r '.orgSelectionRequired' 2>/dev/null)

    echo ""
    echo "  Session Verification:"
    echo "    selectedOrgId: $SESSION_ORG_ID"
    echo "    selectedOrgName: $SESSION_ORG_NAME"
    echo "    orgSelectionRequired: $SESSION_ORG_REQUIRED"

    if [ "$SESSION_ORG_REQUIRED" = "false" ]; then
      echo -e "    ${GREEN}✓ Organization selection complete${NC}"
    fi
  else
    echo -e "  ${RED}✗ Organization selection failed${NC}"
    echo "  Response: $ORG_SELECT_BODY"
    ORG_SELECT_TEST_PASSED=false
  fi
elif [ "$SKIP_REMAINING_TESTS" != "true" ]; then
  echo "  Organization already selected (single org user or auto-selected)"
  ORG_SELECT_TEST_PASSED=true
fi
echo ""

# Step 4.1.4: Verify Session State (GET /api/v1/session)
echo -e "  ${YELLOW}Step 4.1.4: Verify Session State (GET /api/v1/session)${NC}"
echo ""

if [ "$SKIP_REMAINING_TESTS" != "true" ]; then
  echo "  Calling: GET $SESSION_API_URL"
  echo "  Using session cookie"
  echo ""

  SESSION_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$SESSION_API_URL" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -b "$COOKIE_FILE")

  SESSION_HTTP_CODE=$(echo "$SESSION_RESPONSE" | tail -1)
  SESSION_BODY=$(echo "$SESSION_RESPONSE" | sed '$d')

  echo "  HTTP Status: $SESSION_HTTP_CODE"
  if [ "$SESSION_HTTP_CODE" = "200" ]; then
    echo -e "  ${GREEN}✓ Session retrieved successfully${NC}"
    echo "  Response:"
    echo "$SESSION_BODY" | jq . 2>/dev/null
    SESSION_VERIFY_TEST_PASSED=true
  else
    echo -e "  ${RED}✗ Failed to retrieve session${NC}"
    echo "  Response: $SESSION_BODY"
    SESSION_VERIFY_TEST_PASSED=false
  fi
fi
echo ""

##############################################################################
# Step 4.2: Credential CRUD Operations (Using Session Cookie)
##############################################################################
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}Step 4.2: Credential CRUD Operations (Using Session)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if [ "$SKIP_REMAINING_TESTS" != "true" ] && [ -n "$SELECTED_ORG_ID" ] && [ "$SELECTED_ORG_ID" != "null" ]; then
  CREDS_API_URL="$ENVOY_URL$CONTEXT_PATH/api/v1/credentials"

  echo "  Note: Using session cookie for org context (stored in Redis)"
  echo ""

  # ─────────────────────────────────────────────────────────────────────────
  # Test 4.2.1: CREATE Credential (using session)
  # ─────────────────────────────────────────────────────────────────────────
  echo -e "  ${YELLOW}Test 4.2.1: CREATE Credential${NC}"
  echo ""

  CRED_NAME="test-cred-$(date +%s)"
  CREATE_CRED_BODY='{
    "name": "'$CRED_NAME'",
    "validityInDays": 30
  }'

  echo "    POST $CREDS_API_URL"
  echo "    Using session cookie (org context from Redis session)"
  echo "    Body: $CREATE_CRED_BODY"
  echo ""

  CREATE_CRED_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$CREDS_API_URL" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -b "$COOKIE_FILE" \
    -d "$CREATE_CRED_BODY")

  CREATE_CRED_HTTP_CODE=$(echo "$CREATE_CRED_RESPONSE" | tail -1)
  CREATE_CRED_BODY=$(echo "$CREATE_CRED_RESPONSE" | sed '$d')

  echo "    HTTP Status: $CREATE_CRED_HTTP_CODE"

  if [ "$CREATE_CRED_HTTP_CODE" = "200" ] || [ "$CREATE_CRED_HTTP_CODE" = "201" ]; then
    echo -e "    ${GREEN}✓ Credential created${NC}"
    CREATED_CRED_ID=$(echo "$CREATE_CRED_BODY" | jq -r '.id' 2>/dev/null)
    CREATED_CLIENT_SECRET=$(echo "$CREATE_CRED_BODY" | jq -r '.clientSecret' 2>/dev/null)
    echo "    ID: $CREATED_CRED_ID"
    echo "    Client Secret (unmasked): $CREATED_CLIENT_SECRET"
    echo ""
    echo "    Response:"
    echo "$CREATE_CRED_BODY" | jq . 2>/dev/null
  else
    echo -e "    ${RED}✗ Failed to create credential${NC}"
    echo "    Response: $CREATE_CRED_BODY"
    SKIP_REMAINING_TESTS=true
  fi
  echo ""

  # ─────────────────────────────────────────────────────────────────────────
  # Test 4.2.2: GET Credential by ID (should be MASKED)
  # ─────────────────────────────────────────────────────────────────────────
  if [ "$SKIP_REMAINING_TESTS" != "true" ] && [ -n "$CREATED_CRED_ID" ]; then
    echo -e "  ${YELLOW}Test 4.2.2: GET Credential by ID (should be MASKED)${NC}"
    echo ""

    echo "    GET $CREDS_API_URL/$CREATED_CRED_ID"
    echo "    Using session cookie"
    echo ""

    GET_CRED_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$CREDS_API_URL/$CREATED_CRED_ID" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -b "$COOKIE_FILE")

    GET_CRED_HTTP_CODE=$(echo "$GET_CRED_RESPONSE" | tail -1)
    GET_CRED_BODY=$(echo "$GET_CRED_RESPONSE" | sed '$d')

    echo "    HTTP Status: $GET_CRED_HTTP_CODE"

    if [ "$GET_CRED_HTTP_CODE" = "200" ]; then
      echo -e "    ${GREEN}✓ Credential retrieved${NC}"
      GET_CLIENT_SECRET=$(echo "$GET_CRED_BODY" | jq -r '.clientSecret' 2>/dev/null)
      echo ""
      echo "    Response:"
      echo "$GET_CRED_BODY" | jq . 2>/dev/null
      echo ""

      # Verify secret is masked
      if [ "$GET_CLIENT_SECRET" != "$CREATED_CLIENT_SECRET" ]; then
        echo -e "    ${GREEN}✓ Client Secret is MASKED: $GET_CLIENT_SECRET${NC}"
        GET_MASKED_TEST_PASSED=true
      else
        echo -e "    ${RED}✗ Client Secret is NOT masked${NC}"
        GET_MASKED_TEST_PASSED=false
      fi
    else
      echo -e "    ${RED}✗ Failed to get credential${NC}"
      echo "    Response: $GET_CRED_BODY"
    fi
    echo ""

    # ─────────────────────────────────────────────────────────────────────────
    # Test 4.2.3: RESET Credential Secret
    # ─────────────────────────────────────────────────────────────────────────
    echo -e "  ${YELLOW}Test 4.2.3: RESET Credential Secret${NC}"
    echo ""

    echo "    PATCH $CREDS_API_URL/$CREATED_CRED_ID/reset-secret"
    echo "    Using session cookie"
    echo ""

    RESET_CRED_RESPONSE=$(curl -s -w "\n%{http_code}" -X PATCH "$CREDS_API_URL/$CREATED_CRED_ID/reset-secret" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -b "$COOKIE_FILE")

    RESET_CRED_HTTP_CODE=$(echo "$RESET_CRED_RESPONSE" | tail -1)
    RESET_CRED_BODY=$(echo "$RESET_CRED_RESPONSE" | sed '$d')

    echo "    HTTP Status: $RESET_CRED_HTTP_CODE"

    if [ "$RESET_CRED_HTTP_CODE" = "200" ]; then
      echo -e "    ${GREEN}✓ Credential secret reset${NC}"
      RESET_CLIENT_SECRET=$(echo "$RESET_CRED_BODY" | jq -r '.clientSecret' 2>/dev/null)
      echo ""
      echo "    Response:"
      echo "$RESET_CRED_BODY" | jq . 2>/dev/null
      echo ""

      if [ "$RESET_CLIENT_SECRET" != "$CREATED_CLIENT_SECRET" ]; then
        echo -e "    ${GREEN}✓ New secret is different from original${NC}"
        echo "      Old: $CREATED_CLIENT_SECRET"
        echo "      New: $RESET_CLIENT_SECRET"
        RESET_TEST_PASSED=true
      else
        echo -e "    ${RED}✗ Secret unchanged${NC}"
        RESET_TEST_PASSED=false
      fi
    else
      echo -e "    ${RED}✗ Failed to reset secret${NC}"
      echo "    Response: $RESET_CRED_BODY"
    fi
    echo ""

    # ─────────────────────────────────────────────────────────────────────────
    # Test 4.2.4: DELETE Credential
    # ─────────────────────────────────────────────────────────────────────────
    echo -e "  ${YELLOW}Test 4.2.4: DELETE Credential${NC}"
    echo ""

    echo "    DELETE $CREDS_API_URL/$CREATED_CRED_ID"
    echo "    Using session cookie"
    echo ""

    DELETE_CRED_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$CREDS_API_URL/$CREATED_CRED_ID" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -b "$COOKIE_FILE")

    DELETE_CRED_HTTP_CODE=$(echo "$DELETE_CRED_RESPONSE" | tail -1)

    echo "    HTTP Status: $DELETE_CRED_HTTP_CODE"

    if [ "$DELETE_CRED_HTTP_CODE" = "204" ] || [ "$DELETE_CRED_HTTP_CODE" = "200" ]; then
      echo -e "    ${GREEN}✓ Credential deleted${NC}"
      DELETE_TEST_PASSED=true
    else
      echo -e "    ${RED}✗ Failed to delete${NC}"
      DELETE_TEST_PASSED=false
    fi
    echo ""

    # ─────────────────────────────────────────────────────────────────────────
    # Test 4.2.5: GET Deleted Credential (should return 404)
    # ─────────────────────────────────────────────────────────────────────────
    echo -e "  ${YELLOW}Test 4.2.5: GET Deleted Credential (expect 404)${NC}"
    echo ""

    echo "    GET $CREDS_API_URL/$CREATED_CRED_ID"
    echo "    Expected: HTTP 404"
    echo "    Using session cookie"
    echo ""

    GET_DELETED_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$CREDS_API_URL/$CREATED_CRED_ID" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -H "Content-Type: application/json" \
      -b "$COOKIE_FILE")

    GET_DELETED_HTTP_CODE=$(echo "$GET_DELETED_RESPONSE" | tail -1)
    GET_DELETED_BODY=$(echo "$GET_DELETED_RESPONSE" | sed '$d')

    echo "    HTTP Status: $GET_DELETED_HTTP_CODE"

    if [ "$GET_DELETED_HTTP_CODE" = "404" ]; then
      echo -e "    ${GREEN}✓ Correctly returned 404 Not Found${NC}"
      GET_404_TEST_PASSED=true
    else
      echo -e "    ${RED}✗ Expected 404, got $GET_DELETED_HTTP_CODE${NC}"
      echo "    Response: $GET_DELETED_BODY"
      GET_404_TEST_PASSED=false
    fi
  fi
else
  echo -e "  ${YELLOW}ℹ Skipping credential tests (no org available)${NC}"
fi
echo ""

##############################################################################
# Step 5: Verify Headers Were Forwarded to Backend
##############################################################################
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}Step 5: Verify Headers Forwarded by Envoy${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

echo "  Checking backend logs for x-user-* headers..."
echo ""

# Get the last few log entries from backend
BACKEND_LOGS=$(docker compose logs backend_service --tail 20 2>&1 | grep -E "x-user-sub|x-user-email|x-user-name" | tail -5)

if [ -n "$BACKEND_LOGS" ]; then
  echo -e "  ${GREEN}✓ Headers found in backend logs:${NC}"
  echo "$BACKEND_LOGS" | while read -r line; do
    echo "    $line"
  done
else
  echo "  No x-user-* headers found in recent logs."
  echo "  Run: docker compose logs backend_service --tail 50 | grep x-user"
fi
echo ""

##############################################################################
# Summary
##############################################################################
echo -e "${BLUE}============================================================================${NC}"
echo -e "${BLUE}   Integration Test Summary${NC}"
echo -e "${BLUE}============================================================================${NC}"
echo ""

TESTS_PASSED=0
TESTS_FAILED=0

# Check token claims
if [ "$ACTUAL_SUB" = "$EXPECTED_SUB" ] && [ "$ACTUAL_EMAIL" = "$EXPECTED_EMAIL" ]; then
  echo -e "  ${GREEN}✓${NC} Token claims match expected values"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} Token claims do not match"
  ((TESTS_FAILED++))
fi

# Security tests - JWT validation
if [ "$NO_TOKEN_TEST_PASSED" = "true" ]; then
  echo -e "  ${GREEN}✓${NC} Missing JWT returns 401 Unauthorized"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} Missing JWT did not return 401"
  ((TESTS_FAILED++))
fi

if [ "$INVALID_TOKEN_TEST_PASSED" = "true" ]; then
  echo -e "  ${GREEN}✓${NC} Invalid JWT returns 401 Unauthorized"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} Invalid JWT did not return 401"
  ((TESTS_FAILED++))
fi

if [ "$TAMPERED_TOKEN_TEST_PASSED" = "true" ]; then
  echo -e "  ${GREEN}✓${NC} Tampered JWT returns 401 Unauthorized"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} Tampered JWT did not return 401"
  ((TESTS_FAILED++))
fi

if [ "$WRONG_SCHEME_TEST_PASSED" = "true" ]; then
  echo -e "  ${GREEN}✓${NC} Wrong auth scheme returns 401 Unauthorized"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} Wrong auth scheme did not return 401"
  ((TESTS_FAILED++))
fi

# Check API response
if [ "$HTTP_CODE" = "200" ]; then
  echo -e "  ${GREEN}✓${NC} GET /api/v1/users returned HTTP 200"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} GET /api/v1/users failed with HTTP $HTTP_CODE"
  ((TESTS_FAILED++))
fi

# Check first-time login test
if [ "$LOGIN_HTTP_CODE" = "200" ]; then
  echo -e "  ${GREEN}✓${NC} POST /api/v1/users/login returned HTTP 200"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} POST /api/v1/users/login failed with HTTP $LOGIN_HTTP_CODE"
  ((TESTS_FAILED++))
fi

# Check org selection via session API
if [ "$ORG_SELECT_TEST_PASSED" = "true" ]; then
  echo -e "  ${GREEN}✓${NC} POST /api/v1/session/org - org selection successful"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} POST /api/v1/session/org - org selection failed"
  ((TESTS_FAILED++))
fi

# Check session verification
if [ "$SESSION_VERIFY_TEST_PASSED" = "true" ]; then
  echo -e "  ${GREEN}✓${NC} GET /api/v1/session - session verified"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} GET /api/v1/session - session verification failed"
  ((TESTS_FAILED++))
fi

# Credential CRUD tests
if [ "$CREATE_CRED_HTTP_CODE" = "200" ] || [ "$CREATE_CRED_HTTP_CODE" = "201" ]; then
  echo -e "  ${GREEN}✓${NC} CREATE credential successful"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} CREATE credential failed"
  ((TESTS_FAILED++))
fi

if [ "$GET_CRED_HTTP_CODE" = "200" ]; then
  echo -e "  ${GREEN}✓${NC} GET credential by ID successful"
  ((TESTS_PASSED++))
  if [ "$GET_MASKED_TEST_PASSED" = "true" ]; then
    echo -e "  ${GREEN}✓${NC} GET credential returns MASKED secret"
    ((TESTS_PASSED++))
  else
    echo -e "  ${RED}✗${NC} GET credential secret NOT masked"
    ((TESTS_FAILED++))
  fi
else
  echo -e "  ${RED}✗${NC} GET credential by ID failed"
  ((TESTS_FAILED++))
fi

if [ "$RESET_CRED_HTTP_CODE" = "200" ]; then
  echo -e "  ${GREEN}✓${NC} RESET credential secret successful"
  ((TESTS_PASSED++))
  if [ "$RESET_TEST_PASSED" = "true" ]; then
    echo -e "  ${GREEN}✓${NC} RESET generated new secret"
    ((TESTS_PASSED++))
  fi
else
  echo -e "  ${RED}✗${NC} RESET credential secret failed"
  ((TESTS_FAILED++))
fi

if [ "$DELETE_TEST_PASSED" = "true" ]; then
  echo -e "  ${GREEN}✓${NC} DELETE credential successful"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} DELETE credential failed"
  ((TESTS_FAILED++))
fi

if [ "$GET_404_TEST_PASSED" = "true" ]; then
  echo -e "  ${GREEN}✓${NC} GET deleted credential returns 404"
  ((TESTS_PASSED++))
else
  echo -e "  ${RED}✗${NC} GET deleted credential did NOT return 404"
  ((TESTS_FAILED++))
fi

echo ""
echo "  Tests Passed: $TESTS_PASSED"
echo "  Tests Failed: $TESTS_FAILED"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
  echo -e "  ${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "  ${GREEN}   ✅ ALL TESTS PASSED${NC}"
  echo -e "  ${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
else
  echo -e "  ${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "  ${RED}   ❌ SOME TESTS FAILED${NC}"
  echo -e "  ${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
fi

echo ""
echo "Available users to test:"
echo "  ./integration-test.sh admin-user      # Admin with admin,user roles"
echo "  ./integration-test.sh regular-user    # Regular user with user role"
echo "  ./integration-test.sh saravanan       # Saravanan with admin,user,developer roles"
echo ""

# Cleanup
rm -f "$COOKIE_FILE" 2>/dev/null

