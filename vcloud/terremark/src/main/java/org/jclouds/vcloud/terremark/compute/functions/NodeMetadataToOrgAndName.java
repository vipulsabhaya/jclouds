/**
 *
 * Copyright (C) 2010 Cloud Conscious, LLC. <info@cloudconscious.com>
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

package org.jclouds.vcloud.terremark.compute.functions;

import java.util.Map;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.reference.ComputeServiceConstants;
import org.jclouds.logging.Logger;
import org.jclouds.vcloud.VCloudClient;
import org.jclouds.vcloud.domain.Organization;
import org.jclouds.vcloud.endpoints.VDC;
import org.jclouds.vcloud.terremark.compute.domain.OrgAndName;

import com.google.common.base.Function;
import com.google.common.base.Supplier;

/**
 * 
 * @author Adrian Cole
 * 
 */
@Singleton
public class NodeMetadataToOrgAndName implements Function<NodeMetadata, OrgAndName> {

   @Resource
   @Named(ComputeServiceConstants.COMPUTE_LOGGER)
   protected Logger logger = Logger.NULL;

   final Supplier<Map<String, String>> vdcToOrg;

   private final VCloudClient client;

   @Inject
   NodeMetadataToOrgAndName(VCloudClient client, @VDC Supplier<Map<String, String>> vdcToOrg) {
      this.vdcToOrg = vdcToOrg;
      this.client = client;
   }

   @Override
   public OrgAndName apply(NodeMetadata from) {
      if (from.getTag() != null) {
         Organization org = client.findOrganizationNamed(vdcToOrg.get().get(from.getLocation().getId()));
         if (org == null) {
            logger.warn("did not find an association for vdc %s in %s", from.getLocation().getId(), vdcToOrg);
         } else {
            return new OrgAndName(org.getId(), from.getTag());
         }
      }
      return null;
   }
}