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

package dev.examples;

import java.io.IOException;
import java.util.List;

import org.apache.jena.tdb.base.file.Location;
import org.seaborne.delta.Id;
import org.seaborne.delta.client.DeltaConnection;
import org.seaborne.delta.client.DeltaLinkHTTP;
import org.seaborne.delta.client.Zone;
import org.seaborne.delta.link.DeltaLink;
import org.seaborne.delta.server.http.DataPatchServer;
import org.seaborne.delta.server.local.DataSource;
import org.seaborne.delta.server.local.DeltaLinkLocal;
import org.seaborne.delta.server.local.LocalServer;

/** Connect to a local server, create a new DataSource, remove it. */ 
public class Example2 {
    public static void example_local(String... args) throws IOException {
        // The local state of the server.
        Location loc = Location.create("DeltaServer");
        LocalServer localServer = LocalServer.attach(loc);
        DeltaLink serverState = DeltaLinkLocal.connect(localServer);
        DataPatchServer server = DataPatchServer.create(1066, serverState);
        // --------

        // Connect to a server
        DeltaLink dLink = DeltaLinkHTTP.connect("http://localhost:1066/");
        // One one zone supported currently.
        Zone zone = Zone.get();
        Id clientId = Id.create();
        
        // Create a new patch log.
        try ( DeltaConnection dConn = DeltaConnection.create(zone, clientId, "TEST", "http://example/", null, dLink) ) {
        }

        // and now connect to it again.
        try ( DeltaConnection dConn = DeltaConnection.connect(zone, clientId, null, null, dLink) ) {
            int version = dConn.getRemoteVersionLatest();
            System.out.println("Version = "+version);
        }

        System.out.println("DONE");
    }
    
}
