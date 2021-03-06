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

package org.seaborne.patch.filelog.rotate;

import java.nio.file.Path;

import org.seaborne.patch.filelog.OutputMgr;

/**
 * Interface to a policy for rotating files. Writing to files is in "sections" - a section
 * always goes into a singel file; multiple sections may go into one file or several.
 * Rollover only happens between sections.
 * <p>
 * {@code startSection}, {@code finishSection} bracket
 * each use of a {@link ManagedOutput} object.
 * 
 * @see OutputMgr
 */
interface Roller {

    /** Directory under management. */
    public Path directory(); 
    
    /** Starting an output section. */
    public void startSection();

    /** Finished an output section. */
    public void finishSection();
    
    /** Policy says that any previous the setup is no longer valid for a new section. */  
    public boolean hasExpired();
    
    /** Move files on (if appropriate) **/
    public void rotate();
    
    /** Generate the next filename; incldues any directory name to the file. */ 
    public String nextFilename();
    
    /** Create a {@link Filename} if and only if it matches the pattern managed by this policy. */
    public Filename toFilename(String filename);
}
