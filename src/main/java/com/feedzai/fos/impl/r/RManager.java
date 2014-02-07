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
import com.feedzai.fos.common.validation.NotBlank;
import com.feedzai.fos.common.validation.NotNull;
import com.feedzai.fos.impl.r.config.RManagerConfig;
import com.feedzai.fos.impl.r.config.RModelConfig;
import com.feedzai.fos.impl.r.rserve.FosRserve;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static com.feedzai.fos.impl.r.RScorer.rVariableName;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class provides a R implementation of a FOS Manager
 *
 * @since 1.0.2
 * @author miguel.duarte
 */
public class RManager implements Manager {
    /** R Manager logger */
    private final static Logger logger = LoggerFactory.getLogger(RManager.class);

    /** Handle for the RServer daemon */
    private final FosRserve rserve;
    /** Map that stores RModel configurations for each configured model */
    private Map<UUID, RModelConfig> modelConfigs = new HashMap<>();

    /** Manager configuration */
    private RManagerConfig rManagerConfig;

    /** Reference for an R scorer */
    private RScorer rScorer;


    /**
     * Create a new manager from the given configuration.
     * <p/> Will lookup any headers files and to to instantiate the model.
     * <p/> If a model fails, a log is produced but loading other models will continue (no exception is thrown).
     *
     * @param rManagerConfig the manager configuration
     */
    public RManager(RManagerConfig rManagerConfig) throws FOSException {
        checkNotNull(rManagerConfig, "Manager config cannot be null");

        this.rManagerConfig = rManagerConfig;
        this.rserve = new FosRserve();

        this.rScorer = new RScorer(rserve);
    }

    /**
     * Persists the model to disk.
     *
     * @param id    the id of the model
     * @param model the serialized classifier
     * @return the File where the model was written
     * @throws java.io.IOException if saving to disk was not possible
     */
    private File createModelFile(UUID id,byte[] model) throws IOException {
        File file = File.createTempFile(id.toString(), ".model", modelConfigs.get(id).getModel());
        FileUtils.writeByteArrayToFile(file, model);
        return file;
    }

    @Override
    public synchronized UUID addModel(ModelConfig config,byte[] model) throws FOSException {
        try {
            UUID uuid = getUuid(config);

            File file = createModelFile(uuid, model);

            RModelConfig rModelConfig = new RModelConfig(config, rManagerConfig);
            rModelConfig.setId(uuid);
            rModelConfig.setModel(file);

            modelConfigs.put(uuid, rModelConfig);
            rScorer.addOrUpdate(rModelConfig);


            return uuid;
        } catch (IOException e) {
            throw new FOSException(e);
        }
    }

    /**
     * Obtain model UUID from ModelConfig if defined or generate a new random uuid
     * @param config Model Configuration
     * @return new Model UUID
     * @throws FOSException
     */
    private UUID getUuid(ModelConfig config) throws FOSException {
        String suuid = config.getProperty("UUID");
        UUID uuid;
        if (suuid == null) {
            uuid = UUID.randomUUID();
        } else {
            uuid = UUID.fromString(suuid);
        }
        return uuid;
    }

    @Override
    public synchronized UUID addModel(ModelConfig config, @NotBlank String localFileName) throws FOSException {
        UUID uuid = getUuid(config);

        RModelConfig rModelConfig = new RModelConfig(config, rManagerConfig);
        rModelConfig.setId(uuid);
        rModelConfig.setModel(new File(localFileName));

        modelConfigs.put(uuid, rModelConfig);
        rScorer.addOrUpdate(rModelConfig);

        return uuid;
    }

    @Override
    public synchronized void removeModel(UUID modelId) throws FOSException {
        RModelConfig rModelConfig = modelConfigs.remove(modelId);
        rScorer.removeModel(modelId);

        // delete the header & model  file (or else it will be picked up on the next restart)
        rModelConfig.getHeader().delete();
        rModelConfig.getModel().delete();
    }

    @Override
    public synchronized void reconfigureModel(UUID modelId,ModelConfig modelConfig) throws FOSException {
        RModelConfig rModelConfig = this.modelConfigs.get(modelId);
        rModelConfig.update(modelConfig);

        rScorer.addOrUpdate(rModelConfig);
    }

    @Override
    public synchronized void reconfigureModel(UUID modelId,ModelConfig modelConfig,byte[] model) throws FOSException {
        throw new FOSException("Not implemented for R");
    }

    @Override
    public synchronized void reconfigureModel(UUID modelId,ModelConfig modelConfig, @NotBlank String localFileName) throws FOSException {
        File file = new File(localFileName);

        RModelConfig rModelConfig = this.modelConfigs.get(modelId);
        rModelConfig.update(modelConfig);
        rModelConfig.setModel(file);

        rScorer.addOrUpdate(rModelConfig);
    }

    @Override
    @NotNull
    public synchronized Map<UUID, ModelConfig> listModels() {
        Map<UUID, ModelConfig> result = new HashMap<>(modelConfigs.size());
        for (Map.Entry<UUID, RModelConfig> entry : modelConfigs.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getModelConfig());
        }

        return result;
    }

    @Override
    @NotNull
    public RScorer getScorer() {
        return rScorer;
    }

    @Override
    public synchronized UUID trainAndAdd(ModelConfig config,List<Object[]> instances) throws FOSException {
        try {
            File instanceFile = writeInstancesToTempFile(instances, config.getAttributes());
            config.setProperty(RModelConfig.MODEL_SAVE_PATH, instanceFile.getParent());
            trainFile(config, instanceFile.getAbsolutePath());

            return addModel(config, (new File(instanceFile.getParent(), instanceFile.getName() + ".model").getAbsolutePath()));
        } catch (IOException e) {
           throw new FOSException(e);
        }
    }

    /**
     * Dump a training instances lists into a temporary file
     *
     * @param instances training instances list
     * @return Temporary file with the dumped training instances
     * @throws IOException
     */
    private File writeInstancesToTempFile(List<Object[]> instances, List<Attribute> attributeList) throws IOException {
        File instanceFile = File.createTempFile("fosrtraining", ".arff");

        PrintWriter pw = new PrintWriter(new FileOutputStream(instanceFile));

        pw.println("% FOS generated ARFF file");
        pw.println("@relation fosrelation");
        pw.println("");

        for(Attribute attribute : attributeList) {
            pw.print("@attribute " + rVariableName(attribute.getName()) + " ");
            if(attribute instanceof NumericAttribute) {
                pw.println("REAL");
            } else if( attribute instanceof  CategoricalAttribute ) {
                CategoricalAttribute cat = (CategoricalAttribute) attribute;
                List<String> values = new ArrayList(cat.getCategoricalInstances());
                values.remove(cat.getUnknownReplacementIndex());
                pw.print("{ '");
                pw.print(Joiner.on("', '").join(values));
                pw.println("'}");
            }
        }
        pw.println();
        pw.println("@data");
        // Dump instances to file
        for (Object[] instance : instances) {
            pw.println(Joiner.on(',').join(instance));
        }
        pw.close();
        return instanceFile;
    }

    @Override
    public synchronized UUID trainAndAddFile(ModelConfig config,String path) throws FOSException {
        trainFile(config, path);
        return addModel(config, path + ".model");
    }


    @Override
    public byte[] train(ModelConfig config,List<Object[]> instances) throws FOSException {
        try {
            File instanceFile = writeInstancesToTempFile(instances, config.getAttributes());
            return trainFile(config, instanceFile.getAbsolutePath());
        } catch (IOException e) {
            throw new FOSException(e);
        }
    }

    /**
     * Generate R boilerplate code to train a model. By default it will use a build in implementation using random
     * randomForest. Another algorithm can be used by overriding <code>RModelConfig.TRAIN_FILE</code> and
     * <code>RModelConfig.TRAIN_FUNCTION</code>.
     *
     * Sample generated code
     * <pre>
     *    headersfile <- '/tmp/fosrtraining8499205938185291252.instances.header'
     *    instancesfile <- '/tmp/fosrtraining8499205938185291252.instances'
     *    class.name <- 'class'
     *
     *    categorical.features <- c(
     *    'A1',
     *    'A4',
     *    'A5',
     *    'A6',
     *    'A7',
     *    'A9',
     *    'A10',
     *    'A12',
     *    'A13',
     *    'class')
     *    modelsavepath <- '/tmp/fosrtraining8499205938185291252.instances.model'
     *    trainRmodel()
     * </pre>
     *
     *
     * @param config    the model configuration
     * @param path File with the training instances
     * @return
     * @throws FOSException
     */
    @Override
    public byte[] trainFile(ModelConfig config, String path) throws FOSException {
        String trainFile = config.getProperty(RModelConfig.TRAIN_FILE);
        String trainFunction = config.getProperty(RModelConfig.TRAIN_FUNCTION);
        if (trainFunction == null) {
               trainFunction = RModelConfig.BUILT_IN_TRAIN_FUNCTION;
        }

        String trainArguments = config.getProperty(RModelConfig.TRAIN_FUNCTION_ARGUMENTS);
        String trainScript = null;

        try {
            if (trainFile != null)  {
                trainScript = Files.toString(new File(trainFile), Charsets.UTF_8);
            }

            String libraries = config.getProperty(RModelConfig.LIBRARIES);
            for(String library : libraries.trim().split(",")) {
                library = library.trim();
                if(library.length() > 0) {
                    rserve.eval(String.format("require(%1s)", library));
                }
            }

            // eval optional train script
            if(trainScript != null) {
                rserve.eval(trainScript);
            }

            List<Attribute> attributes = config.getAttributes();

            File modelSaveFile = new File(config.getProperty(RModelConfig.MODEL_SAVE_PATH),
                                         (new File(path).getName()) + ".model");

            config.setProperty(RModelConfig.MODEL_FILE, modelSaveFile.getAbsolutePath());

            Attribute modelClass = attributes.get(config.getIntProperty(RModelConfig.CLASS_INDEX));

            // load training data
            rserve.eval(String.format("train.data <- read.arff('%s')", path));
            rserve.eval(String.format("classfn <- as.formula('%s ~ .')", rVariableName(modelClass.getName())));
            rserve.eval(String.format("model <- %s(formula = classfn, data = train.data%s)",
                                      trainFunction,
                                      trainArguments != null ? ", " + trainArguments : ""));

            rserve.eval(String.format("save(model, file = '%1s')", modelSaveFile.getAbsolutePath()));

            return Files.toByteArray(modelSaveFile);

        } catch(Throwable e) {
            throw new FOSException(e);
        }

    }

    /**
     * Will save the configuration to file.
     *
     * @throws FOSException when there are IO problems writing the configuration to file
     */
    @Override
    public synchronized void close() throws FOSException {
    }

    @Override
    public void save(UUID uuid, String savepath) throws FOSException {
        try {
            File source = modelConfigs.get(uuid).getModel();
            File destination = new File(savepath);
            Files.copy(source, destination);
        } catch (Exception e) {
            String msg = "Unable to save model " + uuid + " to " + savepath;
            logger.error(msg, e);
            throw new FOSException(msg);
        }
    }
}
