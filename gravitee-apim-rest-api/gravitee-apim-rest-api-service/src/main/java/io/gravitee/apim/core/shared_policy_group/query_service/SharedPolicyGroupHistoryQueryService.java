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
package io.gravitee.apim.core.shared_policy_group.query_service;

import io.gravitee.apim.core.shared_policy_group.model.SharedPolicyGroup;
import io.gravitee.common.data.domain.Page;
import io.gravitee.rest.api.model.common.Pageable;
import io.gravitee.rest.api.model.common.Sortable;
import java.util.Optional;
import java.util.stream.Stream;

public interface SharedPolicyGroupHistoryQueryService {
    Stream<SharedPolicyGroup> streamLatestBySharedPolicyGroupId(String environmentId);

    Page<SharedPolicyGroup> search(String environmentId, String sharedPolicyGroupId, Pageable pageable, Sortable sortable);

    Optional<SharedPolicyGroup> getLatestBySharedPolicyGroupId(String environmentId, String sharedPolicyGroupId);
}
