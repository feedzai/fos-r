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
package com.feedzai.fos.impl.r;

import com.feedzai.fos.api.*;
import com.feedzai.fos.impl.r.config.RModelConfig;
import com.feedzai.fos.impl.r.rserve.FosRserve;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implementation of the Scorer API using R as the backend
 *
 * @author miguel.duarte
 * @since 1.0.2
 */
public class RScorer implements Scorer {

    /**
     * Scorer logger
     */
    private static final Logger logger = LoggerFactory.getLogger(RScorer.class);

    /**
     * The prefix for the saveAsPMML function. It will be prefixed with the model's uuid.
     */
    private static final String SAVE_AS_PMML_FUNCTION_PREFIX = "saveAsPMML";

    /**
     * Reference to the backing RServe daemon
     */
    private final FosRserve rserve;

    /**
     * Set with all the configured models
     */
    private Set<UUID>  uuids = new HashSet<>();

    /**
     * Return the scorer for a given model ID
     * @param modelId UUID of the model to score
     * @return the scorer
     * @throws FOSException
     */
    private Scorer getScorer(UUID modelId) throws FOSException {
        return this;
    }

    /**
     * Creates a RScorer instance with a backing RServe process
     * @param rserve Backing rserve process
     */
    public RScorer(FosRserve rserve) throws FOSException {
        checkNotNull(rserve, "Manager config cannot be null");
        this.rserve = rserve;
    }

    /**
     * Create a RScorer instance loading custom libraries
     *
     * @param rserve Backing rserve process
     * @param rlibraries Libraries that will be loaded prior to generating the scoring function
     *
     * @throws FOSException If unable to add the relevant libraries
     */
    public RScorer(FosRserve rserve, String... rlibraries) throws FOSException {
        checkNotNull(rserve, "Manager config cannot be null");
        this.rserve = rserve;

        for (String library : rlibraries) {
            rserve.eval("library(" + library  + ")");
        }
    }


    @Override
    public final double[] score(final UUID modelId, final Object[] scorable) throws FOSException {
        StringBuilder sb = new StringBuilder();
        sb.append(uuid2environment(modelId))
            .append("$score(c(");

        for (int i = 0; i != scorable.length - 1; ++i) {
            appendValue(scorable[i], sb);
            sb.append(',');
        }

        appendValue(scorable[scorable.length - 1], sb);

        sb.append("))");

        return rserve.eval(sb.toString());
    }

    /**
     * Generate the scoring vector in the correct format by quoting strings.
     * All other values will be printed as is
     * @param scorable scorable to be appended
     * @param sb String buffer that contain the generated string
     */
    private void appendValue(Object scorable, StringBuilder sb) {
        if (scorable == null) {
            sb.append("NA"); /* NA is the missing value constant in R */

        } else if (scorable instanceof String) {
            sb.append('"')
              .append(scorable)
              .append('"');

        } else {
            sb.append(scorable);
        }
    }

    @Override
    public void close() throws FOSException {
        for (UUID uuid : uuids) {
            removeModel(uuid);
        }
    }

    /**
     * Add or update a Rmodel
     * @param rModelConfig R model configuration
     * @throws FOSException Thrown on invalid configuration
     */
    public void addOrUpdate(RModelConfig rModelConfig) throws FOSException {
        String rEnvironment = uuid2environment(rModelConfig.getId());

        String libraries = rModelConfig.getModelConfig().getProperty(RModelConfig.LIBRARIES);
        if (libraries != null) {
            for(String library : libraries.split(",")) {
                rserve.eval("library(" + library + ")");
            }
        }

        // create a uuid named environment
        rserve.eval(String.format("%1$s <- new.env()\n" +
                                  "%1$s$modelname <- load (file='%2$s', envir=%1$s)\n",
                                  rEnvironment,
                                  rModelConfig.getModel().getAbsolutePath()));

        ModelConfig config = rModelConfig.getModelConfig();
        List<Attribute> attrs = rModelConfig.getAttributes();

        // Remove class from attribute list
        attrs.remove(config.getIntProperty(RModelConfig.CLASS_INDEX));

        StringBuilder sb = generateScoringFunction(rEnvironment, attrs, rModelConfig);

        rserve.eval(sb.toString());

        // Generate a function to save the model to PMML and add it to the environment.
        String saveAsPMMLFunction = generateSaveAsPMMLFunction(rEnvironment, rModelConfig.getPMMLModel().getAbsolutePath(), rModelConfig.getModelConfig().getIntProperty(rModelConfig.CLASS_INDEX));
        rserve.eval(saveAsPMMLFunction);
    }

    /**
     * This function generates an environment specific scoring function
     *
     * This scoring function will convert a vector with the elements to score into a data frame
     * whose names will be defined by the environment attribute names.
     *
     * For categorical attributes, the levels will be extracted from the trained model.
     *
     * Sample environment specific scoring function
     * <pre>
     * x81b495fdc00944dab01afcf03c85a04e$score <- function(v) {
     *    v <- as.data.frame(t(as.matrix(v)))
     *    names(v) <- c('A1', 'A2', 'A3', 'A4', 'A5', 'A6', 'A7', 'A8', 'A9', 'A10', 'A11', 'A12', 'A13', 'A14', 'A15')
     *
     *    num_range <- c(2,3,8,11,14,15)
     *    v[, num_range] <- sapply(v[, num_range], as.numeric)
     *
     *    factor_range <- c(1,4,5,6,7,9,10,12,13)
     *    v[, factor_range] <- sapply(v[, factor_range], as.factor)
     *
     *    v['A1'] <- factor(v['A1'], levels = c('a', 'b'))
     *    v['A4'] <- factor(v['A4'], levels = c('l', 'u', 'y'))
     *    v['A5'] <- factor(v['A5'], levels = c('g', 'gg', 'p'))
     *    v['A6'] <- factor(v['A6'], levels = c('aa', 'c', 'cc', 'd', 'e', 'ff', 'i', 'j', 'k', 'm', 'q', 'r', 'w', 'x'))
     *    v['A7'] <- factor(v['A7'], levels = c('bb', 'dd', 'ff', 'h', 'j', 'n', 'o', 'v', 'z'))
     *    v['A9'] <- factor(v['A9'], levels = c('f', 't'))
     *    v['A10'] <- factor(v['A10'], levels = c('f', 't'))
     *    v['A12'] <- factor(v['A12'], levels = c('f', 't'))
     *    v['A13'] <- factor(v['A13'], levels = c('g', 'p', 's'))
     *
     *
     *    r <- predict(get(x81b495fdc00944dab01afcf03c85a04e$modelname, envir=x81b495fdc00944dab01afcf03c85a04e), v, type = 'raw')
     *}
     * </pre>
     *
     * @param rEnvironment environment code
     * @param attrs  Fos model attribute list
     * @param rModelConfig  R model configuration
     * @return buffer where the R code will be generated
     */
    private StringBuilder generateScoringFunction(String rEnvironment, List<Attribute> attrs, RModelConfig rModelConfig) throws FOSException {
        StringBuilder sb = new StringBuilder();
        // Generate scoring function preamble

        List<String> attrnames = Lists.transform(attrs, new Function<Attribute, String>() {
            @Override
            public String apply(Attribute input) {
                return rVariableName(input.getName());
            }
        });

        sb.append(String.format(
                "%1$s$score <- function(v) {\n" +
                "   v <- as.data.frame(t(as.matrix(v)))\n" +
                "   names(v) <- c('%2$s')\n\n",
                rEnvironment,
                Joiner.on("', '").join(attrnames)));

        List<Integer> numeric_indices = new ArrayList<>();
        List<Integer> factor_indices = new ArrayList<>();
        int count = 1;

        for (Attribute attribute : attrs) {
            if(attribute instanceof NumericAttribute) {
                numeric_indices.add(count);
            } else if (attribute instanceof CategoricalAttribute) {
                factor_indices.add(count);
            } else {
                throw new FOSException("Unknown attribute type");
            }
            count++;
        }

        generateNumericConversion(numeric_indices, sb);

        generateFactorConversion(factor_indices, sb);

        List<CategoricalAttribute> categoricals = extractCategoricals(attrs);


        generateLevelFactorConversion(rEnvironment, categoricals, sb);

        String predictArguments = rModelConfig.getModelConfig().getProperty(RModelConfig.PREDICT_FUNCTION_ARGUMENTS);

        sb.append(String.format(
                "\n\n" +
                "r <- predict(get(%1$s$modelname, envir=%1$s), v%2$s)\n",
                rEnvironment,
                predictArguments != null ? ", " + predictArguments : ""));

        String resultsTransform = rModelConfig.getModelConfig().getProperty(RModelConfig.PREDICT_RESULT_TRANSFORM);

        if(resultsTransform != null) {
            sb.append(resultsTransform + "\n");
            sb.append("r\n");
        }

        sb.append("}");

        return sb;
    }

    /**
     * Convert the dataframe categorical fields to the same levels as the levels used in the training model.
     *
     * This is necessary because R randomForest checks if the fields levels are exactly the same as the ones discovered
     * during training in adition to check the scorable value.
     *
     * Example:
     *
     * If the X column spec says the categorical field can assume the values "A", "B", and "C" but if during training
     * only "A" and "B" were found, the field levels must be set to "A" and "B" only, otherwise predict will fail.
     * Sample generated code:
     * <pre>
     *    v['A1'] <- factor(v['A1'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A1)
     *    v['A4'] <- factor(v['A4'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A4)
     *    v['A5'] <- factor(v['A5'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A5)
     *    v['A6'] <- factor(v['A6'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A6)
     *    v['A7'] <- factor(v['A7'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A7)
     *    v['A9'] <- factor(v['A9'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A9)
     *    v['A10'] <- factor(v['A10'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A10)
     *    v['A12'] <- factor(v['A12'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A12)
     *    v['A13'] <- factor(v['A13'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A13)
     * </pre>
     *
     * @param rEnvironment environment code
     * @param categoricals categoricals attributes
     * @param sb buffer where the R code will be generated
     */
    private void generateLevelFactorConversion(String rEnvironment, List<CategoricalAttribute> categoricals, StringBuilder sb) {
        for (CategoricalAttribute categoricalAttribute : categoricals) {
            List<String> factors = new ArrayList<>(categoricalAttribute.getCategoricalInstances());
            sb.append(String.format(
                    "   v['%1$s'] <- factor(v['%1$s'], levels = c('%2$s'))\n",
                    categoricalAttribute.getName(),
                    Joiner.on("', '").join(factors)));

        }
    }

    /**
     * Convert all categorical fields to factor
     * Sample Generated code
     * <pre>
     *    factor_range <- c(1,4,5,6,7,9,10,12,13)
     *    v[, factor_range] <- sapply(v[, factor_range], as.factor)
     * </pre>
     * @param indices  List categorical attributes indices
     * @param sb   buffer where the R code will be generated
     */
    private void generateFactorConversion(List<Integer> indices, StringBuilder sb) {
        sb.append("   factor_range <- c(");
        Joiner.on(',').appendTo(sb,indices);
        sb.append(")\n");
        sb.append("   v[, factor_range] <- sapply(v[, factor_range], as.factor)\n\n");
    }

    /**
     * Convert numeric attributes to numeric types
     * Sample generated code
     * <pre>
     *    num_range <- c(2,3,8,11,14,15)
     *    v[, num_range] <- sapply(v[, num_range], as.numeric)
     * </pre>
     *
     * @param indices  List of numeric attributes indices
     * @param sb   buffer where the R code will be generated
     */
    private void generateNumericConversion(List<Integer> indices, StringBuilder sb) {
        sb.append("   num_range <- c(");
        Joiner.on(',').appendTo(sb, indices);
        sb.append(")\n");
        sb.append("   v[, num_range] <- sapply(v[, num_range], as.numeric)\n\n");
    }

    /**
     * Generates a new function that saves a model as PMML file.
     *
     * @param rEnvironment The environment code.
     * @param saveFilePath The absolute path to the file where the model will be saved.
     * @return The function code.
     */
    private String generateSaveAsPMMLFunction(String rEnvironment, String saveFilePath, int classIndex) {
        // Major hack to get the actual class index in an R PMML.
        // R's pmml package doesn't honor the trained class index, and always places it
        // as the first element of the data dictionary. Because the package is very
        // limited in its configuration, we've placed the class index in the Application tag
        // <Application name="FOS-R/classindex=2">.
        // This is very, very ugly, but couldn't find a better solution
        String pmmlApplication =  String.format("\"FOS-R/classindex=%d\"", classIndex);

        StringBuilder sb = new StringBuilder();
        sb.append(SAVE_AS_PMML_FUNCTION_PREFIX).append(String.format("%1$s <- function() {\n", rEnvironment));
        sb.append("cat(\"Exporting model to PMML.\\n\")\n");
        sb.append(String.format("modelPMML <- pmml(get(%1$s$modelname, envir=%1s), app.name=%s)\n", rEnvironment, pmmlApplication));
        sb.append(String.format("cat(\"Writing PMML to XML file into\", \"%s\", \"\\n\")\n", saveFilePath));
        sb.append(String.format("saveXML(modelPMML, \"%s\")\n", saveFilePath));
        sb.append("cat(\"Model export to PMML completed.\\n\")\n");
        sb.append("}");

        return sb.toString();
    }

    /**
     * Retrieves a call to the function that saves the model with the given UUID to PMML.
     *
     * @param uuid The UUID of the model to save as PMML.
     * @return A string with the call to the function.
     */
    public String getSaveAsPMMLFunctionCall(UUID uuid) {
        return SAVE_AS_PMML_FUNCTION_PREFIX + String.format("%1$s()", uuid2environment(uuid));
    }

    /**
     * Extract categoricals from the attribute list
     * @param attrs List of attributes
     * @return List of categorical attributes
     */
    static List<CategoricalAttribute> extractCategoricals(List<Attribute> attrs) {
        List<CategoricalAttribute> categoricals = new ArrayList<>();

        for (Attribute attribute : attrs) {
            if(attribute instanceof CategoricalAttribute) {
                CategoricalAttribute categorical = (CategoricalAttribute) attribute;
                categoricals.add(categorical);
            }
        }
        return categoricals;
    }

    /**
     * Append a "X" to variable names if attributes names start with a numbner
     * @param original original variable name
     * @return valid r variable name
     */
    public static String rVariableName(String original) {
        return Character.isDigit(original.charAt(0)) ? "X" + original : original;
    }

    /**
     * Each model will live inside its own enviroment defined by the uuuid.
     * The non alphabetic charts stripped string representation of the uuid will be used as the
     * R environment name
     * @param uuid model uuid
     * @return Enviroment name;
     */
    private String uuid2environment(UUID uuid) {
        // environments cannot start with a number, add a 'x'
        return "x" + uuid.toString().replace("-", "");
    }

    /**
     * Remove a trained model an everything that goes along with it by deleting the enviroment
     *
     * @param modelId model id to delete
     * @throws FOSException
     */
    public void removeModel(UUID modelId) throws FOSException {
        rserve.eval("rm(" + uuid2environment(modelId) + ")");
    }
}


