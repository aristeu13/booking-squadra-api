# Booking Squadra API Integration Report

This document is the frontend integration contract for the Booking Squadra mobile app. It is written for a Flutter client and for Claude-assisted implementation on the mobile side.

All application endpoints are under `/api/v1`. Infrastructure endpoints, such as `/health`, stay at the root.

## Integration Basics

Base URL:

- Local development: `http://localhost:8080`
- Android emulator against local backend: usually `http://10.0.2.2:8080`
- Production: use the deployed API host provided by the backend environment.

Default headers:

```http
Accept: application/json
Content-Type: application/json
```

Authenticated headers:

```http
Authorization: Bearer <accessToken>
```

Date and time formats:

- `LocalDate`: `YYYY-MM-DD`, for example `2026-05-10`.
- `LocalTime`: API accepts ISO local times. Prefer `HH:mm:ss` in request bodies, for example `18:00:00`.
- Available slot strings are returned as `HH:mm`, for example `18:00`.
- `OffsetDateTime`: ISO-8601 with offset, usually UTC, for example `2026-05-10T21:00:00Z`.
- Money values are integer cents. Never use floating point for prices in Flutter.
- UUID fields are strings on the wire.

Security:

- Public: `/health`, `/api/v1/auth/**`, `/api/v1/legal/**`, `/api-docs/**`, `/swagger-ui/**`, `/scalar/**`.
- Payment webhook: `/api/v1/payments-webhook/**` is public at Spring Security level but requires `asaas-access-token`. This is provider-facing, not mobile-facing.
- Admin: `/api/v1/admin/**` requires a JWT whose `role` claim maps to `ROLE_ADMIN`.
- All other endpoints require `Authorization: Bearer <accessToken>`.
- Access JWTs are stateless. Refresh tokens are opaque, stored only as server-side hashes, and rotated on refresh.

## Recommended Mobile Flow

1. Request OTP with `POST /api/v1/auth/otp/request`.
2. User enters the email code.
3. Verify OTP with `POST /api/v1/auth/otp/verify`.
4. Store `accessToken` and `refreshToken` securely, for example with Flutter secure storage.
5. Send `Authorization: Bearer <accessToken>` on all non-public app endpoints.
6. On access-token expiry, call `POST /api/v1/auth/refresh` with the current refresh token and replace both stored tokens.
7. On refresh failure, remove both tokens and return the user to the auth flow.

For booking:

1. Search cities if the user types a city name.
2. List venues with optional location and sport filters.
3. Load venue detail to get courts, operating hours, slot duration, and cancel policy.
4. Load available slots for a selected court and date.
5. Create booking using the selected `date`, `startTime`, and computed `endTime`.
6. Show bookings from `GET /api/v1/bookings/me`.

## Error Contract

Most errors return `application/problem+json` using Spring `ProblemDetail`.

Example:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/auth/otp/request",
  "code": "validation_failed",
  "timestamp": "2026-04-29T16:00:00Z",
  "errors": {
    "email": "must be a well-formed email address"
  }
}
```

Common status codes:

- `400`: malformed JSON, missing parameter, invalid query/path/body value, or validation failure.
- `401`: missing/invalid/expired token, invalid OTP, or deleted account.
- `403`: authenticated user does not have permission, usually non-admin accessing admin routes.
- `404`: resource not found or inactive venue/court hidden from users.
- `409`: booking slot conflict or data conflict.
- `415`: unsupported `Content-Type`.
- `429`: OTP request or verification rate limited.
- `500`: unexpected server error.

Common error `code` values:

- `validation_failed`
- `malformed_request`
- `missing_parameter`
- `type_mismatch`
- `method_not_allowed`
- `unsupported_media_type`
- `not_found`
- `data_conflict`
- `unauthorized`
- `forbidden`
- `rate_limited`
- `internal_error`

## Public Endpoints

### Health

`GET /health`

Purpose: lightweight liveness check.

Response `200`:

```json
{
  "status": "UP",
  "timestamp": "2026-04-29T16:00:00Z"
}
```

### Request Login OTP

`POST /api/v1/auth/otp/request`

Auth: public.

Request:

```json
{
  "email": "user@example.com"
}
```

Response: `202` with empty body.

Behavior notes:

- Email is normalized by trimming and lowercasing.
- If the user does not exist, the backend creates an active user record with empty `name`.
- OTP expires after 10 minutes.
- A recent OTP may be reused silently within the cooldown window, so the endpoint can still return `202` without sending a new email.
- Test environments may use configured OTP credentials.
- Rate limiting can return `429`.

### Verify Login OTP

`POST /api/v1/auth/otp/verify`

Auth: public.

Request:

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

Response `200`:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque-refresh-token>"
}
```

Behavior notes:

- Invalid or expired OTP returns `401`.
- Too many failed attempts for the same email/IP window returns `429`.
- JWT includes the user UUID as subject and a `role` claim.
- A new refresh token is issued with the access token.

### Refresh Token

`POST /api/v1/auth/refresh`

Auth: public.

Request:

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

Response `200`:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<new-opaque-refresh-token>"
}
```

Behavior notes:

- Refresh tokens are single-use. A successful refresh revokes the presented token and returns a new token pair.
- Invalid, expired, or revoked refresh tokens return `401`.

### Sign Out

`POST /api/v1/auth/signout`

Auth: route is public by security rule.

Request:

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

Response: `204` with empty body.

Mobile behavior:

- This endpoint revokes the presented refresh token when it is still active.
- The Flutter app should delete locally stored access and refresh tokens and clear auth state.

### Terms Of Use

`GET /api/v1/legal/terms`

Auth: public.

Response `200`:

```json
{
  "version": "2026-04-28",
  "title": "Termos e Condições de Uso do Aplicativo Squadra",
  "content": "...",
  "updatedAt": "2026-04-28"
}
```

## Authenticated Mobile Endpoints

### Current Profile

`GET /api/v1/profiles/me`

Auth: user JWT required.

Response `200`:

```json
{
  "id": "9f0a0f8e-7d8e-4f79-ae79-2bd9d0b1c9a0",
  "name": "Ana Silva",
  "email": "ana@example.com",
  "phone": "+5534999999999",
  "cpf": "52998224725",
  "googleAuth": false
}
```

### Update Profile

`PUT /api/v1/profiles/me`

Auth: user JWT required.

Request:

```json
{
  "name": "Ana Silva",
  "phone": "+5534999999999",
  "cpf": "52998224725"
}
```

Response `200`: `ProfileDto`.

Validation:

- `name`: optional, max 255 characters.
- `phone`: optional, max 32 characters.
- `cpf`: optional, max 32 characters; when provided, normalized to digits and validated (checksum). Send only digits or a common formatted CPF string.
- Fields omitted or sent as `null` are not changed.

### Request Account Deletion OTP

`POST /api/v1/profiles/me/delete/otp/request`

Auth: user JWT required.

Response: `202` with empty body.

Behavior notes:

- Sends a delete-account OTP to the current user's email.
- OTP expires after 10 minutes.

### Delete Current Account

`DELETE /api/v1/profiles/me`

Auth: user JWT required.

Request:

```json
{
  "code": "123456"
}
```

Response: `204` with empty body.

Behavior notes:

- Invalid or expired OTP returns `401`.
- The account is anonymized and marked deleted.
- The mobile app should delete the token and return to onboarding after success.

### Search Cities

`GET /api/v1/cities?q=<text>`

Auth: user JWT required.

Query:

- `q`: optional string. If blank or missing, returns an empty array.

Response `200`:

```json
[
  {
    "id": 3170206,
    "name": "Uberlandia",
    "stateCode": "MG",
    "latitude": -18.9146,
    "longitude": -48.2754
  }
]
```

Behavior notes:

- Max results: 50.
- Use this for city autocomplete.

### List Venues

`GET /api/v1/venues`

Auth: user JWT required.

Query params:

- `lat`: optional double, user latitude.
- `lon`: optional double, user longitude.
- `distance_km`: optional double. Defaults to `999.0` when omitted.
- `sports_filters`: optional repeated query param or comma-compatible list binding, depending on the client. Prefer repeated params from Flutter: `sports_filters=soccer&sports_filters=beach_tennis`.

Examples:

```http
GET /api/v1/venues
GET /api/v1/venues?lat=-18.91&lon=-48.27&distance_km=20
GET /api/v1/venues?sports_filters=soccer&sports_filters=padel
```

Response `200`:

```json
[
  {
    "id": "0b3fd1d3-7b96-49b5-8a54-01489441ef4d",
    "slug": "arena-squadra",
    "name": "Arena Squadra",
    "description": "Quadras esportivas",
    "imageUrl": "https://example.com/venue.jpg",
    "address": "Av. Brasil, 100",
    "cityId": 3170206,
    "city": "Uberlandia",
    "stateCode": "MG",
    "timezone": "America/Sao_Paulo",
    "sports": ["soccer", "padel"],
    "amenities": "{\"parking\":true}",
    "priceCents": 12000,
    "distanceKm": 3.42
  }
]
```

Important field notes:

- `distanceKm` is `null` when `lat` or `lon` is not provided.
- `amenities` is a JSON string in the list response.
- Only active venues are returned.

### Venue Detail

`GET /api/v1/venues/{id}`

Auth: user JWT required.

Response `200`:

```json
{
  "id": "0b3fd1d3-7b96-49b5-8a54-01489441ef4d",
  "name": "Arena Squadra",
  "slug": "arena-squadra",
  "description": "Quadras esportivas",
  "imageUrl": "https://example.com/venue.jpg",
  "address": "Av. Brasil, 100",
  "cityId": 3170206,
  "city": "Uberlandia",
  "stateCode": "MG",
  "timezone": "America/Sao_Paulo",
  "latitude": -18.9146,
  "longitude": -48.2754,
  "sports": ["soccer", "padel"],
  "amenities": {
    "parking": true,
    "lockerRoom": true
  },
  "priceCents": 12000,
  "slotDurationMinutes": 60,
  "active": true,
  "courts": [
    {
      "id": "77e09255-67b5-4cfa-80b5-62d9e8a59cb9",
      "venueId": "0b3fd1d3-7b96-49b5-8a54-01489441ef4d",
      "name": "Quadra 1",
      "surfaceType": "synthetic",
      "indoor": false,
      "sortOrder": 1,
      "active": true
    }
  ],
  "operatingHours": [
    {
      "dayOfWeek": 1,
      "openTime": "08:00:00",
      "closeTime": "22:00:00"
    }
  ],
  "cancelPolicy": {
    "pixFullRefundHours": 24,
    "pixPartialRefundHours": 12,
    "pixPartialRefundPercent": 50,
    "localCancelHours": 6,
    "noShowPixThreshold": 15
  }
}
```

Field notes:

- `dayOfWeek`: `0 = Sunday`, `1 = Monday`, ..., `6 = Saturday`.
- `closeTime` can be earlier than `openTime` for overnight operating hours.
- `cancelPolicy` can be `null` if not configured.
- `courts` includes active courts only for user-facing detail.

### Venue Cancel Policy

`GET /api/v1/venues/{id}/cancel-policy`

Auth: user JWT required.

Response `200`: `CancelPolicyDto`.

Possible errors:

- `404` if venue does not exist.
- `404` if cancel policy does not exist.

### Venue Booking Count

`GET /api/v1/venues/{id}/bookings/count`

Auth: user JWT required.

Response `200`:

```json
{
  "venueId": "0b3fd1d3-7b96-49b5-8a54-01489441ef4d",
  "count": 42
}
```

### Available Slots

`GET /api/v1/courts/{courtId}/available-slots?date=YYYY-MM-DD`

Auth: user JWT required.

Example:

```http
GET /api/v1/courts/77e09255-67b5-4cfa-80b5-62d9e8a59cb9/available-slots?date=2026-05-10
```

Response `200`:

```json
{
  "date": "2026-05-10",
  "timezone": "America/Sao_Paulo",
  "slotDurationMinutes": 60,
  "slots": ["08:00", "09:00", "10:00", "18:00"]
}
```

Behavior notes:

- `date` is interpreted in the venue timezone.
- Past dates return `400`.
- Slots are start times in venue-local time.
- Slots exclude existing `pending` and `confirmed` bookings.
- Slots exclude recurring maintenance blocks.
- For same-day requests, slots earlier than the current venue-local time are omitted.

### Create Booking

`POST /api/v1/bookings`

Auth: user JWT required.

Request:

```json
{
  "courtId": "77e09255-67b5-4cfa-80b5-62d9e8a59cb9",
  "bookingDate": "2026-05-10",
  "startTime": "18:00:00",
  "endTime": "19:00:00",
  "note": "Prefer side court if possible"
}
```

Response `201`:

```json
{
  "id": "c4e0b5da-1f56-4a26-bcd5-355f010f892f",
  "userId": "9f0a0f8e-7d8e-4f79-ae79-2bd9d0b1c9a0",
  "courtId": "77e09255-67b5-4cfa-80b5-62d9e8a59cb9",
  "startsAt": "2026-05-10T21:00:00Z",
  "endsAt": "2026-05-10T22:00:00Z",
  "timezone": "America/Sao_Paulo",
  "bookingDate": "2026-05-10",
  "startTime": "18:00:00",
  "endTime": "19:00:00",
  "status": "pending",
  "bookingType": "reservation",
  "note": "Prefer side court if possible"
}
```

Validation and behavior:

- `courtId`, `bookingDate`, `startTime`, and `endTime` are required.
- `note` is optional, max 1000 characters.
- Date/time values are venue-local.
- Duration must be a positive multiple of the venue `slotDurationMinutes`.
- `startTime` must align with the venue slot grid.
- Past dates or start times return `400`.
- Slot outside operating hours returns `400`.
- Slot overlapping recurring maintenance returns `409`.
- Slot overlapping another pending/confirmed booking returns `409`.
- Amount is calculated server-side from venue price and number of slots.
- Created bookings start as `status = "pending"`.

### List My Bookings

`GET /api/v1/bookings/me`

Auth: user JWT required.

Query params:

- `status`: optional, `upcoming` or `past`. Default: `upcoming`.
- `page`: optional integer, zero-based. Default: `0`.
- `page_size`: optional integer. Default: `20`, min effective value `1`, max effective value `100`.

Example:

```http
GET /api/v1/bookings/me?status=upcoming&page=0&page_size=20
```

Response `200`: Spring `Page<AppointmentDto>`.

```json
{
  "content": [
    {
      "id": "c4e0b5da-1f56-4a26-bcd5-355f010f892f",
      "status": "pending",
      "bookingType": "reservation",
      "startsAt": "2026-05-10T21:00:00Z",
      "endsAt": "2026-05-10T22:00:00Z",
      "timezone": "America/Sao_Paulo",
      "bookingDate": "2026-05-10",
      "startTime": "18:00:00",
      "endTime": "19:00:00",
      "amountCents": 12000,
      "paymentMethod": null,
      "venueId": "0b3fd1d3-7b96-49b5-8a54-01489441ef4d",
      "venueName": "Arena Squadra",
      "venueSlug": "arena-squadra",
      "venueAddress": "Av. Brasil, 100",
      "city": "Uberlandia",
      "stateCode": "MG",
      "courtId": "77e09255-67b5-4cfa-80b5-62d9e8a59cb9",
      "courtName": "Quadra 1",
      "courtSurfaceType": "synthetic",
      "note": "Prefer side court if possible"
    }
  ],
  "number": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

Behavior notes:

- `upcoming`: bookings whose `endsAt` is after now, ordered by `startsAt` ascending.
- `past`: bookings whose `endsAt` is before now, ordered by `startsAt` descending.
- Invalid `status` returns `400` with detail `status must be upcoming or past`.
- The serialized Spring `Page` may include extra fields such as `pageable` and `sort`; Flutter models can ignore unknown fields.

### Get Booking Detail

`GET /api/v1/bookings/{id}`

Auth: user JWT required.

Response `200`: `AppointmentDto`.

Behavior notes:

- Users can only fetch their own bookings.
- Unknown or not-owned booking returns `404`.

### Cancel Booking

`POST /api/v1/bookings/{id}/cancel`

Auth: user JWT required.

Request body is optional.

```json
{
  "reason": "Plans changed"
}
```

Response `200`:

```json
{
  "bookingId": "c4e0b5da-1f56-4a26-bcd5-355f010f892f",
  "cancelled": true,
  "refundPercent": 100,
  "refundAmountCents": 12000,
  "paymentImpact": "pix_full_refund",
  "message": "Reserva cancelada. Reembolso integral de 100%."
}
```

Validation and behavior:

- `reason`: optional, max 1000 characters.
- Already-cancelled booking returns `cancelled: true` with `paymentImpact: "already_cancelled"`.
- Booking whose start time has passed returns `cancelled: false` with `paymentImpact: "not_cancellable"`.
- Cancellation policy determines refund outcome.
- Current payment-related `paymentImpact` values include:
  - `already_cancelled`
  - `not_cancellable`
  - `pix_full_refund`
  - `pix_partial_refund`
  - `pix_no_refund`
  - `local_cancel_allowed`
  - `local_late_cancel`

## Provider Webhook Endpoint

### Payment Webhook

`POST /api/v1/payments-webhook`

Auth: provider token header, not mobile JWT.

Required header:

```http
asaas-access-token: <configured-token>
```

Request body: provider webhook JSON. The backend currently reads:

```json
{
  "event": "PAYMENT_RECEIVED",
  "payment": {
    "id": "pay_123",
    "externalReference": "c4e0b5da-1f56-4a26-bcd5-355f010f892f"
  }
}
```

Response `200`:

```json
{
  "received": true
}
```

Behavior notes:

- `PAYMENT_CREATED`: links provider payment id to a booking and sets `paymentMethod` to `pix`.
- `PAYMENT_RECEIVED`: links provider payment id, sets `paymentMethod` to `pix`, and confirms the booking.
- The mobile app should not call this endpoint.
- Payment/PIX creation and status polling APIs are not implemented in this backend phase.

## Admin Endpoints

These endpoints are listed for completeness. The normal player mobile app should not call them unless it includes an admin/owner experience and has an admin JWT.

### Create Venue

`POST /api/v1/admin/venues`

Auth: admin JWT required.

Request:

```json
{
  "name": "Arena Squadra",
  "slug": "arena-squadra",
  "description": "Quadras esportivas",
  "imageUrl": "https://example.com/venue.jpg",
  "address": "Av. Brasil, 100",
  "cityId": 3170206,
  "latitude": -18.9146,
  "longitude": -48.2754,
  "sports": ["soccer", "padel"],
  "amenities": {
    "parking": true
  },
  "priceCents": 12000,
  "slotDurationMinutes": 60,
  "courts": [
    {
      "name": "Quadra 1",
      "surfaceType": "synthetic",
      "indoor": false,
      "sortOrder": 1
    }
  ],
  "operatingHours": [
    {
      "dayOfWeek": 1,
      "openTime": "08:00:00",
      "closeTime": "22:00:00"
    }
  ],
  "cancelPolicy": {
    "pixFullRefundHours": 24,
    "pixPartialRefundHours": 12,
    "pixPartialRefundPercent": 50,
    "localCancelHours": 6,
    "noShowPixThreshold": 15
  }
}
```

Response `201`: `VenueDto`.

Validation:

- `name`, `slug`, `address`, `cityId`, `latitude`, `longitude`, and `operatingHours` are required.
- `slug` max length is 200.
- `latitude`: `-90` to `90`.
- `longitude`: `-180` to `180`.
- `priceCents`: zero or positive.
- `slotDurationMinutes`: positive.

### Update Venue

`PATCH /api/v1/admin/venues/{id}`

Auth: admin JWT required.

Request: any subset of:

```json
{
  "name": "Arena Squadra Updated",
  "slug": "arena-squadra-updated",
  "description": "Updated description",
  "imageUrl": "https://example.com/new.jpg",
  "address": "Av. Brasil, 200",
  "cityId": 3170206,
  "latitude": -18.9146,
  "longitude": -48.2754,
  "sports": ["soccer"],
  "amenities": {
    "parking": true
  },
  "priceCents": 13000,
  "slotDurationMinutes": 60,
  "active": true
}
```

Response `200`: `VenueDto`.

### Deactivate Venue

`DELETE /api/v1/admin/venues/{id}`

Auth: admin JWT required.

Response: `204` with empty body.

Behavior: soft deactivates the venue.

### Create Court

`POST /api/v1/admin/venues/{venueId}/courts`

Auth: admin JWT required.

Request:

```json
{
  "name": "Quadra 2",
  "surfaceType": "sand",
  "indoor": false,
  "sortOrder": 2
}
```

Response `201`: `CourtDto`.

Valid `surfaceType` values:

- `sand`
- `synthetic`
- `hard`
- `clay`
- `padel`
- `wood`
- `grass`

### Update Court

`PATCH /api/v1/admin/courts/{id}`

Auth: admin JWT required.

Request: any subset of:

```json
{
  "name": "Quadra 2",
  "surfaceType": "sand",
  "indoor": true,
  "sortOrder": 2,
  "active": true
}
```

Response `200`: `CourtDto`.

### Deactivate Court

`DELETE /api/v1/admin/courts/{id}`

Auth: admin JWT required.

Response: `204` with empty body.

Behavior: soft deactivates the court.

### List Operating Hours

`GET /api/v1/admin/venues/{venueId}/operating-hours`

Auth: admin JWT required.

Response `200`:

```json
[
  {
    "dayOfWeek": 1,
    "openTime": "08:00:00",
    "closeTime": "22:00:00"
  }
]
```

### Upsert Operating Hours

`PUT /api/v1/admin/venues/{venueId}/operating-hours/{dayOfWeek}`

Auth: admin JWT required.

Request:

```json
{
  "dayOfWeek": 1,
  "openTime": "08:00:00",
  "closeTime": "22:00:00"
}
```

Response `200`: `OperatingHoursDto`.

Validation:

- `dayOfWeek`: required, `0` to `6`.
- `openTime`: required.
- `closeTime`: required.

### Delete Operating Hours

`DELETE /api/v1/admin/venues/{venueId}/operating-hours/{dayOfWeek}`

Auth: admin JWT required.

Response: `204` with empty body.

### Get Admin Cancel Policy

`GET /api/v1/admin/venues/{venueId}/cancel-policy`

Auth: admin JWT required.

Response `200`: `CancelPolicyDto`.

### Upsert Cancel Policy

`PUT /api/v1/admin/venues/{venueId}/cancel-policy`

Auth: admin JWT required.

Request:

```json
{
  "pixFullRefundHours": 24,
  "pixPartialRefundHours": 12,
  "pixPartialRefundPercent": 50,
  "localCancelHours": 6,
  "noShowPixThreshold": 15
}
```

Response `200`: `CancelPolicyDto`.

Validation:

- All fields are required.
- Hour fields must be zero or positive.
- `pixPartialRefundPercent` must be from `0` to `100`.

## DTO Reference

### Auth DTOs

`OtpRequestDto`

```json
{
  "email": "user@example.com"
}
```

`OtpVerifyDto`

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

`RefreshTokenDto`

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

`AuthTokenDto`

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque-refresh-token>"
}
```

### User DTOs

`ProfileDto`

```json
{
  "id": "uuid",
  "name": "string",
  "email": "string",
  "phone": "string|null",
  "cpf": "string|null",
  "googleAuth": false
}
```

`UpdateProfileDto`

```json
{
  "name": "string|null",
  "phone": "string|null",
  "cpf": "string|null"
}
```

`DeleteAccountDto`

```json
{
  "code": "string"
}
```

### Venue DTOs

`CityDto`

```json
{
  "id": 3170206,
  "name": "Uberlandia",
  "stateCode": "MG",
  "latitude": -18.9146,
  "longitude": -48.2754
}
```

`CourtDto`

```json
{
  "id": "uuid",
  "venueId": "uuid",
  "name": "string",
  "surfaceType": "sand|synthetic|hard|clay|padel|wood|grass",
  "indoor": false,
  "sortOrder": 1,
  "active": true
}
```

`OperatingHoursDto`

```json
{
  "dayOfWeek": 0,
  "openTime": "08:00:00",
  "closeTime": "22:00:00"
}
```

`CancelPolicyDto`

```json
{
  "pixFullRefundHours": 24,
  "pixPartialRefundHours": 12,
  "pixPartialRefundPercent": 50,
  "localCancelHours": 6,
  "noShowPixThreshold": 15
}
```

`VenueResponseDto`

```json
{
  "id": "uuid",
  "slug": "string",
  "name": "string",
  "description": "string|null",
  "imageUrl": "string|null",
  "address": "string",
  "cityId": 3170206,
  "city": "string",
  "stateCode": "MG",
  "timezone": "America/Sao_Paulo",
  "sports": ["string"],
  "amenities": "json-string|null",
  "priceCents": 12000,
  "distanceKm": 3.42
}
```

`VenueDto` is the full detail object and includes all venue fields plus:

- `latitude`
- `longitude`
- `amenities` as an object
- `slotDurationMinutes`
- `active`
- `courts`
- `operatingHours`
- `cancelPolicy`

### Booking DTOs

`AvailableSlotDto`

```json
{
  "date": "2026-05-10",
  "timezone": "America/Sao_Paulo",
  "slotDurationMinutes": 60,
  "slots": ["08:00", "09:00"]
}
```

`CreateBookingDto`

```json
{
  "courtId": "uuid",
  "bookingDate": "2026-05-10",
  "startTime": "18:00:00",
  "endTime": "19:00:00",
  "note": "string|null"
}
```

`BookingDto`

```json
{
  "id": "uuid",
  "userId": "uuid",
  "courtId": "uuid",
  "startsAt": "2026-05-10T21:00:00Z",
  "endsAt": "2026-05-10T22:00:00Z",
  "timezone": "America/Sao_Paulo",
  "bookingDate": "2026-05-10",
  "startTime": "18:00:00",
  "endTime": "19:00:00",
  "status": "pending|confirmed|completed|cancelled",
  "bookingType": "reservation|block",
  "note": "string|null"
}
```

`AppointmentDto`

```json
{
  "id": "uuid",
  "status": "pending|confirmed|completed|cancelled",
  "bookingType": "reservation|block",
  "startsAt": "2026-05-10T21:00:00Z",
  "endsAt": "2026-05-10T22:00:00Z",
  "timezone": "America/Sao_Paulo",
  "bookingDate": "2026-05-10",
  "startTime": "18:00:00",
  "endTime": "19:00:00",
  "amountCents": 12000,
  "paymentMethod": "pix|null",
  "venueId": "uuid",
  "venueName": "string",
  "venueSlug": "string",
  "venueAddress": "string",
  "city": "string",
  "stateCode": "MG",
  "courtId": "uuid",
  "courtName": "string",
  "courtSurfaceType": "string",
  "note": "string|null"
}
```

`CancelBookingRequestDto`

```json
{
  "reason": "string|null"
}
```

`CancelBookingDto`

```json
{
  "bookingId": "uuid",
  "cancelled": true,
  "refundPercent": 100,
  "refundAmountCents": 12000,
  "paymentImpact": "pix_full_refund",
  "message": "string"
}
```

## Flutter Implementation Notes

- Configure the HTTP client to ignore unknown JSON fields, especially for Spring `Page` responses.
- Treat all nullable values as nullable in Dart models, especially `description`, `imageUrl`, `phone`, `paymentMethod`, `note`, `distanceKm`, and `cancelPolicy`.
- Convert `priceCents` and `amountCents` to display strings in the app UI only.
- Use the venue `timezone` and returned local `bookingDate`/`startTime` for display. Use `startsAt`/`endsAt` for absolute ordering and comparisons.
- For booking creation, send the exact selected available slot as `startTime` and calculate `endTime` by adding `slotDurationMinutes` locally.
- Do not call the payment webhook from the mobile app.
- The backend currently does not expose a payment creation endpoint. Bookings are created as `pending`; payment confirmation is handled externally through webhook integration.
- Public OpenAPI/Scalar documentation may be available at `/scalar` and raw docs at `/api-docs` when enabled.
