package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.policy.CircleSizingPolicyType;
import com.ajoledger.backend.system.Constants;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Builder(toBuilder = true)
public record CircleCreateRequestDTO(
    @NotBlank(message = "Name is required") String name,

    @NotBlank(message = "Description is required") String description,

    @NotNull(message = "Sizing policy type is required") CircleSizingPolicyType sizingPolicyType,

    @NotNull(message = "Max members is required")
    @Min(value = Constants.Circle.DEFAULT_MIN_MEMBERS, message = "Max members must be at least "
            + Constants.Circle.DEFAULT_MIN_MEMBERS) Integer maxMembers,

    /** OPEN_ENROLLMENT only. The creator's own floor. Leave unset to use the platform default. */
    @Min(value = Constants.Circle.DEFAULT_MIN_MEMBERS, message = "Min members must be at least "
            + Constants.Circle.DEFAULT_MIN_MEMBERS) Integer minMembers,

    /** Required for OPEN_ENROLLMENT, ignored for FIXED_SIZE. */
    Instant enrollmentDeadline,

    @NotNull(message = "Term is required")
    @Positive(message = "Term must be positive") Integer term,

    @NotNull(message = "Contribution amount is required")
    @Positive(message = "Contribution amount must be positive") BigDecimal contributionAmount,

    boolean gated,

    /** 0.0-1.0. Leave unset for no minimum. Doesn't apply to the creator's own auto-join. */
    @DecimalMin(value = "0.0", message = "Min reputation score must be at least 0.0")
    @DecimalMax(value = "1.0", message = "Min reputation score must be at most 1.0") Double minReputationScore
) {

    @AssertTrue(message = "Enrollment deadline is required for OPEN_ENROLLMENT circles")
    public boolean isEnrollmentDeadlinePresentWhenRequired() {
        return sizingPolicyType != CircleSizingPolicyType.OPEN_ENROLLMENT || enrollmentDeadline != null;
    }

    @AssertTrue(message = "Enrollment deadline must be at least "
            + Constants.Circle.MIN_ENROLLMENT_LEAD_DAYS + " day(s) in the future")
    public boolean isEnrollmentDeadlineFarEnoughOut() {
        return enrollmentDeadline == null
                || enrollmentDeadline.isAfter(Instant.now().plus(Duration.ofDays(Constants.Circle.MIN_ENROLLMENT_LEAD_DAYS)));
    }
}
