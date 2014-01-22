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
import com.feedzai.fos.api.config.FosConfig;
import com.feedzai.fos.impl.r.config.RManagerConfig;
import com.feedzai.fos.impl.r.config.RModelConfig;
import com.feedzai.fos.impl.r.rserve.FosRserve;
import com.google.common.collect.ImmutableList;
import org.apache.commons.configuration.BaseConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * This test uses a R manager to train a R model using a random Forest and persist it.
 * The model is then loaded and scored against a test instance
 * @author miguel.duarte
 * @since 1.0.2
 */
public class RIntegrationTest {

    static FosRserve rserve;

    @BeforeClass
    public static void init() throws FOSException {
        rserve = new FosRserve();
    }
    @Test
    public void addModel() throws Exception {

        BaseConfiguration configuration = new BaseConfiguration();
        Map<String, String> properties = new HashMap<>();
        List<Attribute> attributes = getAttributes();


        String suuid = "d9556c14-97f1-40f6-9514-f6fb339474af";
        UUID uuid = UUID.fromString(suuid);

        ModelConfig modelConfig = new ModelConfig(attributes, properties);
        modelConfig.setProperty("UUID", suuid);

        modelConfig.setProperty(RModelConfig.LIBRARIES, "randomForest");

        modelConfig.setProperty(RModelConfig.MODEL_SAVE_PATH, getCwd());
        modelConfig.setProperty(RModelConfig.CLASS_INDEX, Integer.valueOf(attributes.size() - 1).toString());

        configuration.setProperty(FosConfig.FACTORY_NAME, RManagerFactory.class.getName());

        FosConfig config = new FosConfig(configuration);

        RManagerConfig rManagerConfig = new RManagerConfig(config);

        RManager rManager = new RManager(rManagerConfig);
        rManager.trainFile(modelConfig, getCwd() + "/test.instances");
        rManager.addModel(modelConfig, getCwd() + "/test.instances.model");


        Scorer scorer = rManager.getScorer();

        Object[] instance = {"b",30.83,0,"u","g","w","v",1.25,"t","t",1,"f","g",202,0};

        List<double[]> result =  scorer.score(ImmutableList.of(uuid), instance);
        assertEquals("Only 1 score expected", 1, result.size());
        assertEquals("2 probabilities (not fraud, fraud)", 2, result.get(0).length);


    }

    @Test
    public void addModelFile() throws Exception {

        BaseConfiguration configuration = new BaseConfiguration();
        Map<String, String> properties = new HashMap<>();
        List<Attribute> attributes = getAttributes();


        ModelConfig modelConfig = new ModelConfig(attributes, properties);

        modelConfig.setProperty(RModelConfig.LIBRARIES, "randomForest");

        modelConfig.setProperty(RModelConfig.MODEL_SAVE_PATH, getCwd());
        modelConfig.setProperty(RModelConfig.CLASS_INDEX, Integer.valueOf(attributes.size() - 1).toString());

        configuration.setProperty(FosConfig.FACTORY_NAME, RManagerFactory.class.getName());

        FosConfig config = new FosConfig(configuration);

        RManagerConfig rManagerConfig = new RManagerConfig(config);

        RManager rManager = new RManager(rManagerConfig);
        UUID uuid = rManager.trainAndAddFile(modelConfig, getCwd() + "/test.instances");



        Scorer scorer = rManager.getScorer();

        Object[] instance = {"b",30.83,0,"u","g","w","v",1.25,"t","t",1,"f","g",202,0};

        List<double[]> result =  scorer.score(ImmutableList.of(uuid), instance);
        assertEquals("Only 1 score expected", 1, result.size());
        assertEquals("2 probabilities (not fraud, fraud)", 2, result.get(0).length);


    }

    @Test
    public void trainandAdd() throws Exception {

        BaseConfiguration configuration = new BaseConfiguration();
        Map<String, String> properties = new HashMap<>();
        List<Attribute> attributes = getAttributes();


        ModelConfig modelConfig = new ModelConfig(attributes, properties);
        modelConfig.setProperty(RModelConfig.LIBRARIES, "randomForest");


        modelConfig.setProperty(RModelConfig.CLASS_INDEX, Integer.valueOf(attributes.size() - 1).toString());

        configuration.setProperty(FosConfig.FACTORY_NAME, RManagerFactory.class.getName());

        FosConfig config = new FosConfig(configuration);

        RManagerConfig rManagerConfig = new RManagerConfig(config);

        RManager rManager = new RManager(rManagerConfig);

        UUID uuid  = rManager.trainAndAdd(modelConfig, getTrainingInstances());



        Scorer scorer = rManager.getScorer();

        Object[] instance = {"b",30.83,0,"u","g","w","v",1.25,"t","t",1,"f","g",202,0};

        List<double[]> result =  scorer.score(ImmutableList.of(uuid), instance);
        assertEquals("Only 1 score expected", 1, result.size());
        assertEquals("2 probabilities (not fraud, fraud)", 2, result.get(0).length);

    }


    private String getCwd() throws IOException {
        return (new File(".")).getCanonicalPath();
    }

    static List<Attribute> getAttributes() {

        String[][] attrs = {
                {"A1", "b","a"},
                {"A2"},
                {"A3"},
                {"A4", "u", "y", "l", "t"},
                {"A5", "g", "p", "gg"},
                {"A6", "c", "d", "cc", "i", "j", "k", "m", "r", "q", "w", "x", "e", "aa", "ff"},
                {"A7", "v", "h", "bb", "j", "n", "z", "dd", "ff", "o"},
                {"A8"},
                {"A9", "t", "f"},
                {"A10", "t", "f"},
                {"A11"},
                {"A12", "t", "f"},
                {"A13", "g", "p", "s"},
                {"A14"},
                {"A15"},
                {"class", "1", "0"}
        };

        List<Attribute> attributes = new ArrayList<>();
        for(String[] attr : attrs) {
            if(attr.length > 1) { // categorical
                attributes.add(new CategoricalAttribute(attr[0], Arrays.asList(attr).subList(1, attr.length)));
            } else {
                attributes.add(new NumericAttribute(attr[0]));
            }

        }
        return attributes;
    }



    private List<Object[]> getTrainingInstances() {
        Object[][] instances = new Object[][] {
                {"b",30.83,0,"u","g","w","v",1.25,"t","t",1,"f","g",202,0,0},
                {"a",58.67,4.46,"u","g","q","h",3.04,"t","t",6,"f","g",43,560,0},
                {"a",24.50,0.5,"u","g","q","h",1.5,"t","f",0,"f","g",280,824,0},
                {"b",27.83,1.54,"u","g","w","v",3.75,"t","t",5,"t","g",100,3,0},
                {"b",20.17,5.625,"u","g","w","v",1.71,"t","f",0,"f","s",120,0,0},
                {"b",32.08,4,"u","g","m","v",2.5,"t","f",0,"t","g",360,0,0},
                {"b",33.17,1.04,"u","g","r","h",6.5,"t","f",0,"t","g",164,31285,0},
                {"a",22.92,11.585,"u","g","cc","v",0.04,"t","f",0,"f","g",80,1349,0},
                {"b",54.42,0.5,"y","p","k","h",3.96,"t","f",0,"f","g",180,314,0},
                {"b",42.50,4.915,"y","p","w","v",3.165,"t","f",0,"t","g",52,1442,0},
                {"b",22.08,0.83,"u","g","c","h",2.165,"f","f",0,"t","g",128,0,0},
                {"b",29.92,1.835,"u","g","c","h",4.335,"t","f",0,"f","g",260,200,0},
                {"a",38.25,6,"u","g","k","v",1,"t","f",0,"t","g",0,0,0},
                {"b",48.08,6.04,"u","g","k","v",0.04,"f","f",0,"f","g",0,2690,0},
                {"a",45.83,10.5,"u","g","q","v",5,"t","t",7,"t","g",0,0,0},
                {"b",36.67,4.415,"y","p","k","v",0.25,"t","t",10,"t","g",320,0,0},
                {"b",28.25,0.875,"u","g","m","v",0.96,"t","t",3,"t","g",396,0,0},
                {"a",23.25,5.875,"u","g","q","v",3.17,"t","t",10,"f","g",120,245,0},
                {"b",21.83,0.25,"u","g","d","h",0.665,"t","f",0,"t","g",0,0,0},
                {"a",19.17,8.585,"u","g","cc","h",0.75,"t","t",7,"f","g",96,0,0},
                {"b",25.00,11.25,"u","g","c","v",2.5,"t","t",17,"f","g",200,1208,0},
                {"b",23.25,1,"u","g","c","v",0.835,"t","f",0,"f","s",300,0,0},
                {"a",47.75,8,"u","g","c","v",7.875,"t","t",6,"t","g",0,1260,0},
                {"a",27.42,14.5,"u","g","x","h",3.085,"t","t",1,"f","g",120,11,0},
                {"a",41.17,6.5,"u","g","q","v",0.5,"t","t",3,"t","g",145,0,0},
                {"a",15.83,0.585,"u","g","c","h",1.5,"t","t",2,"f","g",100,0,0},
                {"a",47.00,13,"u","g","i","bb",5.165,"t","t",9,"t","g",0,0,0},
                {"b",56.58,18.5,"u","g","d","bb",15,"t","t",17,"t","g",0,0,0},
                {"b",57.42,8.5,"u","g","e","h",7,"t","t",3,"f","g",0,0,0},
                {"b",42.08,1.04,"u","g","w","v",5,"t","t",6,"t","g",500,10000,0},
                {"b",29.25,14.79,"u","g","aa","v",5.04,"t","t",5,"t","g",168,0,0},
                {"b",42.00,9.79,"u","g","x","h",7.96,"t","t",8,"f","g",0,0,0},
                {"b",49.50,7.585,"u","g","i","bb",7.585,"t","t",15,"t","g",0,5000,0},
                {"a",36.75,5.125,"u","g","e","v",5,"t","f",0,"t","g",0,4000,0},
                {"a",22.58,10.75,"u","g","q","v",0.415,"t","t",5,"t","g",0,560,0},
                {"b",27.83,1.5,"u","g","w","v",2,"t","t",11,"t","g",434,35,0},
                {"b",27.25,1.585,"u","g","cc","h",1.835,"t","t",12,"t","g",583,713,0},
                {"a",23.00,11.75,"u","g","x","h",0.5,"t","t",2,"t","g",300,551,0},
                {"b",27.75,0.585,"y","p","cc","v",0.25,"t","t",2,"f","g",260,500,0},
                {"b",54.58,9.415,"u","g","ff","ff",14.415,"t","t",11,"t","g",30,300,0},
                {"b",34.17,9.17,"u","g","c","v",4.5,"t","t",12,"t","g",0,221,0},
                {"b",28.92,15,"u","g","c","h",5.335,"t","t",11,"f","g",0,2283,0},
                {"b",29.67,1.415,"u","g","w","h",0.75,"t","t",1,"f","g",240,100,0},
                {"b",39.58,13.915,"u","g","w","v",8.625,"t","t",6,"t","g",70,0,0},
                {"b",56.42,28,"y","p","c","v",28.5,"t","t",40,"f","g",0,15,0},
                {"b",54.33,6.75,"u","g","c","h",2.625,"t","t",11,"t","g",0,284,0},
                {"a",41.00,2.04,"y","p","q","h",0.125,"t","t",23,"t","g",455,1236,0},
                {"b",31.92,4.46,"u","g","cc","h",6.04,"t","t",3,"f","g",311,300,0},
                {"b",41.50,1.54,"u","g","i","bb",3.5,"f","f",0,"f","g",216,0,0},
                {"b",23.92,0.665,"u","g","c","v",0.165,"f","f",0,"f","g",100,0,0},
                {"a",25.75,0.5,"u","g","c","h",0.875,"t","f",0,"t","g",491,0,0},
                {"b",26.00,1,"u","g","q","v",1.75,"t","f",0,"t","g",280,0,0},
                {"b",37.42,2.04,"u","g","w","v",0.04,"t","f",0,"t","g",400,5800,0},
                {"b",34.92,2.5,"u","g","w","v",0,"t","f",0,"t","g",239,200,0},
                {"b",34.25,3,"u","g","cc","h",7.415,"t","f",0,"t","g",0,0,0},
                {"b",23.33,11.625,"y","p","w","v",0.835,"t","f",0,"t","g",160,300,0},
                {"b",23.17,0,"u","g","cc","v",0.085,"t","f",0,"f","g",0,0,0},
                {"b",44.33,0.5,"u","g","i","h",5,"t","f",0,"t","g",320,0,0},
                {"b",35.17,4.5,"u","g","x","h",5.75,"f","f",0,"t","s",711,0,0},
                {"b",43.25,3,"u","g","q","h",6,"t","t",11,"f","g",80,0,0},
                {"b",56.75,12.25,"u","g","m","v",1.25,"t","t",4,"t","g",200,0,0},
                {"b",31.67,16.165,"u","g","d","v",3,"t","t",9,"f","g",250,730,0},
                {"a",23.42,0.79,"y","p","q","v",1.5,"t","t",2,"t","g",80,400,0},
                {"a",20.42,0.835,"u","g","q","v",1.585,"t","t",1,"f","g",0,0,0},
                {"b",26.67,4.25,"u","g","cc","v",4.29,"t","t",1,"t","g",120,0,0},
                {"b",34.17,1.54,"u","g","cc","v",1.54,"t","t",1,"t","g",520,50000,0},
                {"a",36.00,1,"u","g","c","v",2,"t","t",11,"f","g",0,456,0},
                {"b",25.50,0.375,"u","g","m","v",0.25,"t","t",3,"f","g",260,15108,0},
                {"b",19.42,6.5,"u","g","w","h",1.46,"t","t",7,"f","g",80,2954,0},
                {"b",35.17,25.125,"u","g","x","h",1.625,"t","t",1,"t","g",515,500,0},
                {"b",32.33,7.5,"u","g","e","bb",1.585,"t","f",0,"t","s",420,0,1},
                {"a",38.58,5,"u","g","cc","v",13.5,"t","f",0,"t","g",980,0,1},
                {"b",44.25,0.5,"u","g","m","v",10.75,"t","f",0,"f","s",400,0,1},
                {"b",44.83,7,"y","p","c","v",1.625,"f","f",0,"f","g",160,2,1},
                {"b",20.67,5.29,"u","g","q","v",0.375,"t","t",1,"f","g",160,0,1},
                {"b",34.08,6.5,"u","g","aa","v",0.125,"t","f",0,"t","g",443,0,1},
                {"a",19.17,0.585,"y","p","aa","v",0.585,"t","f",0,"t","g",160,0,1},
                {"b",21.67,1.165,"y","p","k","v",2.5,"t","t",1,"f","g",180,20,1},
                {"b",21.50,9.75,"u","g","c","v",0.25,"t","f",0,"f","g",140,0,1},
                {"b",49.58,19,"u","g","ff","ff",0,"t","t",1,"f","g",94,0,1},
                {"a",27.67,1.5,"u","g","m","v",2,"t","f",0,"f","s",368,0,1},
                {"b",39.83,0.5,"u","g","m","v",0.25,"t","f",0,"f","s",288,0,1},
                {"b",27.25,0.625,"u","g","aa","v",0.455,"t","f",0,"t","g",200,0,1},
                {"b",37.17,4,"u","g","c","bb",5,"t","f",0,"t","s",280,0,1},
                {"b",25.67,2.21,"y","p","aa","v",4,"t","f",0,"f","g",188,0,1},
                {"b",34.00,4.5,"u","g","aa","v",1,"t","f",0,"t","g",240,0,1},
                {"a",49.00,1.5,"u","g","j","j",0,"t","f",0,"t","g",100,27,1},
                {"b",62.50,12.75,"y","p","c","h",5,"t","f",0,"f","g",112,0,1},
                {"b",31.42,15.5,"u","g","c","v",0.5,"t","f",0,"f","g",120,0,1},
                {"b",52.33,1.375,"y","p","c","h",9.46,"t","f",0,"t","g",200,100,1},
                {"b",28.75,1.5,"y","p","c","v",1.5,"t","f",0,"t","g",0,225,1},
                {"a",28.58,3.54,"u","g","i","bb",0.5,"t","f",0,"t","g",171,0,1},
                {"b",23.00,0.625,"y","p","aa","v",0.125,"t","f",0,"f","g",180,1,1},
                {"a",22.50,11,"y","p","q","v",3,"t","f",0,"t","g",268,0,1},
                {"a",28.50,1,"u","g","q","v",1,"t","t",2,"t","g",167,500,1},
                {"b",37.50,1.75,"y","p","c","bb",0.25,"t","f",0,"t","g",164,400,1},
                {"b",35.25,16.5,"y","p","c","v",4,"t","f",0,"f","g",80,0,1},
                {"b",18.67,5,"u","g","q","v",0.375,"t","t",2,"f","g",0,38,1},
                {"b",25.00,12,"u","g","k","v",2.25,"t","t",2,"t","g",120,5,1},
                {"b",27.83,4,"y","p","i","h",5.75,"t","t",2,"t","g",75,0,1},
                {"b",54.83,15.5,"u","g","e","z",0,"t","t",20,"f","g",152,130,1},
                {"b",28.75,1.165,"u","g","k","v",0.5,"t","f",0,"f","s",280,0,1},
                {"a",25.00,11,"y","p","aa","v",4.5,"t","f",0,"f","g",120,0,1},
                {"b",40.92,2.25,"y","p","x","h",10,"t","f",0,"t","g",176,0,1},
                {"a",19.75,0.75,"u","g","c","v",0.795,"t","t",5,"t","g",140,5,1},
                {"b",29.17,3.5,"u","g","w","v",3.5,"t","t",3,"t","g",329,0,1},
                {"a",24.50,1.04,"y","p","ff","ff",0.5,"t","t",3,"f","g",180,147,1},
                {"b",24.58,12.5,"u","g","w","v",0.875,"t","f",0,"t","g",260,0,1},
                {"a",33.75,0.75,"u","g","k","bb",1,"t","t",3,"t","g",212,0,1},
                {"b",20.67,1.25,"y","p","c","h",1.375,"t","t",3,"t","g",140,210,1},
                {"a",25.42,1.125,"u","g","q","v",1.29,"t","t",2,"f","g",200,0,1},
                {"b",37.75,7,"u","g","q","h",11.5,"t","t",7,"t","g",300,5,1},
                {"b",52.50,6.5,"u","g","k","v",6.29,"t","t",15,"f","g",0,11202,0},
                {"b",57.83,7.04,"u","g","m","v",14,"t","t",6,"t","g",360,1332,0},
                {"a",20.75,10.335,"u","g","cc","h",0.335,"t","t",1,"t","g",80,50,0},
                {"b",39.92,6.21,"u","g","q","v",0.04,"t","t",1,"f","g",200,300,0},
                {"b",25.67,12.5,"u","g","cc","v",1.21,"t","t",67,"t","g",140,258,0},
                {"a",24.75,12.5,"u","g","aa","v",1.5,"t","t",12,"t","g",120,567,0},
                {"a",44.17,6.665,"u","g","q","v",7.375,"t","t",3,"t","g",0,0,0},
                {"a",23.50,9,"u","g","q","v",8.5,"t","t",5,"t","g",120,0,0},
                {"b",34.92,5,"u","g","x","h",7.5,"t","t",6,"t","g",0,1000,0},
                {"b",47.67,2.5,"u","g","m","bb",2.5,"t","t",12,"t","g",410,2510,0},
                {"b",22.75,11,"u","g","q","v",2.5,"t","t",7,"t","g",100,809,0},
                {"b",34.42,4.25,"u","g","i","bb",3.25,"t","t",2,"f","g",274,610,0},
                {"a",28.42,3.5,"u","g","w","v",0.835,"t","f",0,"f","s",280,0,0},
                {"b",67.75,5.5,"u","g","e","z",13,"t","t",1,"t","g",0,0,0},
                {"b",20.42,1.835,"u","g","c","v",2.25,"t","t",1,"f","g",100,150,0},
                {"a",47.42,8,"u","g","e","bb",6.5,"t","t",6,"f","g",375,51100,0},
                {"b",36.25,5,"u","g","c","bb",2.5,"t","t",6,"f","g",0,367,0},
                {"b",32.67,5.5,"u","g","q","h",5.5,"t","t",12,"t","g",408,1000,0},
                {"b",48.58,6.5,"u","g","q","h",6,"t","f",0,"t","g",350,0,0},
                {"b",39.92,0.54,"y","p","aa","v",0.5,"t","t",3,"f","g",200,1000,0},
                {"b",33.58,2.75,"u","g","m","v",4.25,"t","t",6,"f","g",204,0,0},
                {"a",18.83,9.5,"u","g","w","v",1.625,"t","t",6,"t","g",40,600,0},
                {"a",26.92,13.5,"u","g","q","h",5,"t","t",2,"f","g",0,5000,0},
                {"a",31.25,3.75,"u","g","cc","h",0.625,"t","t",9,"t","g",181,0,0},
                {"a",56.50,16,"u","g","j","ff",0,"t","t",15,"f","g",0,247,0},
                {"b",43.00,0.29,"y","p","cc","h",1.75,"t","t",8,"f","g",100,375,0},
                {"b",22.33,11,"u","g","w","v",2,"t","t",1,"f","g",80,278,0},
                {"b",27.25,1.665,"u","g","cc","h",5.085,"t","t",9,"f","g",399,827,0},
                {"b",32.83,2.5,"u","g","cc","h",2.75,"t","t",6,"f","g",160,2072,0},
                {"b",23.25,1.5,"u","g","q","v",2.375,"t","t",3,"t","g",0,582,0},
                {"a",40.33,7.54,"y","p","q","h",8,"t","t",14,"f","g",0,2300,0},
                {"a",30.50,6.5,"u","g","c","bb",4,"t","t",7,"t","g",0,3065,0},
                {"a",52.83,15,"u","g","c","v",5.5,"t","t",14,"f","g",0,2200,0},
                {"a",46.67,0.46,"u","g","cc","h",0.415,"t","t",11,"t","g",440,6,0},
                {"a",58.33,10,"u","g","q","v",4,"t","t",14,"f","g",0,1602,0},
                {"b",37.33,6.5,"u","g","m","h",4.25,"t","t",12,"t","g",93,0,0},
                {"b",23.08,2.5,"u","g","c","v",1.085,"t","t",11,"t","g",60,2184,0},
                {"b",32.75,1.5,"u","g","cc","h",5.5,"t","t",3,"t","g",0,0,0},
                {"a",21.67,11.5,"y","p","j","j",0,"t","t",11,"t","g",0,0,0},
                {"a",28.50,3.04,"y","p","x","h",2.54,"t","t",1,"f","g",70,0,0},
                {"a",68.67,15,"u","g","e","z",0,"t","t",14,"f","g",0,3376,0},
                {"b",28.00,2,"u","g","k","h",4.165,"t","t",2,"t","g",181,0,0},
                {"b",34.08,0.08,"y","p","m","bb",0.04,"t","t",1,"t","g",280,2000,0},
                {"b",27.67,2,"u","g","x","h",1,"t","t",4,"f","g",140,7544,0},
                {"b",44.00,2,"u","g","m","v",1.75,"t","t",2,"t","g",0,15,0},
                {"b",25.08,1.71,"u","g","x","v",1.665,"t","t",1,"t","g",395,20,0},
                {"b",32.00,1.75,"y","p","e","h",0.04,"t","f",0,"t","g",393,0,0},
                {"a",60.58,16.5,"u","g","q","v",11,"t","f",0,"t","g",21,10561,0},
                {"a",40.83,10,"u","g","q","h",1.75,"t","f",0,"f","g",29,837,0},
                {"b",19.33,9.5,"u","g","q","v",1,"t","f",0,"t","g",60,400,0},
                {"a",32.33,0.54,"u","g","cc","v",0.04,"t","f",0,"f","g",440,11177,0},
                {"b",36.67,3.25,"u","g","q","h",9,"t","f",0,"t","g",102,639,0},
                {"b",37.50,1.125,"y","p","d","v",1.5,"f","f",0,"t","g",431,0,0},
                {"a",25.08,2.54,"y","p","aa","v",0.25,"t","f",0,"t","g",370,0,0},
                {"b",41.33,0,"u","g","c","bb",15,"t","f",0,"f","g",0,0,0},
                {"b",56.00,12.5,"u","g","k","h",8,"t","f",0,"t","g",24,2028,0},
                {"a",49.83,13.585,"u","g","k","h",8.5,"t","f",0,"t","g",0,0,0},
                {"b",22.67,10.5,"u","g","q","h",1.335,"t","f",0,"f","g",100,0,0},
                {"b",27.00,1.5,"y","p","w","v",0.375,"t","f",0,"t","g",260,1065,0},
                {"b",25.00,12.5,"u","g","aa","v",3,"t","f",0,"t","s",20,0,0},
                {"a",26.08,8.665,"u","g","aa","v",1.415,"t","f",0,"f","g",160,150,0},
                {"a",18.42,9.25,"u","g","q","v",1.21,"t","t",4,"f","g",60,540,0},
                {"b",20.17,8.17,"u","g","aa","v",1.96,"t","t",14,"f","g",60,158,0},
                {"b",47.67,0.29,"u","g","c","bb",15,"t","t",20,"f","g",0,15000,0},
                {"a",21.25,2.335,"u","g","i","bb",0.5,"t","t",4,"f","s",80,0,0},
                {"a",20.67,3,"u","g","q","v",0.165,"t","t",3,"f","g",100,6,0},
                {"a",57.08,19.5,"u","g","c","v",5.5,"t","t",7,"f","g",0,3000,0},
                {"a",22.42,5.665,"u","g","q","v",2.585,"t","t",7,"f","g",129,3257,0},
                {"b",48.75,8.5,"u","g","c","h",12.5,"t","t",9,"f","g",181,1655,0},
                {"b",40.00,6.5,"u","g","aa","bb",3.5,"t","t",1,"f","g",0,500,0},
                {"b",40.58,5,"u","g","c","v",5,"t","t",7,"f","g",0,3065,0},
                {"a",28.67,1.04,"u","g","c","v",2.5,"t","t",5,"t","g",300,1430,0},
                {"a",33.08,4.625,"u","g","q","h",1.625,"t","t",2,"f","g",0,0,0},
                {"b",21.33,10.5,"u","g","c","v",3,"t","f",0,"t","g",0,0,0},
                {"b",42.00,0.205,"u","g","i","h",5.125,"t","f",0,"f","g",400,0,0},
                {"b",41.75,0.96,"u","g","x","v",2.5,"t","f",0,"f","g",510,600,0},
                {"b",22.67,1.585,"y","p","w","v",3.085,"t","t",6,"f","g",80,0,0},
                {"b",34.50,4.04,"y","p","i","bb",8.5,"t","t",7,"t","g",195,0,0},
                {"b",28.25,5.04,"y","p","c","bb",1.5,"t","t",8,"t","g",144,7,0},
                {"b",33.17,3.165,"y","p","x","v",3.165,"t","t",3,"t","g",380,0,0},
                {"b",48.17,7.625,"u","g","w","h",15.5,"t","t",12,"f","g",0,790,0},
                {"b",27.58,2.04,"y","p","aa","v",2,"t","t",3,"t","g",370,560,0},
                {"b",22.58,10.04,"u","g","x","v",0.04,"t","t",9,"f","g",60,396,0},
                {"a",24.08,0.5,"u","g","q","h",1.25,"t","t",1,"f","g",0,678,0},
                {"a",41.33,1,"u","g","i","bb",2.25,"t","f",0,"t","g",0,300,0},
                {"a",20.75,10.25,"u","g","q","v",0.71,"t","t",2,"t","g",49,0,0},
                {"b",36.33,2.125,"y","p","w","v",0.085,"t","t",1,"f","g",50,1187,0},
                {"a",35.42,12,"u","g","q","h",14,"t","t",8,"f","g",0,6590,0},
                {"b",28.67,9.335,"u","g","q","h",5.665,"t","t",6,"f","g",381,168,0},
                {"b",35.17,2.5,"u","g","k","v",4.5,"t","t",7,"f","g",150,1270,0},
                {"b",39.50,4.25,"u","g","c","bb",6.5,"t","t",16,"f","g",117,1210,0},
                {"b",39.33,5.875,"u","g","cc","h",10,"t","t",14,"t","g",399,0,0},
                {"b",24.33,6.625,"y","p","d","v",5.5,"t","f",0,"t","s",100,0,0},
                {"b",60.08,14.5,"u","g","ff","ff",18,"t","t",15,"t","g",0,1000,0},
                {"b",23.08,11.5,"u","g","i","v",3.5,"t","t",9,"f","g",56,742,0},
                {"b",26.67,2.71,"y","p","cc","v",5.25,"t","t",1,"f","g",211,0,0},
                {"b",48.17,3.5,"u","g","aa","v",3.5,"t","f",0,"f","s",230,0,0},
                {"b",41.17,4.04,"u","g","cc","h",7,"t","t",8,"f","g",320,0,0},
                {"b",55.92,11.5,"u","g","ff","ff",5,"t","t",5,"f","g",0,8851,0},
                {"b",53.92,9.625,"u","g","e","v",8.665,"t","t",5,"f","g",0,0,0},
                {"a",18.92,9.25,"y","p","c","v",1,"t","t",4,"t","g",80,500,0},
                {"a",50.08,12.54,"u","g","aa","v",2.29,"t","t",3,"t","g",156,0,0},
                {"b",65.42,11,"u","g","e","z",20,"t","t",7,"t","g",22,0,0},
                {"a",17.58,9,"u","g","aa","v",1.375,"t","f",0,"t","g",0,0,0},
                {"a",18.83,9.54,"u","g","aa","v",0.085,"t","f",0,"f","g",100,0,0},
                {"a",37.75,5.5,"u","g","q","v",0.125,"t","f",0,"t","g",228,0,0},
                {"b",23.25,4,"u","g","c","bb",0.25,"t","f",0,"t","g",160,0,0},
                {"b",18.08,5.5,"u","g","k","v",0.5,"t","f",0,"f","g",80,0,0},
                {"a",22.50,8.46,"y","p","x","v",2.46,"f","f",0,"f","g",164,0,0},
                {"b",19.67,0.375,"u","g","q","v",2,"t","t",2,"t","g",80,0,0},
                {"b",22.08,11,"u","g","cc","v",0.665,"t","f",0,"f","g",100,0,0},
                {"b",25.17,3.5,"u","g","cc","v",0.625,"t","t",7,"f","g",0,7059,0},
                {"a",47.42,3,"u","g","x","v",13.875,"t","t",2,"t","g",519,1704,0},
                {"b",33.50,1.75,"u","g","x","h",4.5,"t","t",4,"t","g",253,857,0},
                {"b",27.67,13.75,"u","g","w","v",5.75,"t","f",0,"t","g",487,500,0},
                {"a",58.42,21,"u","g","i","bb",10,"t","t",13,"f","g",0,6700,0},
                {"a",20.67,1.835,"u","g","q","v",2.085,"t","t",5,"f","g",220,2503,0},
                {"b",26.17,0.25,"u","g","i","bb",0,"t","f",0,"t","g",0,0,0},
                {"b",21.33,7.5,"u","g","aa","v",1.415,"t","t",1,"f","g",80,9800,0},
                {"b",42.83,4.625,"u","g","q","v",4.58,"t","f",0,"f","s",0,0,0},
                {"b",38.17,10.125,"u","g","x","v",2.5,"t","t",6,"f","g",520,196,0},
                {"b",20.50,10,"y","p","c","v",2.5,"t","f",0,"f","s",40,0,0},
                {"b",48.25,25.085,"u","g","w","v",1.75,"t","t",3,"f","g",120,14,0},
                {"b",28.33,5,"u","g","w","v",11,"t","f",0,"t","g",70,0,0},
                {"b",18.50,2,"u","g","i","v",1.5,"t","t",2,"f","g",120,300,0},
                {"b",33.17,3.04,"y","p","c","h",2.04,"t","t",1,"t","g",180,18027,0},
                {"b",45.00,8.5,"u","g","cc","h",14,"t","t",1,"t","g",88,2000,0},
                {"a",19.67,0.21,"u","g","q","h",0.29,"t","t",11,"f","g",80,99,0},
                {"b",21.83,11,"u","g","x","v",0.29,"t","t",6,"f","g",121,0,0},
                {"b",40.25,21.5,"u","g","e","z",20,"t","t",11,"f","g",0,1200,0},
                {"b",41.42,5,"u","g","q","h",5,"t","t",6,"t","g",470,0,0},
                {"a",17.83,11,"u","g","x","h",1,"t","t",11,"f","g",0,3000,0},
                {"b",23.17,11.125,"u","g","x","h",0.46,"t","t",1,"f","g",100,0,0},
                {"b",18.17,10.25,"u","g","c","h",1.085,"f","f",0,"f","g",320,13,1},
                {"b",20.00,11.045,"u","g","c","v",2,"f","f",0,"t","g",136,0,1},
                {"b",20.00,0,"u","g","d","v",0.5,"f","f",0,"f","g",144,0,1},
                {"a",20.75,9.54,"u","g","i","v",0.04,"f","f",0,"f","g",200,1000,1},
                {"a",24.50,1.75,"y","p","c","v",0.165,"f","f",0,"f","g",132,0,1},
                {"b",32.75,2.335,"u","g","d","h",5.75,"f","f",0,"t","g",292,0,1},
                {"a",52.17,0,"y","p","ff","ff",0,"f","f",0,"f","g",0,0,1},
                {"a",48.17,1.335,"u","g","i","o",0.335,"f","f",0,"f","g",0,120,1},
                {"a",20.42,10.5,"y","p","x","h",0,"f","f",0,"t","g",154,32,1},
                {"b",50.75,0.585,"u","g","ff","ff",0,"f","f",0,"f","g",145,0,1},
                {"b",17.08,0.085,"y","p","c","v",0.04,"f","f",0,"f","g",140,722,1},
                {"b",18.33,1.21,"y","p","e","dd",0,"f","f",0,"f","g",100,0,1},
                {"a",32.00,6,"u","g","d","v",1.25,"f","f",0,"f","g",272,0,1},
                {"b",59.67,1.54,"u","g","q","v",0.125,"t","f",0,"t","g",260,0,0},
                {"b",18.00,0.165,"u","g","q","n",0.21,"f","f",0,"f","g",200,40,0},
                {"b",32.33,2.5,"u","g","c","v",1.25,"f","f",0,"t","g",280,0,1},
                {"b",18.08,6.75,"y","p","m","v",0.04,"f","f",0,"f","g",140,0,1},
                {"b",38.25,10.125,"y","p","k","v",0.125,"f","f",0,"f","g",160,0,1},
                {"b",30.67,2.5,"u","g","cc","h",2.25,"f","f",0,"t","s",340,0,1},
                {"b",18.58,5.71,"u","g","d","v",0.54,"f","f",0,"f","g",120,0,1},
                {"a",19.17,5.415,"u","g","i","h",0.29,"f","f",0,"f","g",80,484,1},
                {"a",18.17,10,"y","p","q","h",0.165,"f","f",0,"f","g",340,0,1},
                {"b",16.25,0.835,"u","g","m","v",0.085,"t","f",0,"f","s",200,0,1},
                {"b",21.17,0.875,"y","p","c","h",0.25,"f","f",0,"f","g",280,204,1},
                {"b",23.92,0.585,"y","p","cc","h",0.125,"f","f",0,"f","g",240,1,1},
                {"b",17.67,4.46,"u","g","c","v",0.25,"f","f",0,"f","s",80,0,1},
                {"a",16.50,1.25,"u","g","q","v",0.25,"f","t",1,"f","g",108,98,1},
        };
        List<Object[]> instanceList = new ArrayList<>();
        for(Object[] instance : instances) {
            instanceList.add(instance);
        }
        return instanceList;
    }
}


