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

package dev;

import java.io.IOException;
import java.util.List;

import org.apache.jena.atlas.lib.DateTimeUtils;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.atlas.logging.LogCtl;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.sse.SSE;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb.base.file.Location;
import org.seaborne.delta.Id;
import org.seaborne.delta.client.DeltaConnection;
import org.seaborne.delta.client.Zone;
import org.seaborne.delta.link.DeltaLink;
import org.seaborne.delta.server.local.DeltaLinkLocal;
import org.seaborne.delta.server.local.LocalServer;

public class Run {
    static { 
        //LogCtl.setLog4j(); 
        LogCtl.setJavaLogging();
    }
    
    // Tests for restart
    
    // Remove (hide) DSG from DeltaConnection start-up : TDB or file only.
    
    // ** Need patch rejection
    // ** Start at id:nil (or id:datasource?)
    
    // TransPBlob not PersistentState ; memory versions.
    
    // [PersistentVersion] in client 
    //   DeltaLink? DeltaConnection? 
    //   Persistent counter, integritity of the log and client side. 
    
    // PatchCache
    
    // Client side persistence. 
    // Client side shadow.
    // DataState init

    /*
18:04:55 INFO  Delta                : Patch range [4, 4]
18:04:55 INFO  Delta                : Sync: patch=4

Don't read 4!
     */
    
    // Tests for persistence lifecycle: client, server.
    //   AbstractTestDeltaConnection
    //   TestLocalServer
    // Client-shadow data.
    
    // DataState - map of dsRef->area.
    //   Scan for existing.
    
    // try-resource fo DeltaConnection.
    
    public static void main(String... args) throws IOException {
        System.out.println("**** run 1");
        // Create clean.
        run(true, true);
        System.out.println();
        System.out.println("**** run 2");
        // Resonnect to existing local and remote.
        run(false, false);
        System.out.println();
        System.out.println("**** run 3");
        // Connect to existing remote.
        run(false, true);

        System.out.println("DONE");
        System.exit(0);
    }
    
    public static void run(boolean cleanServer, boolean cleanConnections) throws IOException {
        Zone zone = Zone.get();
        zone.reset();
        String datasourceName = "XYZ";
        Location serverLoc = Location.create("DeltaServer");
        Location dConnLoc = Location.create("DConn");
        
        if ( cleanServer ) {
            FileOps.clearAll(serverLoc.getPath(datasourceName));
            FileOps.delete(serverLoc.getPath(datasourceName));
        }

        if ( cleanConnections ) {
            FileOps.clearAll(dConnLoc.getDirectoryPath());
        }

        zone.init(dConnLoc);
        System.out.println("Local connections: "+zone.localConnections());

        
        LocalServer server = LocalServer.attach(serverLoc);
        
        // --------
        //List<DataSource> x = server.listDataSources();
        DeltaLink dLink = DeltaLinkLocal.connect(server);
        
        Id clientId = Id.create();
        //dLink.register(clientId);
        DatasetGraph dsg0 = DatasetGraphFactory.createTxnMem();
        
        if ( cleanServer ) {
            // Create, but do not open.
            // Two cases. Local TDB, local file.
            // v1 - initialialy empty.
            
            //DeltaConnection.create(clientId, dConnLoc, datasourceName, "http://example/new");
        }
        
        try(DeltaConnection dConn = connectOrCreate(zone, clientId, datasourceName, dsg0, dLink, cleanServer) ) {
            dConn.sync();
            DatasetGraph dsg = dConn.getDatasetGraph();
            Txn.executeWrite(dsg,  ()->{
                Quad q = SSE.parseQuad("(_ :ss :pp '"+DateTimeUtils.nowAsXSDDateTimeString()+"'^^xsd:dateTimeStamp)");
                dsg.add(q);
            });
            
            //dLink.listDatasets();
            System.out.println("Local connections: "+zone.localConnections());
            
        }        
    }
    
    static DeltaConnection connectOrCreate(Zone zone, Id clientId, String datasourceName, DatasetGraph dsg0, DeltaLink dLink, boolean create) {
        if ( create ) {
            DeltaConnection dConn = DeltaConnection.create(zone, clientId, datasourceName, "http://example/new", dsg0, dLink);
            Id dsRef = dConn.getDatasourceId();
            System.out.println("++++ Create: "+dsRef);
            return dConn;
        } else {
            List<Id> datasources = dLink.listDatasets();
            System.out.println("++++ "+datasources);
            Id dsRef = datasources.get(0);
            DeltaConnection dConn = DeltaConnection.connect(zone, clientId, dsRef, dsg0, dLink);
            return dConn;
        }
    }
}
