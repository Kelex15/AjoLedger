package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.policy.CircleSizingPolicyType;
import com.ajoledger.backend.system.Constants;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class CircleCreateRequestDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Sizing policy type is required")
    private CircleSizingPolicyType sizingPolicyType;

    @NotNull(message = "Max members is required")
    @Positive(message = "Max members must be positive")
    private Integer maxMembers;

    /** OPEN_ENROLLMENT only. The creator's own floor. Leave unset to use the platform default. */
    @Positive(message = "Min members must be positive")
    private Integer minMembers;

    /** Required for OPEN_ENROLLMENT, ignored for FIXED_SIZE. */
    private Instant enrollmentDeadline;

    @NotNull(message = "Term is required")
    @Positive(message = "Term must be positive")
    private Integer term;

    @NotNull(message = "Contribution amount is required")
    @Positive(message = "Contribution amount must be positive")
    private BigDecimal contributionAmount;

    private boolean gated;

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
