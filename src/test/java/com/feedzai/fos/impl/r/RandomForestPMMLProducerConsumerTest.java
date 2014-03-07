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

import com.feedzai.fos.api.Attribute;
import com.feedzai.fos.api.FOSException;
import com.feedzai.fos.api.ModelConfig;
import com.feedzai.fos.api.config.FosConfig;
import com.feedzai.fos.impl.r.config.RManagerConfig;
import com.feedzai.fos.impl.r.config.RModelConfig;
import com.feedzai.fos.impl.r.rserve.FosRserve;
import org.apache.commons.configuration.BaseConfiguration;
import org.dmg.pmml.IOUtil;
import org.dmg.pmml.PMML;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * @author Ricardo Ferreira (ricardo.ferreira@feedzai.com)
 */
public class RandomForestPMMLProducerConsumerTest {

    static FosRserve rserve;

    @BeforeClass
    public static void init() throws FOSException {
        rserve = new FosRserve();
    }

    private ModelConfig setupConfig() {
        Map<String, String> properties = new HashMap<>();
        List<Attribute> attributes = RIntegrationTest.getAttributes();
        ModelConfig modelConfig = new ModelConfig(attributes, properties);
        modelConfig.setProperty(RModelConfig.LIBRARIES, "randomForest, foreign");
        modelConfig.setProperty(RModelConfig.TRAIN_FUNCTION, "randomForest");

        modelConfig.setProperty(RModelConfig.CLASS_INDEX, Integer.valueOf(attributes.size() - 1).toString());

        return modelConfig;
    }

    private RManager setupManager() throws FOSException {
        BaseConfiguration configuration = new BaseConfiguration();

        configuration.setProperty(FosConfig.FACTORY_NAME, RManagerFactory.class.getName());

        FosConfig config = new FosConfig(configuration);

        RManagerConfig rManagerConfig = new RManagerConfig(config);

        return new RManager(rManagerConfig);
    }

    @Test
    public void testUncompressed() throws Exception {
        ModelConfig modelConfig = setupConfig();
        RManager rManager = setupManager();

        UUID uuid  = rManager.trainAndAdd(modelConfig, RIntegrationTest.getTrainingInstances());

        File targetFile = Files.createTempFile("targetPMML", ".xml").toFile();

        // Save the model as PMML and load it.
        rManager.saveAsPMML(uuid, targetFile.getAbsolutePath(), false);

        IOUtil.unmarshal(targetFile);

        targetFile.delete();
    }

    @Test
    public void testCompressed() throws Exception {
        ModelConfig modelConfig = setupConfig();
        RManager rManager = setupManager();

        UUID uuid  = rManager.trainAndAdd(modelConfig, RIntegrationTest.getTrainingInstances());

        File targetFile = Files.createTempFile("targetPMML", ".xml").toFile();

        // Save the model as PMML and load it.
        rManager.saveAsPMML(uuid, targetFile.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(targetFile);
             GZIPInputStream gis = new GZIPInputStream(fis)) {
            IOUtil.unmarshal(gis);
        }

        targetFile.delete();
    }



}
