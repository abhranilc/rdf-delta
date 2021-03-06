/*
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

package test;

import org.junit.Test;
import org.seaborne.delta.server.local.DPS;
import org.seaborne.delta.server.local.LocalServerConfig;
import org.seaborne.delta.server.local.PatchStore;
import org.seaborne.delta.server.local.PatchStoreMgr;
import org.seaborne.delta.server.patchstores.AbstractTestPatchStore;

public class TestPatchStoreZk extends AbstractTestPatchStore {
    //extends TestPatchStoreMem {

    // Really all covered by AbstractTestPatchLog
    
    @Override
    protected PatchStore patchStore() {
        System.err.println("Fixup needed: TestPatchStoreZk");
        LocalServerConfig config = null;
        return PatchStoreMgr.getPatchStoreProvider(DPS.PatchStoreMemProvider).create(config);
        //return PatchStoreZk.create(ZkT.curator());
    }
    
    @Override
    @Test public void recovery1() {}

}
