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

import static org.seaborne.delta.DPConst.F_BASE;
import static org.seaborne.delta.DPConst.F_ID;
import static org.seaborne.delta.DPConst.F_URI;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonBuilder;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.atlas.lib.ListUtils;
import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.tdb.base.file.Location;
import org.seaborne.delta.*;
import org.seaborne.delta.lib.IOX;
import org.seaborne.delta.lib.JSONX;
import org.seaborne.delta.lib.LibX;
import org.seaborne.delta.link.DeltaLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A local server.
 *  <p>
 *  This provides for several {@link DataSource} areas (one per managed patch set - i.e. one per dataset).
 *  {@code LocalServer} is responsible for server wide configuration and for the 
 *  {@link DataSource} lifecycle of create and delete.  
 *    
 * @see DeltaLinkLocal 
 * @see DataSource 
 *
 */
public class LocalServer {
    /* File system layout:
     *   Server Root
     *      delta.cfg
     *      /NAME ... per DataSource.
     *          autogenerated assembler?
     *          /patches
     *          /data -- TDB database (optional)
     *          disabled -- if this file is present, then the datasource is not accessible.  
     *          
     *  But also external databases 
     *  
     *  Need to stop two LocalServers on one location.
     */
    
    private static Logger LOG = LoggerFactory.getLogger(LocalServer.class);
    
    private static Map<Location, LocalServer> servers = new ConcurrentHashMap<>();
    
    private final DataRegistry dataRegistry;
    private final Location serverRoot;
    private final LocalServerConfig serverConfig;
    private static AtomicInteger counter = new AtomicInteger(0);
    // Cache of known disabled data sources. (Not set of startup).
    private Set<Id> disabledDatasources = new HashSet<>();
    private Object lock = new Object();
    
    /** Attach to the runtime area for the server. Use "delta.cfg" as the configuration file name.  
     * @param serverRoot
     * @return LocalServer
     */
    public static LocalServer attach(Location serverRoot) {
        return attach(serverRoot, DPConst.SERVER_CONFIG); 
    }
    
    /** Attach to the runtime area for the server. 
     * Use "delta.cfg" as the configuration file name.
     * Use the directory fo the configuration file as the location.
     * @param confFile  Filename
     * @return LocalServer
     */
    public static LocalServer attach(String confFile) {
        LocalServerConfig conf = LocalServerConfig.create()
            .parse(confFile)
            .build();
        return attach(conf);
    }
    

    /** Attach to the runtime area for the server.
     * @param serverRoot
     * @param confFile  Filename: absolute filename, or relative to the server process.
     * @return LocalServer
     */
    public static LocalServer attach(Location serverRoot, String confFile) {
        confFile = LibX.resolve(serverRoot, confFile);
        LocalServerConfig conf = LocalServerConfig.create()
            .setLocation(serverRoot)
            .parse(confFile)
            .build();
        return attach(conf);
    }
    
    public static LocalServer attach(LocalServerConfig conf) {
        Objects.requireNonNull(conf, "Null for configuation");
        if ( conf.location == null )
            throw new DeltaConfigException("No location");
        
        DataRegistry dataRegistry = new DataRegistry("Server"+counter.incrementAndGet());
        Pair<List<Path>, List<Path>> pair = scanDirectory(conf.location, dataRegistry);
        List<Path> dataSources = pair.getLeft();
        List<Path> disabledDataSources = pair.getRight();
        
        dataSources.forEach(p->LOG.info("Data source: "+p));
        disabledDataSources.forEach(p->LOG.info("Data source: "+p+" : Disabled"));
        
        for ( Path p : dataSources ) {
            DataSource ds = makeDataSource(p);
            dataRegistry.put(ds.getId(), ds);
        }
        return attach(conf, dataRegistry);
    }
    
    public static void release(LocalServer localServer) {
        Location key = localServer.serverConfig.location;
        if ( key != null ) {
            servers.remove(key);
            localServer.shutdown$();
        }
    }
     
    /** 
     * Scan a directory for datasource areas.
     * These must have a file called source.cfg.
     */
    private static Pair<List<Path>/*enabled*/, List<Path>/*disabled*/> scanDirectory(Location serverRoot, DataRegistry dataRegistry) {
        Path dir = IOX.asPath(serverRoot);
        try { 
            List<Path> directory = ListUtils.toList( Files.list(dir).filter(p->Files.isDirectory(p)) );
            directory.stream()
                .filter(LocalServer::isFormattedDataSource)
                .collect(Collectors.toList());
            List<Path> enabled = directory.stream()
                .filter(path -> isEnabled(path))
                .collect(Collectors.toList());
            List<Path> disabled = directory.stream()
                .filter(path -> !isEnabled(path))
                .collect(Collectors.toList());
            return Pair.create(enabled, disabled);
        }
        catch (IOException ex) {
            LOG.error("Exception while reading "+dir);
            throw IOX.exception(ex);
        }
    }

    /** Test for a valid data source - does not check "disabled" */
    private static boolean isFormattedDataSource(Path path) {
        if ( ! isMinimalDataSource(path) )
            return false;
        // Additional requirements
        Path patchesArea = path.resolve(DPConst.PATCHES);
        if ( ! Files.exists(patchesArea) )
            return false;
        // If we keep a state file....
//        Path pathVersion = path.resolve(DPConst.STATE_FILE);
//        if ( ! Files.exists(pathVersion) )
//            return false;
        return true ;
    }
    
    /** Basic tests - not valid DataSource area but the skeleton of one.
     * Checks it is a directory and has a configuration files.
     */
    private static boolean isMinimalDataSource(Path path) {
        if ( ! Files.isDirectory(path) ) 
            return false ;
        Path cfg = path.resolve(DPConst.DS_CONFIG);
        if ( ! Files.exists(cfg) )
            return false ;
        if ( ! Files.isRegularFile(cfg) ) 
            LOG.warn("Data source configuration file name exists but is not a file: "+cfg);
        if ( ! Files.isReadable(cfg) )
            LOG.warn("Data source configuration file exists but is not readable: "+cfg);
        return true ;
    }

    private static boolean isEnabled(Path path) {
        Path disabled = path.resolve(DPConst.DISABLED);
        return ! Files.exists(disabled);
    }

    private static DataSource makeDataSource(Path dataSourceArea) {
        JsonObject sourceObj = JSON.read(dataSourceArea.resolve(DPConst.DS_CONFIG).toString());
        SourceDescriptor dss = fromJsonObject(sourceObj);

        Id id = dss.id;
        String baseStr = dss.base;
        String uriStr = dss.uri; 
            
//        String idStr = JSONX.getStrOrNull(sourceObj, F_ID) ;
//        Id id = Id.fromString(idStr) ; 
//        String uriStr = JSONX.getStrOrNull(sourceObj, F_URI) ;
//        String baseStr = JSONX.getStrOrNull(sourceObj, F_BASE);
      
        Path patchesArea = dataSourceArea.resolve(DPConst.PATCHES);
        FileOps.ensureDir(patchesArea.toString());
        //FmtLog.info(LOG, "DataSource: id=%s, source=%s, patches=%s", id, dataSourceArea, patchesArea);
        
        // --> Path
        DataSource dataSource = DataSource.connect(id, uriStr, dataSourceArea.getFileName().toString(), IOX.asLocation(dataSourceArea));
        FmtLog.info(LOG, "DataSource: %s (%s)", dataSource, baseStr);
      
        return dataSource ;
    }
    
    private static LocalServer attach(LocalServerConfig config, DataRegistry dataRegistry) {
        Location loc = config.location;
        if ( ! loc.isMemUnique() ) {
            if ( servers.containsKey(loc) ) {
                LocalServer lServer = servers.get(loc);
                LocalServerConfig config2 = lServer.getConfig();
                if ( Objects.equals(config, config2) ) {
                    return lServer; 
                } else {
                    throw new DeltaException("Attempt to attach to existing location with different configuration: "+loc);
                }
            }
        }
        LocalServer lServer = new LocalServer(config, dataRegistry);
        if ( ! loc.isMemUnique() ) 
            servers.put(loc, lServer);
        return lServer ;
    }

    private LocalServer(LocalServerConfig config, DataRegistry dataRegistry) {
        this.serverConfig = config;
        this.dataRegistry = dataRegistry;
        this.serverRoot = config.location;
    }

    public void shutdown() {
        LocalServer.release(this);
    }

    private void shutdown$() {
        dataRegistry.clear();
    }

    public DataRegistry getDataRegistry() {
        return dataRegistry;
    }
    
    public DataSource getDataSource(Id dsRef) {
        if ( disabledDatasources.contains(dsRef) )
            return null;
        return dataRegistry.get(dsRef);
    }

    /** Get port - may be negative to indicate "no valid port" */
    public int getPort() {
        return serverConfig.port;
    }

    /** Get port - maybe negative to indicate "no valid port" */
    public LocalServerConfig getConfig() {
        return serverConfig;
    }


    public List<Id> listDataSourcesIds() {
        return new ArrayList<>(dataRegistry.keys());
    }
    
    public List<DataSource> listDataSources() {
      List<DataSource> x = new ArrayList<>();
      dataRegistry.forEach((id, ds)-> x.add(ds));
      return x;
    }

    public SourceDescriptor getDescriptor(Id dsRef) {
        DataSource dataSource = dataRegistry.get(dsRef);
        return descriptor(dataSource);
    }
    
    private SourceDescriptor descriptor(DataSource dataSource) {
        SourceDescriptor descr = new SourceDescriptor
            (dataSource.getId(),
             dataSource.getURI(),
             dataSource.getName());
        return descr;
    }
    
    static class SourceDescriptor {
        final String uri;
        final Id id;
        final String base;
        public SourceDescriptor(Id id, String uri, String base) {
            super();
            this.id = id;
            this.uri = uri;
            this.base = base;
        }
    }
    
    private static Location dataSourceArea(Location serverRoot, String name) {
        return serverRoot.getSubLocation(name);
    }
    
    /*package*/ static Location patchArea(Location dataSourceArea) {
        return dataSourceArea.getSubLocation(DPConst.PATCHES);
    }

    // static DataSource.createDataSource.
    
    /** Create a new data source.
     * This can not be one that has been removed (i.e disabled) whose files must be cleaned up manually.
     */
    public Id createDataSource(boolean inMemory, String name, String baseURI/*, details*/) {
        Location sourceArea = dataSourceArea(serverRoot, name);
        Path sourcePath = IOX.asPath(sourceArea);
        
        // Checking.
        // The area can exist, but it must not be formatted for a DataSource 
//        if ( sourceArea.exists() )
//            throw new DeltaException("Area already exists");

        if ( isMinimalDataSource(sourcePath) )
            throw new DeltaBadRequestException("DataSource area already exists at: "+sourceArea);
        if ( ! isEnabled(sourcePath) )
            throw new DeltaBadRequestException("DataSource area disabled: "+sourceArea);
        
        String patchesDirName = sourceArea.getPath(DPConst.PATCHES);
        if ( FileOps.exists(patchesDirName) )
            throw new DeltaBadRequestException("DataSource area does not have a configuration but does have a patches area.");

        String dataDirName = sourceArea.getPath(DPConst.DATA);
        if ( FileOps.exists(dataDirName) )
            throw new DeltaBadRequestException("DataSource area has a likely looking database already");
        
        //Location db = sourceArea.getSubLocation(DPConst.DATA);
        
        Id dsRef = Id.create();
        SourceDescriptor descr = new SourceDescriptor(dsRef, baseURI, name);
        
        // Create source.cfg.
        if ( ! inMemory ) {
            JsonObject obj = toJsonObj(descr);
            LOG.info(JSON.toStringFlat(obj));
            try (OutputStream out = Files.newOutputStream(sourcePath.resolve(DPConst.DS_CONFIG))) {
                JSON.write(out, obj);
            } catch (IOException ex)  { throw IOX.exception(ex); }
        }
        DataSource newDataSource = DataSource.connect(dsRef, baseURI, name, sourceArea);
        // Atomic.
        dataRegistry.put(dsRef, newDataSource);
        return dsRef ;
    }
    
    public void removeDataSource(Id dsRef) {
        DataSource datasource = getDataSource(dsRef);
        if ( datasource == null )
            return;
        // Atomic.
        dataRegistry.remove(dsRef);
        disabledDatasources.add(dsRef);
        // Mark unavailable.
        if ( ! datasource.inMemory() ) {
            Path disabled = datasource.getPath().resolve(DPConst.DISABLED);
            try { Files.createFile(disabled); } 
            catch (IOException ex) { throw IOX.exception(ex); }
        }
    }

    /** JsonObject -> SourceDescriptor */
    private static SourceDescriptor fromJsonObject(JsonObject sourceObj) {
        String idStr = JSONX.getStrOrNull(sourceObj, F_ID);
        SourceDescriptor descr = new SourceDescriptor
            (Id.fromString(idStr), 
             JSONX.getStrOrNull(sourceObj, F_URI),
             JSONX.getStrOrNull(sourceObj, F_BASE));
        return descr;
    }
    
    /** SourceDescriptor -> JsonObject */
    private static JsonObject toJsonObj(SourceDescriptor descr) {
        return
            JSONX.buildObject(builder->{
                set(builder, F_ID, descr.id.asPlainString());
                set(builder, F_URI, descr.uri);
                set(builder, F_BASE, descr.base);
            });
    }

    private static void set(JsonBuilder builder, String field, String value) {
        if ( value != null )
            builder.key(field).value(value);
    }

    public void resetEngine(DeltaLink link) {}
}
