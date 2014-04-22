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

package com.feedzai.fos.impl.r.config;

import com.feedzai.fos.api.Attribute;
import com.feedzai.fos.api.FOSException;
import com.feedzai.fos.api.ModelConfig;
import com.feedzai.fos.common.validation.NotNull;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents header that defines the schema of the machine learning model.
 *
 * @author Miguel Duarte (miguel.duarte@gmail.com)
 * @since 1.0.2
 * */
public class RModelConfig {

    // Constants to be used as ModelConfig configuration keys
    /**
     * This key will be used to configure all R libraries that the training/scoring
     * functions depend on. If undefined, built in  build libraries will be used.
     */
    public static final String LIBRARIES = "libraries";

    /**
     * This key will be used to configure R model training function
     * If undefined BUILT_IN_RANDOM_FOREST_TRAIN_FUNCTION will be used
     */
    public static final String TRAIN_FUNCTION = "train.function";

    /**
     * This key will be used to configure the R code to be used during training.
     * if not defined BUILT_IN_RANDOM_FOREST_TRAIN will be used
     */
    public static final String TRAIN_FILE = "train.file";

    /**
     * This key will be used to set the trained model save path
     */
    public static final String MODEL_SAVE_PATH = "model.save.location" ;

    /**
     * This key will contain the classifier index in the attributes list
     */
    public static final String CLASS_INDEX = "classIndex";

    /**
     * This key will contain the saved model name
     */
    public static final String MODEL_FILE =  "model";

    /**
     * This key will contain the path to the saved PMML file.
     */
    public static final String PMML_FILE = "pmml";

    /**
     * This key will contain optional training function arguments
     */
    public static final String TRAIN_FUNCTION_ARGUMENTS = "train.function.arguments";

    /**
     * This key will contain optional arguments to the predict function
     */
    public static final String PREDICT_FUNCTION_ARGUMENTS = "predict.function.arguments";


    /**
     * Predict transform function
     */
    public static final String PREDICT_RESULT_TRANSFORM = "predict.result.transform";

    // End of configuration keys constants

    /**
     * Builtin (Random Forest) train function
     */
    public static final String BUILT_IN_TRAIN_FUNCTION = "randomForest";

    /**
     * Extension for generated PMML files.
     */
    public static final String PMML_FILE_EXTENSION = "pmml";

    /**
     * Extension for generated model files.
     */
    public static final String MODEL_FILE_EXTENSION = "model";

    /**
     * Fos model configuration. Contains the attribute definitions and classifier index.
     */
    private final ModelConfig modelConfig;

    /**
     * RManager configuration
     */
    private final RManagerConfig rManagerConfig;

    /**
     * Reference to the model blob
     */
    private File model;

    /**
     * Reference to the model header file
     */
    private File header;

    /**
     * The property name of the ID of the model.
     */
    public static final String ID = "id";

    private UUID id;

    /**
     * Creates a new model from the given {@code ModelConfig} and {@code RManagerConfig}.
     * <p/>
     * From the {@code ModelConfig.properties} the parameters {@code MODEL_FILE}, {@code ID} and {@code CLASS_INDEX} are looked up.
     * If the {@code CLASS_INDEX} doesn't exist int he {@code ModelConfig}, the default value is used from {@code RManagerConfig}.
     *
     * @param modelConfig       the configuration with {@code MODEL_FILE}, {@code ID} and {@code CLASS_INDEX}
     * @param rManagerConfig the configuration with the default {@code CLASS_INDEX}
     */
    public RModelConfig(ModelConfig modelConfig, RManagerConfig rManagerConfig) throws FOSException {
        checkNotNull(modelConfig, "Model configuration cannot be null");
        checkNotNull(rManagerConfig, "Manager configuration cannot be null");

        this.modelConfig = modelConfig;
        this.rManagerConfig = rManagerConfig;
    }

    /**
     * Gets the header file of the model.
     *
     * @return the header file
     */
    @NotNull
    public File getHeader() {
        return header;
    }

    /**
     * Sets the header file of the model.
     *
     * @param header the header file
     */
    public void setHeader(File header) {
        this.header = header;
    }

    /**
     * Gets the ID of the model.
     *
     * @return the ID of the model
     */
    @NotNull
    public UUID getId() {
        return id;
    }

    /**
     * Sets the ID of the model.
     *
     * @param id the ID
     */
    public void setId(UUID id) {
        this.id = id;

        this.modelConfig.setProperty(ID, id.toString());
    }

    /**
     * Gets the model file of the serialized classifier.
     *
     * @return the model file
     */
    @NotNull
    public File getModel() {
        return model;
    }

    /**
     * Sets the model file of the serialized classifier.
     *
     * @param model the model file
     */
    public void setModel(File model) {
        this.model = model;

        this.modelConfig.setProperty(MODEL_FILE, model.getAbsolutePath());
        this.modelConfig.setProperty(PMML_FILE, model.getAbsolutePath() + "." + PMML_FILE_EXTENSION);
    }

    /**
     * Retrieves the PMML {@link File} location.
     *
     * @return The PMML file location.
     */
    public File getPMMLModel() {
        try {
            return new File(modelConfig.getProperty(PMML_FILE));
        } catch (FOSException e) {
            String path = model.getAbsolutePath() + "." + PMML_FILE_EXTENSION;
            this.modelConfig.setProperty(PMML_FILE, path);
            return new File(path);
        }
    }

    /**
     * Gets the instance fields of this configuration.
     *
     * @return the list of instance fields of this classifier
     */
    @NotNull
    public List<Attribute> getAttributes() {
        return this.modelConfig.getAttributes();
    }


    /**
     * Updates the underlying {@code ModelConfig} using {@code ModelConfig.update}.
     *
     * @param modelConfig the model config with the new settings
     */
    public void update(ModelConfig modelConfig) throws FOSException {
        checkNotNull(modelConfig);
        this.modelConfig.update(modelConfig);
    }


    /**
     * Gets the current and updated model config.
     *
     * @return the underlying model config
     */
    @NotNull
    public ModelConfig getModelConfig() {
        return modelConfig;
    }
}
