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
import com.feedzai.fos.impl.r.RScorer;
import com.google.common.io.Files;
import org.apache.commons.io.Charsets;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * This library was tested in Linux and Windows with R-2.15.1.
 * In either systems, R must be globally available in the include path.
 * (in Widows you need to add the R bin directory to the "Path" system variable)
 *
 * A previously running RServe process must be running before using this package
 *
 * To install Rserve open a command line, start R and type {@code install.packages(\"Rserve\")}.
 *
 * After Rserve has been installed sucessfully, start a rserve daemon using the following command line
 * {@code R --no-save --slave -e "library(Rserve);Rserve(args='--no-save --slave');"}
 *
 *
 * @author rafael.marmelo
 * @author miguel.duarte
 * @since 1.0.2
 */
public class FosRserve implements FosRServeAPI {
    private static Process rProcess;
    private RConnection connection;
    Logger logger = LoggerFactory.getLogger(FosRserve.class);

    /**
     * Create a new R communication handle spawning a new RServe process if necessary
     *
     * @throws FOSException
     */
    public FosRserve() throws FOSException {
        try {
            this.connection = new RConnection();
        } catch (RserveException e) {
            throw new FOSException(e);
        }
    }


    @Override
    public void load(String script) throws FOSException {
        try {
            File file = new File(script);

            if (!file.exists()) {
                throw new FOSException("Error loading script '" + script + "' into R (script not found).");
            }

            if (!file.isFile()) {
                throw new FOSException("Error loading script '" + script + "' into R (unsupported file type).");
            }

            // read file
            String contents = Files.toString(new File(script), Charsets.UTF_8);
            // strip all CR because R in Windows does not like them
            contents = contents.replaceAll("\r\n", "\n");

            // evaluate script
            eval(contents);
        } catch (IOException e) {
            throw new FOSException("Error loading script '" + script + "' into R.", e);
        }
    }

    @Override
    public <T> T eval(String command) throws FOSException {
        try {
            if(logger.isTraceEnabled()) {
                logger.trace(command);
            }

            connection.assign("trycodeblock", command);

            REXP result = connection.parseAndEval("try(eval(parse(text=trycodeblock)),silent=TRUE)");
            if (result != null && result.inherits("try-error")) {
                throw new FOSException(result.asString());
            }
            if (result == null || result.isNull()) {
                return null;
            } else if( result.isVector() && result.isNumeric()) {
              return (T) result.asDoubles();
            } else if (result.isInteger()) {
                return (T) new Integer(result.asInteger());
            } else if (result.isNumeric()) {
                return (T) new Double(result.asDouble());
            } else if (result.isString()) {
                return (T) result.asString();
            }
            return null;
        } catch (Exception e) {
            throw new FOSException("Error executing R script.", e);
        }
    }

    @Override
    public void reset() throws FOSException {
        eval("rm(list = ls(all = TRUE))");
    }

    @Override
    public void close() throws FOSException {
        if (connection != null && connection.isConnected()) {
            connection.close();
        }
    }

    @Override
    public void shutdown() throws FOSException {
        if (connection != null && connection.isConnected()) {
            try {
                connection.shutdown();
            } catch (RserveException e) {
                throw new FOSException("Error shutting down R server.", e);
            }
        }
    }

    @Override
    public void assignStringList(String varname, String rEnvironment, List<String> values) throws FOSException {
        StringBuilder sb = new StringBuilder();

        sb.append(rEnvironment)
          .append('$')
          .append(RScorer.rVariableName(varname))
          .append(" <- c(\n");

        int i = 0;
        while(i < values.size() - 1) {
            sb.append("   '").append(values.get(i++)).append("',\n");
        }
        sb.append("   '").append(values.get(i)).append("')");


        eval(sb.toString());
    }

    @Override
    public void assignIntList(String varname, String rEnvironment, List<Integer> values) throws FOSException {
        StringBuilder sb = new StringBuilder();

        sb.append(rEnvironment)
                .append('$')
                .append(varname)
                .append(" <- c(\n");

        int i = 0;
        while(i < values.size() - 1) {
            sb.append("   ").append(values.get(i++)).append(",\n");
        }
        sb.append("   ").append(values.get(i)).append(")");

        eval(sb.toString());
    }
}
