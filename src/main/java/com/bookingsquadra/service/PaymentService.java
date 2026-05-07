package com.bookingsquadra.service;

import com.bookingsquadra.client.asaas.AsaasClient;
import com.bookingsquadra.client.asaas.AsaasCustomerRequest;
import com.bookingsquadra.client.asaas.AsaasCustomerResponse;
import com.bookingsquadra.client.asaas.AsaasPaymentRequest;
import com.bookingsquadra.client.asaas.AsaasPaymentResponse;
import com.bookingsquadra.client.asaas.AsaasPixCodeResponse;
import com.bookingsquadra.client.asaas.AsaasRefundRequest;
import com.bookingsquadra.client.asaas.AsaasSplitRefund;
import com.bookingsquadra.client.asaas.AsaasSplitRequest;
import com.bookingsquadra.client.asaas.AsaasSplitResponse;
import com.bookingsquadra.config.AsaasProperties;
import com.bookingsquadra.dto.CheckoutRequestDto;
import com.bookingsquadra.dto.CheckoutResponseDto;
import com.bookingsquadra.dto.PixCodeDto;
import com.bookingsquadra.dto.RefundResponseDto;
import com.bookingsquadra.entity.Booking;
import com.bookingsquadra.entity.CancelPolicy;
import com.bookingsquadra.entity.Court;
import com.bookingsquadra.entity.Payment;
import com.bookingsquadra.entity.User;
import com.bookingsquadra.entity.Venue;
import com.bookingsquadra.repository.BookingRepository;
import com.bookingsquadra.repository.CancelPolicyRepository;
import com.bookingsquadra.repository.CourtRepository;
import com.bookingsquadra.repository.PaymentRepository;
import com.bookingsquadra.repository.UserRepository;
import com.bookingsquadra.repository.VenueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String BOOKING_STATUS_PENDING = "pending";
    private static final String BOOKING_STATUS_CANCELLED = "cancelled";
    private static final String PAYMENT_METHOD_PIX = "pix";
    private static final int MAX_PARTIAL_REFUND_PERCENT = 90;
    private static final ZoneId ASAAS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter ASAAS_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration PIX_REFRESH_MARGIN = Duration.ofMinutes(5);

    private final AsaasClient asaasClient;
    private final AsaasProperties asaasProperties;
    private final BookingRepository bookingRepository;
    private final CancelPolicyRepository cancelPolicyRepository;
    private final CourtRepository courtRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final VenueRepository venueRepository;

    public PaymentService(
            AsaasClient asaasClient,
            AsaasProperties asaasProperties,
            BookingRepository bookingRepository,
            CancelPolicyRepository cancelPolicyRepository,
            CourtRepository courtRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            UserService userService,
            VenueRepository venueRepository
    ) {
        this.asaasClient = asaasClient;
        this.asaasProperties = asaasProperties;
        this.bookingRepository = bookingRepository;
        this.cancelPolicyRepository = cancelPolicyRepository;
        this.courtRepository = courtRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.venueRepository = venueRepository;
    }

    @Transactional
    public CheckoutResponseDto checkout(CheckoutRequestDto request) {
        User user = userService.findCurrentOrThrow();
        Booking booking = bookingRepository.findByIdAndUserId(request.bookingId(), user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!BOOKING_STATUS_PENDING.equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking is not pending payment");
        }

        Payment existing = paymentRepository.findByBookingId(booking.getId()).orElse(null);
        if (existing != null && Payment.STATUS_PENDING.equals(existing.getStatus())) {
            return toCheckoutResponse(booking, existing);
        }
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment for this booking already exists");
        }

        Court court = courtRepository.findById(booking.getCourtId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
        Venue venue = venueRepository.findById(court.getVenueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        if (venue.getAsaasWalletId() == null || venue.getAsaasWalletId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Venue is not configured to accept payments yet");
        }

        ensureUserBillingDetails(user, request);
        ensureAsaasCustomer(user);

        AsaasPaymentRequest paymentRequest = new AsaasPaymentRequest(
                user.getAsaasCustomerId(),
                Payment.BILLING_TYPE_PIX,
                fromCents(booking.getAmountCents()),
                LocalDate.now(ASAAS_ZONE).plusDays(asaasProperties.dueDaysOrDefault()),
                booking.getId().toString(),
                List.of(new AsaasSplitRequest(
                        venue.getAsaasWalletId(),
                        BigDecimal.valueOf(100),
                        null
                ))
        );

        String idempotencyKey = buildCheckoutIdempotencyKey(booking.getId(), Payment.BILLING_TYPE_PIX);
        AsaasPaymentResponse response = asaasClient.createPayment(paymentRequest, idempotencyKey);

        Payment concurrent = paymentRepository.findByAsaasPaymentId(response.id()).orElse(null);
        if (concurrent != null) {
            return toCheckoutResponse(booking, concurrent);
        }

        AsaasSplitResponse split = firstSplitOrThrow(response);

        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusMinutes(asaasProperties.paymentWindowMinutesOrDefault());
        booking.setExpiresAt(expiresAt);

        Payment payment = Payment.builder()
                .bookingId(booking.getId())
                .asaasPaymentId(response.id())
                .asaasCustomerId(user.getAsaasCustomerId())
                .asaasSplitId(split.id())
                .walletId(split.walletId())
                .billingType(Payment.BILLING_TYPE_PIX)
                .amountCents(booking.getAmountCents())
                .status(Payment.STATUS_PENDING)
                .invoiceUrl(response.invoiceUrl())
                .dueDate(parseDueDate(response.dueDate()))
                .expiresAt(expiresAt)
                .build();

        Payment saved = paymentRepository.save(payment);

        booking.setPaymentMethod(PAYMENT_METHOD_PIX);
        bookingRepository.save(booking);

        return toCheckoutResponse(booking, saved);
    }

    @Transactional
    public PixCodeDto getPixCode(UUID bookingId) {
        User user = userService.findCurrentOrThrow();
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        Payment payment = paymentRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment not found for this booking"));

        if (!Payment.STATUS_PENDING.equals(payment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "PIX is not available for this payment");
        }

        if (isPixCacheValid(payment)) {
            return new PixCodeDto(payment.getPixQrImage(), payment.getPixPayload(), payment.getPixExpiresAt());
        }

        AsaasPixCodeResponse response = asaasClient.getPixQrCode(payment.getAsaasPaymentId());
        if (response == null || Boolean.FALSE.equals(response.success())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "API did not return a PIX code");
        }

        OffsetDateTime pixExpiresAt = parsePixExpirationDate(response.expirationDate());
        payment.setPixPayload(response.payload());
        payment.setPixQrImage(response.encodedImage());
        payment.setPixExpiresAt(pixExpiresAt);
        paymentRepository.save(payment);

        return new PixCodeDto(payment.getPixQrImage(), payment.getPixPayload(), payment.getPixExpiresAt());
    }

    /**
     * Refunds the booking's payment using the cancel-policy percent.
     * Returns null when the booking has no completed PIX payment to refund (caller can ignore).
     */
    @Transactional
    public RefundResponseDto refundForCancellation(Booking booking, int refundPercent) {
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
        if (payment == null || !Payment.STATUS_RECEIVED.equals(payment.getStatus())) {
            return null;
        }
        if (refundPercent <= 0) {
            return new RefundResponseDto(
                    booking.getId(),
                    payment.getAsaasPaymentId(),
                    0,
                    0,
                    0,
                    0,
                    payment.getStatus(),
                    "No refund applicable for this cancellation."
            );
        }
        if (refundPercent < 100 && refundPercent > MAX_PARTIAL_REFUND_PERCENT) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Partial refunds cannot exceed " + MAX_PARTIAL_REFUND_PERCENT + "%");
        }

        int amountCents = payment.getAmountCents();
        int grossCents = Math.floorDiv(amountCents * refundPercent, 100);
        int feeCents = (refundPercent == 100)
                ? Math.floorDiv(amountCents * asaasProperties.fullRefundFeePercentOrDefault(), 100)
                : 0;
        int netCents = grossCents - feeCents;
        if (netCents <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Refund net amount is zero after fees");
        }

        BigDecimal refundValue = fromCents(netCents);
        AsaasRefundRequest refundRequest = new AsaasRefundRequest(
                null,
                buildRefundDescription(refundPercent),
                List.of(new AsaasSplitRefund(payment.getAsaasSplitId(), refundValue))
        );

        AsaasPaymentResponse response = asaasClient.refundPayment(payment.getAsaasPaymentId(), refundRequest);
        log.info("Asaas refund requested for payment {} status={} value={}",
                payment.getAsaasPaymentId(), response.status(), refundValue);

        payment.setStatus(Payment.STATUS_REFUND_REQUESTED);
        payment.setRefundedAt(OffsetDateTime.now(ZoneOffset.UTC));
        payment.setRefundAmountCents(netCents);
        paymentRepository.save(payment);

        return new RefundResponseDto(
                booking.getId(),
                payment.getAsaasPaymentId(),
                refundPercent,
                grossCents,
                feeCents,
                netCents,
                payment.getStatus(),
                "Refund requested. Final settlement will be confirmed by webhook."
        );
    }

    @Transactional
    public RefundResponseDto refundCurrentUserBooking(UUID bookingId) {
        User user = userService.findCurrentOrThrow();
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!BOOKING_STATUS_CANCELLED.equals(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Refund is only available after cancellation");
        }
        int refundPercent = computeRefundPercent(booking);
        RefundResponseDto outcome = refundForCancellation(booking, refundPercent);
        if (outcome == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Booking has no completed payment to refund");
        }
        return outcome;
    }

    /**
     * Cancels an Asaas payment that is still pending (not yet paid).
     * Used when the user cancels a booking before paying.
     */
    @Transactional
    public void cancelPendingPayment(Booking booking) {
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
        if (payment == null || !Payment.STATUS_PENDING.equals(payment.getStatus())) {
            return;
        }
        try {
            asaasClient.deletePayment(payment.getAsaasPaymentId());
        } catch (ResponseStatusException e) {
            log.warn("Failed to delete pending Asaas payment {}: {}",
                    payment.getAsaasPaymentId(), e.getMessage());
        }
        payment.setStatus(Payment.STATUS_DELETED);
        paymentRepository.save(payment);
    }

    @Transactional
    public int cancelExpiredBookings() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Booking> expired = bookingRepository
                .findByStatusAndExpiresAtBefore(BOOKING_STATUS_PENDING, now);
        for (Booking booking : expired) {
            Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
            if (payment != null && Payment.STATUS_PENDING.equals(payment.getStatus())) {
                try {
                    asaasClient.deletePayment(payment.getAsaasPaymentId());
                } catch (ResponseStatusException e) {
                    log.warn("Auto-expire: Asaas delete failed for {}: {}",
                            payment.getAsaasPaymentId(), e.getMessage());
                }
                payment.setStatus(Payment.STATUS_DELETED);
                paymentRepository.save(payment);
            }
            booking.setStatus(BOOKING_STATUS_CANCELLED);
            booking.setCancelledAt(now);
            booking.setCancelReason("payment_window_expired");
            bookingRepository.save(booking);
        }
        return expired.size();
    }

    private int computeRefundPercent(Booking booking) {
        Court court = courtRepository.findById(booking.getCourtId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
        Venue venue = venueRepository.findById(court.getVenueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        CancelPolicy policy = cancelPolicyRepository.findByVenueId(venue.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Cancel policy not found"));
        OffsetDateTime reference = booking.getCancelledAt() != null
                ? booking.getCancelledAt()
                : OffsetDateTime.now(ZoneOffset.UTC);
        long hoursUntilStart = Duration.between(reference, booking.getStartsAt()).toHours();
        if (hoursUntilStart >= policy.getPixFullRefundHours()) {
            return 100;
        }
        if (hoursUntilStart >= policy.getPixPartialRefundHours()) {
            return policy.getPixPartialRefundPercent();
        }
        return 0;
    }

    private void ensureUserBillingDetails(User user, CheckoutRequestDto request) {
        if (request.name() != null && !request.name().isBlank() && (user.getName() == null || user.getName().isBlank())) {
            user.setName(request.name().trim());
        }
        if (user.getCpf() == null || user.getCpf().isBlank()) {
            String cpfRaw = request.cpfCnpj();
            if (cpfRaw == null || cpfRaw.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "CPF/CNPJ is required for the first checkout");
            }
            user.setCpf(cpfRaw.replaceAll("\\D", ""));
        }
        if (user.getName() == null || user.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Name is required to register a customer");
        }
    }

    private void ensureAsaasCustomer(User user) {
        if (user.getAsaasCustomerId() != null && !user.getAsaasCustomerId().isBlank()) {
            return;
        }
        AsaasCustomerResponse customer = asaasClient.createCustomer(new AsaasCustomerRequest(
                user.getName(),
                user.getCpf(),
                user.getId().toString()
        ));
        if (customer == null || customer.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "API did not return a customer id");
        }
        user.setAsaasCustomerId(customer.id());
        userRepository.save(user);
    }

    private boolean isPixCacheValid(Payment payment) {
        if (payment.getPixPayload() == null || payment.getPixPayload().isBlank()) {
            return false;
        }
        OffsetDateTime expiresAt = payment.getPixExpiresAt();
        if (expiresAt == null) {
            return false;
        }
        return OffsetDateTime.now(ZoneOffset.UTC).plus(PIX_REFRESH_MARGIN).isBefore(expiresAt);
    }

    private static AsaasSplitResponse firstSplitOrThrow(AsaasPaymentResponse response) {
        if (response == null || response.split() == null || response.split().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Asaas payment response did not contain a split");
        }
        AsaasSplitResponse split = response.split().get(0);
        if (split.id() == null || split.walletId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Asaas split response missing id/walletId");
        }
        return split;
    }

    private static CheckoutResponseDto toCheckoutResponse(Booking booking, Payment payment) {
        return new CheckoutResponseDto(
                booking.getId(),
                payment.getAsaasPaymentId(),
                payment.getStatus(),
                payment.getAmountCents(),
                payment.getInvoiceUrl(),
                payment.getExpiresAt()
        );
    }

    private static String buildCheckoutIdempotencyKey(UUID bookingId, String billingType) {
        return "checkout_" + bookingId + "_" + billingType + "_v1";
    }

    private static String buildRefundDescription(int refundPercent) {
        return refundPercent == 100
                ? "Full refund (10% retention applied)"
                : "Partial refund (" + refundPercent + "%)";
    }

    private static BigDecimal fromCents(int cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate parseDueDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now(ASAAS_ZONE);
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            return LocalDate.now(ASAAS_ZONE);
        }
    }

    private static OffsetDateTime parsePixExpirationDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDateTime local = LocalDateTime.parse(value, ASAAS_TIMESTAMP);
            return local.atZone(ASAAS_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        } catch (RuntimeException e) {
            log.warn("Failed to parse Asaas PIX expirationDate '{}': {}", value, e.toString());
            return null;
        }
    }
}
