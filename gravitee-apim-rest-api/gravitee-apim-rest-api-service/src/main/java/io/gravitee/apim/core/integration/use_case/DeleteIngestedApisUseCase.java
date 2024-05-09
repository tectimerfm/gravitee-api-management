/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.apim.core.integration.use_case;

import io.gravitee.apim.core.UseCase;
import io.gravitee.apim.core.api.crud_service.ApiCrudService;
import io.gravitee.apim.core.api.domain_service.ApiIndexerDomainService;
import io.gravitee.apim.core.api.domain_service.ApiMetadataDomainService;
import io.gravitee.apim.core.api.model.Api;
import io.gravitee.apim.core.api.model.ApiFieldFilter;
import io.gravitee.apim.core.api.model.ApiSearchCriteria;
import io.gravitee.apim.core.api.query_service.ApiQueryService;
import io.gravitee.apim.core.audit.domain_service.AuditDomainService;
import io.gravitee.apim.core.audit.model.ApiAuditLogEntity;
import io.gravitee.apim.core.audit.model.AuditInfo;
import io.gravitee.apim.core.audit.model.event.ApiAuditEvent;
import io.gravitee.apim.core.documentation.domain_service.DeleteApiDocumentationDomainService;
import io.gravitee.apim.core.documentation.query_service.PageQueryService;
import io.gravitee.apim.core.membership.domain_service.DeleteMembershipDomainService;
import io.gravitee.apim.core.plan.domain_service.DeletePlanDomainService;
import io.gravitee.apim.core.plan.query_service.PlanQueryService;
import io.gravitee.apim.core.search.Indexer;
import io.gravitee.apim.core.subscription.domain_service.CloseSubscriptionDomainService;
import io.gravitee.apim.core.subscription.domain_service.DeleteSubscriptionDomainService;
import io.gravitee.apim.core.subscription.query_service.SubscriptionQueryService;
import io.gravitee.common.utils.TimeProvider;
import java.util.Collections;
import lombok.Builder;

@UseCase
public class DeleteIngestedApisUseCase {

    ApiQueryService apiQueryService;
    PlanQueryService planQueryService;
    SubscriptionQueryService subscriptionQueryService;
    CloseSubscriptionDomainService closeSubscriptionDomainService;
    DeleteSubscriptionDomainService deleteSubscriptionDomainService;
    DeletePlanDomainService deletePlanDomainService;
    PageQueryService pageQueryService;
    DeleteApiDocumentationDomainService deleteApiDocumentationDomainService;
    AuditDomainService auditDomainService;
    ApiMetadataDomainService apiMetadataDomainService;
    DeleteMembershipDomainService deleteMembershipDomainService;
    ApiCrudService apiCrudService;
    ApiIndexerDomainService apiIndexerDomainService;

    public DeleteIngestedApisUseCase(
        ApiQueryService apiQueryService,
        PlanQueryService planQueryService,
        SubscriptionQueryService subscriptionQueryService,
        CloseSubscriptionDomainService closeSubscriptionDomainService,
        DeleteSubscriptionDomainService deleteSubscriptionDomainService,
        DeletePlanDomainService deletePlanDomainService,
        PageQueryService pageQueryService,
        DeleteApiDocumentationDomainService deleteApiDocumentationDomainService,
        AuditDomainService auditDomainService,
        ApiMetadataDomainService apiMetadataDomainService,
        DeleteMembershipDomainService deleteMembershipDomainService,
        ApiCrudService apiCrudService,
        ApiIndexerDomainService apiIndexerDomainService
    ) {
        this.apiQueryService = apiQueryService;
        this.planQueryService = planQueryService;
        this.subscriptionQueryService = subscriptionQueryService;
        this.closeSubscriptionDomainService = closeSubscriptionDomainService;
        this.deleteSubscriptionDomainService = deleteSubscriptionDomainService;
        this.deletePlanDomainService = deletePlanDomainService;
        this.pageQueryService = pageQueryService;
        this.deleteApiDocumentationDomainService = deleteApiDocumentationDomainService;
        this.auditDomainService = auditDomainService;
        this.apiMetadataDomainService = apiMetadataDomainService;
        this.deleteMembershipDomainService = deleteMembershipDomainService;
        this.apiCrudService = apiCrudService;
        this.apiIndexerDomainService = apiIndexerDomainService;
    }

    public void execute(Input input) {
        var apisToDelete = apiQueryService
            .search(
                ApiSearchCriteria.builder().integrationId(input.integrationId).build(),
                null,
                ApiFieldFilter.builder().pictureExcluded(true).definitionExcluded(true).build()
            )
            .filter(api -> !api.getApiLifecycleState().equals(Api.ApiLifecycleState.PUBLISHED))
            .toList();

        apisToDelete.forEach(api -> delete(api, input.auditInfo));
    }

    @Builder
    public record Input(String integrationId, AuditInfo auditInfo) {}

    private void delete(Api api, AuditInfo auditInfo) {
        var apiId = api.getId();

        planQueryService
            .findAllByApiId(apiId)
            .forEach(plan -> {
                //Close active Subscriptions
                subscriptionQueryService
                    .findActiveSubscriptionsByPlan(plan.getId())
                    .forEach(activeSubscription -> closeSubscriptionDomainService.closeSubscription(activeSubscription.getId(), auditInfo));

                //Delete all subscriptions
                subscriptionQueryService
                    .findSubscriptionsByPlan(plan.getId())
                    .forEach(subscription -> deleteSubscriptionDomainService.delete(subscription, auditInfo));

                //Delete plans
                deletePlanDomainService.delete(plan, auditInfo);
            });

        //Delete pages
        pageQueryService.searchByApiId(apiId).forEach(page -> deleteApiDocumentationDomainService.delete(api, page.getId(), auditInfo));

        //Delete API Index
        apiIndexerDomainService.delete(new Indexer.IndexationContext(auditInfo.organizationId(), auditInfo.environmentId()), api);

        //Delete membership
        deleteMembershipDomainService.deleteApiMemberships(apiId, auditInfo);

        //Delete metadata
        apiMetadataDomainService.deleteApiMetadata(apiId, auditInfo);

        //Delete API
        apiCrudService.delete(api.getId());

        //Audit
        createAuditLog(auditInfo, api);
    }

    private void createAuditLog(AuditInfo auditInfo, Api api) {
        auditDomainService.createApiAuditLog(
            ApiAuditLogEntity
                .builder()
                .organizationId(auditInfo.organizationId())
                .environmentId(auditInfo.environmentId())
                .apiId(api.getId())
                .event(ApiAuditEvent.API_DELETED)
                .actor(auditInfo.actor())
                .oldValue(api)
                .createdAt(TimeProvider.now())
                .properties(Collections.emptyMap())
                .build()
        );
    }
}
