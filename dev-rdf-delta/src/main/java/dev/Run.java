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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.atlas.io.IO;
import org.apache.jena.atlas.json.*;
import org.apache.jena.atlas.json.io.JsonWriter;
import org.apache.jena.atlas.lib.DateTimeUtils;
import org.apache.jena.atlas.lib.InternalErrorException;
import org.apache.jena.atlas.logging.Log;
import org.apache.jena.atlas.logging.LogCtl;
import org.apache.jena.ext.com.google.common.io.Files;
import org.apache.jena.tdb.base.file.Location;
import org.seaborne.delta.Id;
import org.seaborne.delta.PersistentState;
import org.seaborne.delta.lib.JSONX;
import org.seaborne.delta.server.local.DataSource;
import org.seaborne.delta.server.local.LocalServer;

public class Run {
    static { 
        //LogCtl.setLog4j(); 
        LogCtl.setJavaLogging();
    }
    
    // JSON?
    
    // Add to datasource description.
    static class DataSourceState {
        public final Id datasourceId;
        public final long version;
        public final Id patchId;
        public final String timestamp;
        public DataSourceState(Id datasourceId, long version, Id patchId, String timestamp) {
            this.datasourceId = datasourceId;
            this.version = version;
            this.patchId = patchId;
            this.timestamp = timestamp;
        }
    }
    
    public static class DSS_JSON implements DSS_IO { 
        public static final String kDATASOURCE  = "datasource";
        public static final String kTIMESTAMP   = "timestamp";
        public static final String kPATCH       = "patch";
        public static final String kVERSION     = "version";
    
        @Override
        public void writeState(PersistentState state, Id datasourceId, long version, Id patchId) {
            if ( datasourceId == null )
                throw new InternalErrorException("No datasource id");
            String timestamp = DateTimeUtils.nowAsXSDDateTimeString();
            JsonObject obj =
                JSONX.buildObject(b->{
                    b.key(kDATASOURCE).value(datasourceId.asPlainString());
                    if ( timestamp != null )
                        b.key(kTIMESTAMP).value(timestamp);
                    if ( version >= 0 )
                        b.key(kVERSION).value(version);
                    if ( patchId != null )
                        b.key(kPATCH).value(patchId.asPlainString());
                });
            // Hmm.
            state.setString(JSON.toString(obj));
        }
        
        @Override
        public DataSourceState readState(PersistentState state) {
            JsonObject obj = JSON.parse(state.getString());
            if ( ! obj.hasKey(kDATASOURCE) )
                throw new InternalErrorException("No datasource id");
            Id datasource = Id.fromString(obj.get(kDATASOURCE).getAsString().value());
            long version = -1;
            if ( obj.hasKey(kVERSION) )
                version = obj.get(kVERSION).getAsNumber().value().longValue();
            Id patchId = null;
            if ( obj.hasKey(kPATCH) )
                patchId = Id.fromString(obj.get(kPATCH).getAsString().value());
            String timestamp = null;
            if ( obj.hasKey(kTIMESTAMP) )
                timestamp = obj.get(kTIMESTAMP).getAsString().value();
            return new DataSourceState(datasource, (int)version, patchId, timestamp);
        }
    }
    
    public interface DSS_IO {
        public void writeState(PersistentState state, Id datasourceId, long version, Id patchId);
        public DataSourceState readState(PersistentState state); 
    }
    
    public static class DSS_Properties implements DSS_IO { 
        @Override
        public void writeState(PersistentState state, Id datasourceId, long version, Id patchId) {
            String xs;
            String timestamp = DateTimeUtils.nowAsXSDDateTimeString();
            StringBuilder builder = new StringBuilder(256);
            if ( datasourceId == null )
                throw new InternalErrorException("No datasource id");
            append(builder, "datasource", datasourceId.asPlainString());
            try {
                if ( timestamp != null )
                    append(builder, "timestamp", timestamp);
                if ( version >= 0 )
                    append(builder, "version", Long.toString(version));
                if ( patchId != null )
                    append(builder, "patch", patchId.asPlainString());
                xs = builder.toString(); 
            } catch (Exception ex) {
                Log.error(DataSource.class, "Failed to format the state to write", ex);
                throw new IllegalArgumentException("Failed to format the state to write", ex);
            }
            state.setString(xs);
        }

        @Override
        public DataSourceState readState(PersistentState state) {
            Properties props = new Properties();
            try(Reader in = new StringReader(state.getString())) {
                props.load(in);
            } catch (IOException ex) { IO.exception(ex); }
            String datasourceStr = props.getProperty("datasource");
            String versionStr = props.getProperty("version"); 
            String patchStr = props.getProperty("patch");
            String timestamp = props.getProperty("timestamp");
            return new DataSourceState(Id.fromString(datasourceStr),
                                       versionStr == null ? -1 : Integer.parseInt(versionStr),
                                       patchStr == null ? null : Id.fromString(patchStr),
                                       timestamp 
                );
        }

        private void append(StringBuilder builder, String field, String value) {
            builder.append(field);
            builder.append(" = ");
            builder.append(value);
            builder.append("\n");
        }
    }

    public static void print(DataSourceState dss) {
        //System.out.print(String.format("datasource=%s  version=%s  patch=%s\n", dss.datasourceId, dss.version, dss.patchId));
        System.out.printf("  datasource = %s\n",  dss.datasourceId);
        System.out.printf("  version    = %s\n",  dss.version);
        System.out.printf("  patch      = %s\n",  dss.patchId);
        System.out.printf("  timestamp  = %s\n",  dss.timestamp);
    }
    
    public static void main(String... args) throws IOException {
        {   
            String x = null;
            
            
            DSS_IO dssIO = new DSS_JSON();
            PersistentState ps = new PersistentState("foobar");
            Id id1 = Id.create();
            Id id2 = Id.create();
            dssIO.writeState(ps, id1, 100, id2);
            
            DataSourceState dss = dssIO.readState(ps);
            print(dss);
            
            if ( ! Objects.equals(dss.datasourceId, id1) )
                System.out.println("Error on id1");
            if ( ! Objects.equals(dss.patchId, id2) )
                System.out.println("Error on id2");
            
            Files.copy(Paths.get("foobar").toFile(), System.out);
            
            JsonArray a = new JsonArray();
            a.add(57);
            a.add(59);
            JSON.write(System.out, a);
            
            
            System.out.println("DONE");
            System.exit(0);
            
//            Pattern pattern = Pattern.compile("#([^\\n]*)\ndatasource=([^\\n]*)\nversion=([^\\n]*)\npatch=([^\\n]*)\n");
//            Matcher m = pattern.matcher(xs);
//            System.out.println(m.matches());
//            System.out.println(m.groupCount());
//            for ( int i = 1 ; i <= m.groupCount() ; i++ )
//                System.out.println(m.group(i));
            
            
            System.out.println("DONE");
            System.exit(0);
        }
        Location loc = Location.create("DeltaServer");
        LocalServer server = LocalServer.attach(loc);
        
        state(server.listDataSources());
        System.out.println();
        
        // Correctly fails if exists
        Id newId = server.createDataSource(false, "XYZ", "http://example/xyz");
        DataSource dSrc = server.getDataRegistry().get(newId);
        
        List<DataSource> x = server.listDataSources();
        x.forEach((ds)->{
            System.out.println(ds);
            System.out.println("    "+ds.getURI());
            System.out.println("    "+ds.getId());
            System.out.println("    "+ds.getLocation());
            System.out.println("    "+ds.getPatchSet());
        });
        System.out.println("DONE");
    }
    
    public static void state(List<DataSource> x) {
        x.forEach((ds)->{
            System.out.println(ds);
            System.out.println("    "+ds.getURI());
            System.out.println("    "+ds.getId());
            System.out.println("    "+ds.getLocation());
            System.out.println("    "+ds.getPatchSet());
        });
    }
}