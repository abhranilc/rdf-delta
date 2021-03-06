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

package org.seaborne.delta.server.local.patchstores;

import org.seaborne.delta.DeltaConst;
import org.seaborne.delta.Id;
import org.seaborne.delta.server.local.PatchStore;

/** State control for a {@link PatchStore} */
public interface PatchLogIndex {
    /** Return whether the log is empty. */ 
    public boolean isEmpty();
    
    /** 
     * Return the next version number.
     * Returns the same value until {@link #save(long, Id, Id)} is called.  
     */
    public long nextVersion();
    
    /** Save the new head of log information. */
    public void save(long version, Id patch, Id prev);
    
    /**
     * Get the earliest version in the log.
     * Returns {@link DeltaConst#VERSION_INIT} when the log is empty.
     * Returns {@link DeltaConst#VERSION_UNSET} when the log has not been initialized yet.
     */
    public long getEarliestVersion();

    /**
     * Get the {@code Id} of the earliest entry in the log or null if the log is empty.
     */
    public Id getEarliestId();
    
    /**
     * Get the {@code version} of the current head of the log.
     * Returns {@link DeltaConst#VERSION_INIT} when the log is empty.
     */
    
    public long getCurrentVersion();
    
    /** Get the {@code Id} of the current head of the log, or null if there isn't one. */
    public Id getCurrentId();

    /** Get the {@code Id} of the previous entry, or null if there isn't one. */
    public Id getPreviousId();

    /** Map version number to the {@link Id} for the patch it refers to */ 
    public Id mapVersionToId(long version); 
}
