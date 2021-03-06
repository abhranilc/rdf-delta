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

package org.seaborne.delta.server.local.patchstores.mem;

import java.util.HashMap;
import java.util.Map;

import org.seaborne.delta.DeltaConst;
import org.seaborne.delta.Id;
import org.seaborne.delta.server.local.PatchStore;
import org.seaborne.delta.server.local.patchstores.PatchLogIndex;

/** State control for a {@link PatchStore} */
public class PatchLogIndexMem implements PatchLogIndex {
    private Map<Long, Id> versions = new HashMap<>(); 
    
    private long earliestVersion = DeltaConst.VERSION_UNSET;
    private Id earliestId = null;
    
    private long version = DeltaConst.VERSION_UNSET;
    private Id current = null;
    private Id prev = null;
    
    public PatchLogIndexMem() {
        version = DeltaConst.VERSION_INIT;
        current = null;
        prev = null;
    }
    
    @Override
    public boolean isEmpty() {
        //return version == DeltaConst.VERSION_UNSET || DeltaConst.VERSION_INIT;
        return current == null;
    }

    @Override
    public long nextVersion() {
        return version+1;
    }
    
    @Override
    public void save(long version, Id patch, Id prev) {
        this.version = version;
        this.current = patch;
        this.prev = prev;
        if ( earliestId == null ) {
            earliestVersion = version;
            earliestId = patch;
        }
        
        versions.put(version, patch);
    }
    
    @Override
    public long getCurrentVersion() {
        return version;
    }

    @Override
    public Id getCurrentId() {
        return current;
    }

    @Override
    public Id getPreviousId() {
        return prev;
    }

    @Override
    public Id mapVersionToId(long ver) {
        return versions.get(ver);
    }

    @Override
    public long getEarliestVersion() {
        return earliestVersion;
    }

    @Override
    public Id getEarliestId() {
        return earliestId;
    }

}
