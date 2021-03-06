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

package org.seaborne.patch.filelog;

import static org.apache.jena.sparql.util.graph.GraphUtils.exactlyOneProperty;

import java.util.List;

import org.apache.jena.assembler.Assembler;
import org.apache.jena.assembler.Mode;
import org.apache.jena.assembler.assemblers.AssemblerBase;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.atlas.lib.IRILib;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.graph.GraphUtils;
import org.apache.jena.util.FileUtils;
import org.seaborne.patch.RDFChanges;
import org.seaborne.patch.RDFPatchConst;
import org.seaborne.patch.RDFPatchOps;
import org.seaborne.patch.changes.RDFChangesN;
import org.seaborne.patch.filelog.rotate.ManagedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssemblerFileLog extends AssemblerBase {
    private static Logger LOG = LoggerFactory.getLogger(AssemblerFileLog.class);
    
    @Override
    public Object open(Assembler a, Resource root, Mode mode) {
        if ( !exactlyOneProperty(root, VocabPatch.pDataset) )
            throw new AssemblerException(root, "No dataset to be logged");
        if ( !root.hasProperty(VocabPatch.pLogFile) )
            throw new AssemblerException(root, "No log file");

        Resource dataset = GraphUtils.getResourceValue(root, VocabPatch.pDataset);
        List<String> destLogs =  GraphUtils.multiValueAsString(root, VocabPatch.pLogFile);
        
        String logPolicy = GraphUtils.getStringValue(root, VocabPatch.pLogPolicy);
        FilePolicy policy = logPolicy == null ? FilePolicy.INDEX : FilePolicy.policy(logPolicy);
        
        DatasetGraph dsgBase;;
        try { 
            Dataset dsBase = (Dataset)a.open(dataset);
            dsgBase = dsBase.asDatasetGraph();
        } catch (Exception ex) {
            FmtLog.error(this.getClass(), "Failed to build the dataset to adding change logging to: %s",dataset);
            throw ex;
        }
        
//        RDFChanges streamChanges = null ;
//        for ( String dest : xs ) {
//            FmtLog.info(log, "Destination: '%s'", dest) ;
//            RDFChanges sc = DeltaLib.destination(dest); << relies on file:
//            streamChanges = RDFChangesN.multi(streamChanges, sc) ;
//        }
        
        RDFChanges changes = null ;
        
        
        for ( String x : destLogs ) {
            FmtLog.info(LOG, "Log file: '%s'", x);
            if ( x.startsWith("file:") )
                x = IRILib.IRIToFilename(x);
            FmtLog.info(LOG, "Log file: '%s'", x);
            
            ManagedOutput output = OutputMgr.create(x, policy);
            // --------------
            String ext = FileUtils.getFilenameExt(x);
//            if ( ext.equals("gz") ) {
//                String fn2 = x.substring(0, ".gz".length());
//                ext = FileUtils.getFilenameExt(fn2);
//            }
//            OutputStream out = IO.openOutputFile(x);
            
            boolean binaryPatches = ext.equalsIgnoreCase(RDFPatchConst.EXT_B);
            RDFChanges sc = binaryPatches
                ? null //RDFPatchOps.binaryWriter(out);
                //: RDFPatchOps.textWriter(out);
                : new RDFChangesManagedOutput(output);
            
            if ( sc == null )
                throw new AssemblerException(root, "Failed to build the output destination: "+x);
            changes = RDFChangesN.multi(changes, sc) ;
        }
        DatasetGraph dsg = RDFPatchOps.changes(dsgBase, changes);
        Dataset ds = DatasetFactory.wrap(dsg);
        return ds;
    }
}
