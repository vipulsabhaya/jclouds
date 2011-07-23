/**
 *
 * Copyright (C) 2011 Cloud Conscious, LLC. <info@cloudconscious.com>
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.jclouds.trmk.vcloud_0_8.config;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jclouds.trmk.vcloud_0_8.domain.Org;
import org.jclouds.trmk.vcloud_0_8.domain.ReferenceType;

import com.google.common.base.Function;
import com.google.common.base.Supplier;

/**
 * 
 * @author Adrian Cole
 */
@Singleton
public class DefaultTasksListForOrg implements Function<ReferenceType, ReferenceType> {
   private final Supplier<Map<String, ? extends Org>> nameToOrg;

   @Inject
   public DefaultTasksListForOrg(Supplier<Map<String, ? extends Org>> nameToOrg) {
      this.nameToOrg = checkNotNull(nameToOrg, "nameToOrg");
   }

   @Override
   public ReferenceType apply(ReferenceType defaultOrg) {
      org.jclouds.trmk.vcloud_0_8.domain.Org org = nameToOrg.get().get(defaultOrg.getName());
      checkState(org != null, "could not retrieve Org at %s", defaultOrg);
      return org.getTasksList();
   }

}