package com.saas.metering.controlplane.subscription.mapper;

import com.saas.metering.controlplane.plan.mapper.PlanMapper;
import com.saas.metering.controlplane.subscription.entity.Subscription;
import com.saas.metering.controlplane.subscription.dto.SubscriptionResponse;

public final class SubscriptionMapper {

    private SubscriptionMapper() {
    }

    public static SubscriptionResponse toResponse(Subscription s) {
        return new SubscriptionResponse(s.getId(), s.getTenant().getId(), s.getStatus(), s.getStartDate(),
                s.getEndDate(), PlanMapper.toResponse(s.getPlan()));
    }
}
