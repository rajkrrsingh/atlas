/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.repository.store.graph.v1;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.atlas.AtlasErrorCode;
import org.apache.atlas.AtlasException;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.instance.AtlasEntityHeader;
import org.apache.atlas.model.instance.EntityMutationResponse;
import org.apache.atlas.model.instance.EntityMutations.EntityOperation;
import org.apache.atlas.listener.EntityChangeListener;
import org.apache.atlas.repository.converters.AtlasInstanceConverter;
import org.apache.atlas.typesystem.ITypedReferenceableInstance;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.apache.atlas.model.instance.EntityMutations.EntityOperation.CREATE;
import static org.apache.atlas.model.instance.EntityMutations.EntityOperation.DELETE;
import static org.apache.atlas.model.instance.EntityMutations.EntityOperation.PARTIAL_UPDATE;
import static org.apache.atlas.model.instance.EntityMutations.EntityOperation.UPDATE;


@Singleton
public class AtlasEntityChangeNotifier {
    private static final Logger LOG = LoggerFactory.getLogger(AtlasEntityChangeNotifier.class);

    private final Set<EntityChangeListener> entityChangeListeners;
    private final AtlasInstanceConverter    instanceConverter;

    @Inject
    public AtlasEntityChangeNotifier(Set<EntityChangeListener> entityChangeListeners,
                                     AtlasInstanceConverter    instanceConverter) {
        this.entityChangeListeners = entityChangeListeners;
        this.instanceConverter     = instanceConverter;
    }

    public void onEntitiesMutated(EntityMutationResponse entityMutationResponse) throws AtlasBaseException {
        if (CollectionUtils.isEmpty(entityChangeListeners) || instanceConverter == null) {
            return;
        }

        List<AtlasEntityHeader> createdEntities          = entityMutationResponse.getCreatedEntities();
        List<AtlasEntityHeader> updatedEntities          = entityMutationResponse.getUpdatedEntities();
        List<AtlasEntityHeader> partiallyUpdatedEntities = entityMutationResponse.getPartialUpdatedEntities();
        List<AtlasEntityHeader> deletedEntities          = entityMutationResponse.getDeletedEntities();

        if (CollectionUtils.isNotEmpty(createdEntities)) {
            List<ITypedReferenceableInstance> typedRefInst = toITypedReferenceable(createdEntities);

            notifyListeners(typedRefInst, EntityOperation.CREATE);
        }

        if (CollectionUtils.isNotEmpty(updatedEntities)) {
            List<ITypedReferenceableInstance> typedRefInst = toITypedReferenceable(updatedEntities);

            notifyListeners(typedRefInst, EntityOperation.UPDATE);
        }

        if (CollectionUtils.isNotEmpty(partiallyUpdatedEntities)) {
            List<ITypedReferenceableInstance> typedRefInst = toITypedReferenceable(partiallyUpdatedEntities);

            notifyListeners(typedRefInst, EntityOperation.PARTIAL_UPDATE);
        }

        if (CollectionUtils.isNotEmpty(deletedEntities)) {
            List<ITypedReferenceableInstance> typedRefInst = toITypedReferenceable(deletedEntities);

            notifyListeners(typedRefInst, EntityOperation.DELETE);
        }
    }

    private void notifyListeners(List<ITypedReferenceableInstance> typedRefInsts, EntityOperation operation) throws AtlasBaseException {
        for (EntityChangeListener listener : entityChangeListeners) {
            try {
                switch (operation) {
                    case CREATE:
                        listener.onEntitiesAdded(typedRefInsts);
                        break;
                    case UPDATE:
                    case PARTIAL_UPDATE:
                        listener.onEntitiesUpdated(typedRefInsts);
                        break;
                    case DELETE:
                        listener.onEntitiesDeleted(typedRefInsts);
                        break;
                }
            } catch (AtlasException e) {
                throw new AtlasBaseException(AtlasErrorCode.NOTIFICATION_FAILED, e, operation.toString());
            }
        }
    }

    private List<ITypedReferenceableInstance> toITypedReferenceable(List<AtlasEntityHeader> entityHeaders) throws AtlasBaseException {
        List<ITypedReferenceableInstance> ret = new ArrayList<>(entityHeaders.size());

        for (AtlasEntityHeader entityHeader : entityHeaders) {
            ret.add(instanceConverter.getITypedReferenceable(entityHeader.getGuid()));
        }

        return ret;
    }
}