#!/bin/bash

##############################################################################
# Token Verification Script
# Purpose: Verify that mock-OIDC is issuing tokens with configured claims
# This script handles token retrieval and decoding for different users
#
# Usage:
#   ./verify-token.sh                    # Uses default user (admin-user)
#   ./verify-token.sh admin-user         # Admin user with admin roles
#   ./verify-token.sh regular-user       # Regular user with user role only
#   ./verify-token.sh saravanan          # Saravanan with developer role
#   ./verify-token.sh <any-client-id>    # Falls back to default claims
##############################################################################

# Get username from command line argument, default to "admin-user"
USERNAME="${1:-admin-user}"

echo "============================================================================"
echo "JWT Token Verification Script"
echo "============================================================================"
echo ""

# Configuration
MOCK_OIDC_URL="http://localhost:8081"
ISSUER_ID="default"

echo "Configuration:"
echo "  Mock OIDC URL: $MOCK_OIDC_URL"
echo "  Issuer ID: $ISSUER_ID"
echo "  Username/Client ID: $USERNAME"
echo "  Token Endpoint: $MOCK_OIDC_URL/$ISSUER_ID/token"
echo ""

# Define expected claims based on username
case "$USERNAME" in
  "admin-user")
    EXPECTED_SUB="admin-123"
    EXPECTED_EMAIL="admin@test.com"
    EXPECTED_NAME="Admin User"
    EXPECTED_ROLES='["admin", "user"]'
    ;;
  "regular-user")
    EXPECTED_SUB="user-456"
    EXPECTED_EMAIL="user@test.com"
    EXPECTED_NAME="Regular User"
    EXPECTED_ROLES='["user"]'
    ;;
  "saravanan")
    EXPECTED_SUB="saravanan-789"
    EXPECTED_EMAIL="saravanan@test.com"
    EXPECTED_NAME="Saravanan"
    EXPECTED_ROLES='["admin", "user", "developer"]'
    ;;
  *)
    EXPECTED_SUB="default-user"
    EXPECTED_EMAIL="default@test.com"
    EXPECTED_NAME="Default User"
    EXPECTED_ROLES='["user"]'
    ;;
esac

echo "Expected Claims for '$USERNAME':"
echo "  sub:   $EXPECTED_SUB"
echo "  email: $EXPECTED_EMAIL"
echo "  name:  $EXPECTED_NAME"
echo "  roles: $EXPECTED_ROLES"
echo ""

# Step 1: Get token using client_credentials flow with username as client_id
echo "Step 1: Requesting token for user '$USERNAME'..."
echo "  Method: POST"
echo "  URL: $MOCK_OIDC_URL/$ISSUER_ID/token"
echo "  Parameters:"
echo "    - grant_type=client_credentials"
echo "    - client_id=$USERNAME"
echo "    - client_secret=secret"
echo "    - scope=openid"
echo ""

TOKEN_RESPONSE=$(curl -s -X POST "$MOCK_OIDC_URL/$ISSUER_ID/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$USERNAME&client_secret=secret&scope=openid")

echo "Token Response:"
echo "$TOKEN_RESPONSE" | jq . 2>/dev/null || echo "$TOKEN_RESPONSE"
echo ""

# Step 3: Extract the access_token
ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token' 2>/dev/null)

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  echo "❌ ERROR: Failed to extract access_token from response"
  echo "Response was:"
  echo "$TOKEN_RESPONSE"
  exit 1
fi

echo "✓ Access token extracted successfully"
echo ""

# Step 3: Display token parts
echo "Step 2: JWT Token Structure"
echo ""
echo "Full Token:"
echo "$ACCESS_TOKEN"
echo ""

# Split JWT into parts
IFS='.' read -r HEADER PAYLOAD SIGNATURE <<< "$ACCESS_TOKEN"

echo "Token Parts:"
echo "  Header (part 1):   ${HEADER:0:50}..."
echo "  Payload (part 2):  ${PAYLOAD:0:50}..."
echo "  Signature (part 3): ${SIGNATURE:0:50}..."
echo ""

# Step 4: Decode Header
echo "Step 3: JWT Header (decoded)"
# Add base64 padding if needed
HEADER_PADDED=$(echo "$HEADER" | tr '_-' '/+' | awk '{l=length($0); if (l%4!=0) {for(i=1;i<=4-l%4;i++) $0=$0"="} print}')
HEADER_DECODED=$(echo "$HEADER_PADDED" | base64 -d 2>/dev/null || echo "$HEADER_PADDED" | base64 -D 2>/dev/null)
echo "$HEADER_DECODED" | jq . 2>/dev/null || echo "$HEADER_DECODED"
echo ""

# Step 5: Decode Payload
echo "Step 4: JWT Payload (decoded) - THIS CONTAINS YOUR CLAIMS"
echo "=========================================================================="
# Add base64 padding if needed and handle URL-safe base64
PAYLOAD_PADDED=$(echo "$PAYLOAD" | tr '_-' '/+' | awk '{l=length($0); if (l%4!=0) {for(i=1;i<=4-l%4;i++) $0=$0"="} print}')
PAYLOAD_DECODED=$(echo "$PAYLOAD_PADDED" | base64 -d 2>/dev/null || echo "$PAYLOAD_PADDED" | base64 -D 2>/dev/null)
echo "$PAYLOAD_DECODED" | jq . 2>/dev/null || echo "$PAYLOAD_DECODED"
echo "=========================================================================="
echo ""

# Step 6: Verify expected claims
echo "Step 5: Verify Configured Claims"
echo ""

CLAIMS_JSON=$(echo "$PAYLOAD_DECODED" | jq . 2>/dev/null)

if [ -z "$CLAIMS_JSON" ]; then
  echo "❌ Could not parse JWT payload"
  exit 1
fi

echo "Expected Claims for '$USERNAME':"
echo "  sub:   $EXPECTED_SUB"
echo "  email: $EXPECTED_EMAIL"
echo "  name:  $EXPECTED_NAME"
echo "  roles: $EXPECTED_ROLES"
echo ""

echo "Actual Claims Found:"

SUB=$(echo "$CLAIMS_JSON" | jq -r '.sub' 2>/dev/null)
EMAIL=$(echo "$CLAIMS_JSON" | jq -r '.email' 2>/dev/null)
NAME=$(echo "$CLAIMS_JSON" | jq -r '.name' 2>/dev/null)
ROLES=$(echo "$CLAIMS_JSON" | jq -r '.roles' 2>/dev/null)

echo "  sub:   $SUB $([ "$SUB" = "$EXPECTED_SUB" ] && echo "✓" || echo "❌ MISMATCH")"
echo "  email: $EMAIL $([ "$EMAIL" = "$EXPECTED_EMAIL" ] && echo "✓" || echo "❌ MISMATCH")"
echo "  name:  $NAME $([ "$NAME" = "$EXPECTED_NAME" ] && echo "✓" || echo "❌ MISMATCH")"
echo "  roles: $ROLES $(echo "$ROLES" | grep -q "admin\|user" && echo "✓" || echo "❌ MISSING")"
echo ""

# Step 7: Additional claims info
echo "Step 6: Additional JWT Claims"
ISSUER=$(echo "$CLAIMS_JSON" | jq -r '.iss' 2>/dev/null)
EXPIRY=$(echo "$CLAIMS_JSON" | jq -r '.exp' 2>/dev/null)
ISSUED_AT=$(echo "$CLAIMS_JSON" | jq -r '.iat' 2>/dev/null)
JTI=$(echo "$CLAIMS_JSON" | jq -r '.jti' 2>/dev/null)

echo "  iss (issuer): $ISSUER"
echo "  exp (expiry):  $EXPIRY"
echo "  iat (issued):  $ISSUED_AT"
echo "  jti (id):      $JTI"
echo ""

# Step 8: Summary
echo "============================================================================"
echo "Summary for User: $USERNAME"
echo "============================================================================"

# Check if all claims are present and correct
CLAIMS_OK=true
[ "$SUB" != "$EXPECTED_SUB" ] && CLAIMS_OK=false
[ "$EMAIL" != "$EXPECTED_EMAIL" ] && CLAIMS_OK=false
[ "$NAME" != "$EXPECTED_NAME" ] && CLAIMS_OK=false

if [ "$CLAIMS_OK" = true ]; then
  echo "✅ SUCCESS: Token contains all configured claims correctly for '$USERNAME'!"
  echo ""
  echo "Token is ready to use for testing:"
  echo "  export TOKEN=\"$ACCESS_TOKEN\""
  echo "  curl -X GET http://localhost:10000/credential-manager/api/v1/credentials \\"
  echo "    -H \"Authorization: Bearer \$TOKEN\""
else
  echo "❌ FAILURE: Token is missing or has incorrect claims for '$USERNAME'"
  echo ""
  echo "Check mock-OIDC configuration in proxy/mock-oidc-config.json"
  echo "View mock-OIDC logs: docker compose logs mock-oidc"
fi

echo ""
echo "Available users to test:"
echo "  ./verify-token.sh admin-user      # Admin with admin,user roles"
echo "  ./verify-token.sh regular-user    # Regular user with user role"
echo "  ./verify-token.sh saravanan       # Saravanan with admin,user,developer roles"
echo "  ./verify-token.sh <any-name>      # Falls back to default user"
echo ""
