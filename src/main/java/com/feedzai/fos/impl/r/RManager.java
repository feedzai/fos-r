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
import com.google.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            File instanceFile = writeInstancesToTempFile(instances);
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
    private File writeInstancesToTempFile(List<Object[]> instances) throws IOException {
        File instanceFile = File.createTempFile("fosrtraining", ".instances");
        PrintWriter pw = new PrintWriter(new FileOutputStream(instanceFile));
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
            File instanceFile = writeInstancesToTempFile(instances);
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
        String trainScript;

        try {
            if (trainFile == null)  {
                trainFunction  = RModelConfig.BUILT_IN_RANDOM_FOREST_TRAIN_FUNCTION;
                URL url = Resources.getResource(RModelConfig.BUILT_IN_RANDOM_FOREST_TRAIN);
                trainScript = Resources.toString(url, Charsets.UTF_8);
            } else {
                trainScript = Files.toString(new File(trainFile), Charsets.UTF_8);
            }


            rserve.eval(trainScript);

            List<Attribute> attributes = config.getAttributes();

            StringBuilder sb = new StringBuilder();

            List<String> rnames = RScorer.fosAttributes2Rnames(attributes);

            Joiner.on(',').appendTo(sb, rnames);

            File modelSaveFile = new File(config.getProperty(RModelConfig.MODEL_SAVE_PATH),
                                         (new File(path).getName()) + ".model");
            config.setProperty(RModelConfig.MODEL_FILE, modelSaveFile.getAbsolutePath());
            String headerFileName = path + ".header";
            Files.write(sb, new File(headerFileName), Charsets.UTF_8);

            rserve.eval(String.format(
                    "headersfile <- '%1$s'\n" +   // define file path with the column descriptions
                    "instancesfile <- '%2$s'\n" +  // define file path with training instances
                    "class.name <- '%3$s'\n",  // define the name of the attribute that contains the training class
                    headerFileName,
                    path,
                    attributes.get(config.getIntProperty(RModelConfig.CLASS_INDEX)).getName()));
            


            List<CategoricalAttribute> categoricals = RScorer.extractCategoricals(attributes);

            List<String> categoricalNames = RScorer.fosAttributes2Rnames(categoricals);

            defineCategoricals(categoricalNames);

            // define file path that will contained the trained model
            rserve.eval(String.format("modelsavepath <- '%1s'", modelSaveFile.getAbsolutePath()));

            rserve.eval(trainFunction);

            return Files.toByteArray(modelSaveFile);

        } catch(Throwable e) {
            throw new FOSException(e);
        }

    }

    /**
     * Generate a variable with all the categorical fields
     * that will be later used during training
     *
     * @param categoricalNames list with the categorical field names
     * @throws FOSException if the generated code is invalid
     */
    private void defineCategoricals(List<String> categoricalNames) throws FOSException {
        // define the categorical fields
        StringBuilder setCategoricals = new StringBuilder();
        setCategoricals.append("categorical.features <- c(\n" );

        int i = 0;
        for(; i < categoricalNames.size() - 1; ++i) {
            setCategoricals.append("     '")
                           .append(categoricalNames.get(i))
                           .append("', \n");
        }

        setCategoricals.append("     '")
                       .append(categoricalNames.get(i))
                       .append("')");

        rserve.eval(setCategoricals.toString());
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
