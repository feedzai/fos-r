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
import com.feedzai.fos.common.validation.NotNull;
import com.feedzai.fos.impl.r.config.RModelConfig;
import com.feedzai.fos.impl.r.rserve.FosRserve;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
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
     * Reference to the backing RServe daemon
     */
    private FosRserve rserve;

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
    public RScorer(FosRserve rserve, String[] rlibraries) throws FOSException {
        this(rserve);
        for (String library : rlibraries) {
            rserve.eval("library(" + library  + ")");
        }
    }



    @Override
    public List<double[]> score(List<UUID> modelIds, Object[] scorables) throws FOSException {
        List<double[]> scores = new ArrayList<>();
        for (UUID uuid : modelIds) {
            StringBuilder sb = new StringBuilder();
            sb.append(uuid2environment(uuid))
              .append("$score(c(");
            for (int i = 0; i != scorables.length - 1; ++i) {
                appendValue(scorables[i], sb);
                sb.append(',');
            }
            appendValue(scorables[scorables.length - 1], sb);

            sb.append("))");

            double[] result = rserve.eval(sb.toString());
            scores.add(result);

        }

        return scores;
    }

    /**
     * Generate the scoring vector in the correct format by quoting strings.
     * All other values will be printed as is
     * @param scorable scorable to be appended
     * @param sb String buffer that contain the genreated string
     */
    private void appendValue(Object scorable, StringBuilder sb) {
        if (scorable instanceof String) {
            sb.append('"')
              .append(scorable)
              .append('"');
        } else {
            sb.append(scorable);
        }
    }

    @Override
    public Map<UUID, double[]> score(Map<UUID, Object[]> modelIdsToScorables) throws FOSException {
        Map<UUID, double[]> scoreMap = new HashMap<>();

        for(UUID uuid : modelIdsToScorables.keySet()) {
            scoreMap.put(uuid, score(ImmutableList.of(uuid), modelIdsToScorables.get(uuid)).get(0));
        }
        return scoreMap;
    }

    @Override
    public void close() throws FOSException {
        for (UUID uuid : uuids) {
            removeModel(uuid);
        }

    }


    @Override
    @NotNull
    public List<double[]> score(UUID modelId, List<Object[]> scorables) throws FOSException {
        checkNotNull(scorables, "List of scorables cannot be null");

        List<double[]> scores = new ArrayList<>(scorables.size());

        for (Object[] scorable : scorables) {
            score(ImmutableList.of(modelId), scorable);
        }


        return scores;
    }

    /**
     * Add or update a Rmodel
     * @param rModelConfig R model configuration
     * @throws FOSException Thrown on invalid configuration
     */
    public void addOrUpdate(RModelConfig rModelConfig) throws FOSException {
        if (rserve != null) {
            rserve = new FosRserve();
        }
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

        List<String> attrNames = fosAttributes2Rnames(attrs);



        rserve.assignStringList("attributes", rEnvironment, attrNames);
        List<CategoricalAttribute> categoricals = extractCategoricals(attrs);

        StringBuilder sb = generateScoringFunction(rEnvironment, attrs, categoricals);

        rserve.eval(sb.toString());

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
     * 38c6beb6d82349eabf8159c4c15b7344$score <- function(v) {
     *    v <- as.data.frame(t(as.matrix(v)))
     *    names(v) <- x38c6beb6d82349eabf8159c4c15b7344$attributes
     *
     *    num_range <- c(2,3,8,11,14,15)
     *    v[, num_range] <- sapply(v[, num_range], as.numeric)
     *
     *    factor_range <- c(1,4,5,6,7,9,10,12,13)
     *    v[, factor_range] <- sapply(v[, factor_range], as.factor)
     *
     *    v['A1'] <- factor(v['A1'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A1)
     *    v['A4'] <- factor(v['A4'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A4)
     *    v['A5'] <- factor(v['A5'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A5)
     *    v['A6'] <- factor(v['A6'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A6)
     *    v['A7'] <- factor(v['A7'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A7)
     *    v['A9'] <- factor(v['A9'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A9)
     *    v['A10'] <- factor(v['A10'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A10)
     *    v['A12'] <- factor(v['A12'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A12)
     *    v['A13'] <- factor(v['A13'], levels = x38c6beb6d82349eabf8159c4c15b7344$model$forest$xlevels$A13)
     *
     *
     *    r <- predict(get(x38c6beb6d82349eabf8159c4c15b7344$modelname, envir=x38c6beb6d82349eabf8159c4c15b7344), v, type ="prob")
     *    r
     * }
     * </pre>
     * @param rEnvironment environment code
     * @param attrs  Fos model attribute list
     * @param categoricals Categorical attributes
     * @return buffer where the R code will be generated
     */
    private StringBuilder generateScoringFunction(String rEnvironment, List<Attribute> attrs, List<CategoricalAttribute> categoricals) throws FOSException {
        StringBuilder sb = new StringBuilder();
        // Generate scoring function preamble
        sb.append(String.format(
                "%1$s$score <- function(v) {\n" +
                "   v <- as.data.frame(t(as.matrix(v)))\n" +
                "   names(v) <- %1$s$attributes\n\n",
                rEnvironment));

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

        generateLevelFactorConversion(rEnvironment, categoricals, sb);

        sb.append(String.format(
                "\n\n" +
                "r <- predict(get(%1$s$modelname, envir=%1s), v, type =\"prob\")\n" +
                "r\n}",
                rEnvironment));
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
            sb.append(String.format(
                    "   v['%1$s'] <- factor(v['%1$s'], levels = %2$s$model$forest$xlevels$%1$s)\n",
                    categoricalAttribute.getName(),
                    rEnvironment));

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
     * Fos variable names must not start with a number,
     * therefore a "X" caracter is preprended to their name
     * @param attrs FOS attribute list
     * @return String with the R varible names
     */
    static List<String> fosAttributes2Rnames(List<? extends Attribute> attrs) {
        return Lists.transform(attrs, new Function<Attribute, String>() {
            @Override
            public String apply(Attribute input) {
                String name = input.getName();
                return Character.isDigit(name.charAt(0)) ? "X" + name : name;
            }
        });
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


