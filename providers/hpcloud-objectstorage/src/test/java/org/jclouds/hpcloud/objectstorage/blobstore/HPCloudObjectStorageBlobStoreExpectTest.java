/**
 * Licensed to jclouds, Inc. (jclouds) under one or more
 * contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  jclouds licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jclouds.hpcloud.objectstorage.blobstore;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.Map;
import java.util.Set;

import org.jclouds.blobstore.BlobStore;
import org.jclouds.domain.Location;
import org.jclouds.hpcloud.objectstorage.internal.BaseHPCloudObjectStorageBlobStoreExpectTest;
import org.jclouds.http.HttpRequest;
import org.jclouds.http.HttpResponse;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

@Test(groups = "unit", testName = "HPCloudObjectStorageBlobStoreExpectTest")
public class HPCloudObjectStorageBlobStoreExpectTest extends BaseHPCloudObjectStorageBlobStoreExpectTest {


    public void testListObjectsWhenResponseIs2xx() throws Exception {
        Map<HttpRequest, HttpResponse> requestResponseMap = ImmutableMap.<HttpRequest, HttpResponse> builder().put(
                keystoneAuthWithAccessKeyAndSecretKey, responseWithKeystoneAccess).build();

        BlobStore clientWhenLocationsExist = requestsSendResponses(requestResponseMap);

        Set<? extends Location> locations = clientWhenLocationsExist.listAssignableLocations();
        assertNotNull(locations);
        assertEquals(locations.size(), 1);
        assertEquals(locations.iterator().next().getId(), "region-a.geo-1");
    }
}
