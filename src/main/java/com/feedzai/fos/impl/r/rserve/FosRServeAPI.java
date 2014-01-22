/*
 * $#
 * FOS R implementation
 *  
 * Copyright (C) 2013 Feedzai SA
 *  
 * This software is licensed under the Apache License, Version 2.0 (the "Apache License") or the GNU
 * Lesser General Public License version 3 (the "GPL License"). You may choose either license to govern
 * your use of this software only upon the condition that you accept all of the terms of either the Apache
 * License or the LGPL License.
 *
 * You may obtain a copy of the Apache License and the LGPL License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Apache License
 * or the LGPL License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the Apache License and the LGPL License for the specific language governing
 * permissions and limitations under the Apache License and the LGPL License.
 * #$
 */

package com.feedzai.fos.impl.r.rserve;

import com.feedzai.fos.api.FOSException;

import java.util.List;

/**
 * FOS interface to interface with RServe
 *
 * @author rafael.marmelo
 * @author miguel.duarte
 */
public interface FosRServeAPI {

    /**
     * Loads a R script to the backing R process
     * @param script path to the R script to be loaded s
     * @throws FOSException if the file load failed
     */
    public void load(String script) throws FOSException;

    /**
     * Evals a R command and returns the returned value as the expected Java type
     * @param command R command to execute
     * @param <T> Expected return type
     * @return returned value
     * @throws FOSException  Thrown if R eval fails
     */
    public <T> T eval(String command) throws FOSException;

    /**
     * Remove all variable definitions from all R namespaces
     * @throws FOSException if unable to remove
     */
    public void reset() throws FOSException;

    /**
     * Close the connection with the backing RServe process
     * @throws FOSException if unable to disconnect
     */
    public void close() throws FOSException;

    /**
     * Shutdown the backing R process. No new connections will be allowed afterwards
     * @throws FOSException
     */
    public void shutdown() throws FOSException;

    /**
     * Convenience method to assign a string list inside a given R environment
     * @param varname variable to be assigned
     * @param rEnvironment environment where the variable should be created
     * @param values values to be assigned
     * @throws FOSException if failed to assign variable
     */
    void assignStringList(String varname, String rEnvironment, List<String> values) throws FOSException;

    /**
     * Convenience method to assign a int list inside a given R environment
     * @param varname variable to be assigned
     * @param rEnvironment environment where the variable should be created
     * @param values values to be assigned
     * @throws FOSException if failed to assign variable
     */
    void assignIntList(String varname, String rEnvironment, List<Integer> values) throws FOSException;
}
