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

package org.seaborne.delta.link;

import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.seaborne.patch.RDFChanges;
import org.seaborne.patch.RDFPatch ;

/** Interface to the server for the operations.
 * A server is any engine that that provides the Delat operations
 * and it may be local or remote.
 * <p> The client connection for operations on a specific dataset is {@code DeltaConnection}.
 */
public interface DeltaLink {
    public RegToken register(Id clientId);
    public RegToken register(String name);
    
//    public RegToken register(String name, Id id);
//    
    public void deregister(RegToken token);
    public void deregister(Id clientId);

    /** Create a new dataset and return the unique Id for it */  
    public Id newDataset(JsonObject description) ;
    
    /** Make a dataset unavailable.
     *  Actual deleting of resources depends on the implementation.
     *  A server will tend not to really delete a persistent database.
     */
    public Id removeDataset(Id dsRef);

    /** Return an array of ids of datasets */
    public JsonArray listDatasets() ;
    
    /** Return details of a dataset (or null if not registered) */
    public JsonObject getDatasetDescription(Id dsRef) ;

    /** Send patch, return new version */
    public int sendPatch(Id dsRef, RDFPatch patch);
    
    public int getCurrentVersion(Id dsRef);

    /** Retrieve a patch by datasource and version, and write it to the {@code OutputStream}. */ 
    public RDFPatch fetch(Id dsRef, int version);

    /** Retrieve a patch and write it to the {@code OutputStream}. */ 
    public RDFPatch fetch(Id dsRef, Id patchId);

    /** Create an {@link RDFChanges} for this link. */
    public RDFChanges createRDFChanges(Id dsRef);
    
    /** Check whether a client id is registered for this link. */
    public boolean isRegistered(Id id);
    
    /** Check whether a {@code RegToken} is active. */
    public boolean isRegistered(RegToken regToken);
    
//    public void existingDataset() {} 
//    
//    public Id newDataset() { return null ; }
//    public void deleteDataset(Id uuid) { }
//
//    // Graph-only system
//    
//    public Id newGraph(String uri) { return null ; }

}