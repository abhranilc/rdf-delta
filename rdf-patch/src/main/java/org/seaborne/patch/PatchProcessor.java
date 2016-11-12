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

package org.seaborne.patch;

public interface PatchProcessor {

    /** Process the whole patch - zero or more transactions.
     * @apiNote
     * Calls start-finish around the processing.
     *  
     * @param destination
     */
    public default void apply(RDFChanges destination) {
        destination.start() ;
        while(hasMore()) {
            apply1(destination) ;
        }
        destination.finish() ;
    }
    
    // Or just "apply"?
    
    public boolean hasMore() ;
    
    /** Execute one transaction.
     *  Return true if there is the possiblity of more.
     *  Does not wrap in start-finish.
     */
    public boolean apply1(RDFChanges destination) ;
}