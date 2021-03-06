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

package org.seaborne.delta.server.local;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.jena.atlas.logging.FmtLog;
import org.seaborne.delta.DataSourceDescription;
import org.seaborne.delta.DeltaException;
import org.seaborne.delta.Id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@code PatchStore} is manager for a number of {@link PatchLog}s. One {@link PatchLog}
 * is the log for one data source; a {@code PatchStore} is a collection of such logs and
 * it is responsible for initialization, restoring {@code PatchLog}s on external storage
 * (files etc.)
 * <p>
 * There is normally one {@code PatchStore} for each patch storage technology but this is
 * not a requirement.
 */
public abstract class PatchStore {
    private static Logger LOG = LoggerFactory.getLogger(PatchStore.class); 
    
    // ---- PatchStore.Provider

    // -------- Global
    // DataRegistry?
    private static Map<Id, PatchLog> logs = new ConcurrentHashMap<>();

    /** Return the {@link PatchLog}, which must already exist. */ 
    public static PatchLog getLog(Id dsRef) { 
        return logs.get(dsRef);
    }
    
    public static boolean logExists(Id dsRef) {
        return logs.containsKey(dsRef);
    }
    
    /**
     * Release ("delete") the {@link PatchLog}. 
     * @param patchLog
     */
    public void release(PatchLog patchLog) {
        Id dsRef = patchLog.getLogId();
        if ( ! logExists(dsRef) ) {
            FmtLog.warn(LOG, "PatchLog not known to PatchStore: dsRef=%s", dsRef);
            return;
        }
        logs.remove(dsRef);
        patchLog.release();
    }
    
    /** Clear the internal mapping from Log (by Id) to its PatchLog. Used for testing. */
    public static void clearLogIdCache() {
        logs.clear();
    }
    
    // ---- /Global
    
    // -------- Instance
    private final PatchStoreProvider provider;
    
    protected PatchStore(PatchStoreProvider provider) {
        this.provider = provider;
    }
    
//    // Do not used - only for injecting classes for testing. 
//    protected PatchStore() {
//        this.provider = new PatchStoreProvider() {
//            @Override public PatchStore create()        { return PatchStore.this; }
//            @Override public String getProviderName()   { return "PatchStoreProviderUnknown"; }
//            @Override public String getShortName()      { return "unknown"; }
//        };
//    }

    /** Return the provider implementation. */ 
    public PatchStoreProvider getProvider() { 
        return provider;
    }
    
    /** Do the {@code PatchStore} logs exist across restarts? */ 
    public boolean isEphemeral() {
        return false;
    }
    
    /** Do the {@code PatchStore} have a file area? */ 
    public boolean hasFileArea() {
        return false;
    }
    
    /**
     * Boot style for this {@code PatchStore} provider.
     * Either it can provide the complete details to the server with {@link #initFromPersistent}
     * or the {@code LocalServer} will scan for DataSource directories.
     * <p>
     * If this call returns false, {@link #initFromPersistent} is not called.
     */
    public abstract boolean callInitFromPersistent(LocalServerConfig config);
    
    /** 
     * Scan for DataSources.
     * Only DataSources that are compatible with the PatchStore provider called
     * should be included in the returned list.  
     */
    public abstract List<DataSource> initFromPersistent(LocalServerConfig config);
    
    /** All the {@link DataSource} currently managed by the {@code PatchStore}. */
    public abstract List<DataSourceDescription> listDataSources();

    /**
     * Return a new {@link PatchLog}. Checking that there is no registered
     * {@link PatchLog} for this {@code dsRef} has already been done.
     * 
     * @param dsd
     * @param dsPath : Path to the DataSource area. PatchStore-impl can create local files. May be null.
     * @return PatchLog
     */
    protected abstract PatchLog create(DataSourceDescription dsd);

    /**
     * Delete a {@link PatchLog}.
     * @param patchLog
     */
    protected abstract void delete(PatchLog patchLog);

    /** Return a new {@link PatchLog}, which must already exist and be registered. */ 
    public PatchLog connectLog(DataSourceDescription dsd) {
        FmtLog.info(LOG, "Connect log: %s", dsd);
        PatchLog pLog = getLog(dsd.getId());
        if ( pLog == null )
            pLog = create$(dsd);
        return pLog;
    }
    
    /** Return a new {@link PatchLog}, which must not already exist. */ 
    public PatchLog createLog(DataSourceDescription dsd) {
        if ( getProvider() == null )
            FmtLog.info(LOG, "Create log[?]: %s", dsd);
        else  
            FmtLog.info(LOG, "Create log[%s]: %s", getProvider().getShortName(), dsd);
        Id dsRef = dsd.getId();
        if ( logExists(dsRef) )
            throw new DeltaException("Can't create - PatchLog exists");
        return create$(dsd);
    }
    
    // Create always. No PatchStore level checking.
    private PatchLog create$(DataSourceDescription dsd) {
        Id dsRef = dsd.getId();
        PatchLog patchLog = create(dsd);
        logs.put(dsRef, patchLog);
        return patchLog;
    }
}
