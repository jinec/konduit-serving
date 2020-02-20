/*
 *
 *  * ******************************************************************************
 *  *  * Copyright (c) 2015-2019 Skymind Inc.
 *  *  * Copyright (c) 2019 Konduit AI.
 *  *  *
 *  *  * This program and the accompanying materials are made available under the
 *  *  * terms of the Apache License, Version 2.0 which is available at
 *  *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *  *
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  *  * License for the specific language governing permissions and limitations
 *  *  * under the License.
 *  *  *
 *  *  * SPDX-License-Identifier: Apache-2.0
 *  *  *****************************************************************************
 *
 *
 */

package ai.konduit.serving.pipeline.steps;

import ai.konduit.serving.executioner.Pipeline;
import ai.konduit.serving.model.PythonConfig;
import ai.konduit.serving.pipeline.PipelineStep;
import ai.konduit.serving.pipeline.step.PythonStep;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.datavec.api.records.Record;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.writable.Writable;
import org.datavec.python.PythonExecutioner;
import org.datavec.python.PythonTransform.PythonTransformBuilder;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Run python code as part of a {@link Pipeline}
 * For configuration options see the
 * {@link PythonStep}.
 * <p>
 * The python pipeline step runner allows a definition of
 * 1 python script per input.
 * An "input" is generally just 1 name of "default"
 * representing 1 python script to be run as part of the pipeline.
 * When running more complex neural network models a python script maybe
 * defined per input where "input" is an individual input name
 * in to a neural network that requires multiple inputs defined
 * in a lower level framework like pytorch or tensorflow.
 * <p>
 * Upon execution a {@link PythonExecutioner}
 * is used underneath for interpreter management, initialization
 * of the python script in memory and the management of the values
 * generated by the pythons script.
 * This runner handles pulling values out of the in memory interepter
 * in python and passes the values along to the next step in a
 * pipline.
 * <p>
 * A common example is transforming numpy arrays with zero copy in to
 * an equivalent {@link org.nd4j.linalg.api.ndarray.INDArray}.
 * This allows for high performance interop in a production pipeline
 * while allowing a user to define extensions or running a model
 * expressed in plain python.
 *
 * @author Adam Gibson
 */
@Slf4j
public class PythonStepRunner extends BaseStepRunner {

    private Map<String, PythonTransform> pythonTransform;

    public PythonStepRunner(PipelineStep pipelineStep) throws Exception {
        super(pipelineStep);
        PythonStep pythonConfig = (PythonStep) pipelineStep;
        pythonTransform = new HashMap<>();

        boolean setPath = false;
        for (Map.Entry<String, PythonConfig> configEntry : pythonConfig.getPythonConfigs().entrySet()) {
            Preconditions.checkState(pipelineStep.hasInputName(configEntry.getKey()),
                    "Invalid input name specified for transform " + configEntry.getKey());
            PythonConfig currConfig = configEntry.getValue();
            if (currConfig.getPythonPath() != null && !setPath) {
                log.info("Over riding python path " + currConfig.getPythonPath());
                System.setProperty(PythonExecutioner.DEFAULT_PYTHON_PATH_PROPERTY, currConfig.getPythonPath());
                setPath = true;
            }

            String code = configEntry.getValue().getPythonCode();
            if (code == null) {
                try {
                    code = FileUtils.readFileToString(new File(currConfig.getPythonCodePath()), Charset.defaultCharset());
                } catch (IOException e) {
                    log.error("Unable to read code from " + currConfig.getPythonCodePath());
                }
                log.info("Resolving code from " + currConfig.getPythonCodePath());
            }

            Preconditions.checkNotNull(code, "No code to run!");
            Preconditions.checkState(!code.isEmpty(), "Code resolved to an empty string!");
            PythonTransformBuilder pythonTransformBuilder = PythonTransform.builder();
            pythonTransformBuilder.code(code)
                    .returnAllInputs(currConfig.isReturnAllInputs())
                    .setupAndRun(currConfig.isSetupAndRun());
            PythonVariables pythonVariables = null;
            if(currConfig.getPythonInputs() != null) {
                pythonVariables = PythonVariables.schemaFromMap(currConfig.getPythonInputs());
                pythonTransformBuilder.inputs(pythonVariables);
                pythonTransformBuilder.inputSchema(schemaForVariables(pythonVariables));
            }

            if(currConfig.getPythonOutputs() != null) {
                PythonVariables outputs = PythonVariables.schemaFromMap(currConfig.getPythonOutputs());
                if(outputs.isEmpty()) {
                    pythonTransformBuilder.outputs(pythonVariables);
                    pythonTransformBuilder.outputSchema(schemaForVariables(pythonVariables));
                }
                else {
                    pythonTransformBuilder.outputs(outputs);
                    pythonTransformBuilder.outputSchema(schemaForVariables(outputs));
                }
            }

            PythonTransform pythonTransform =  pythonTransformBuilder.build();
            this.pythonTransform.put(configEntry.getKey(), pythonTransform);
        }
    }

    protected Schema schemaForVariables(PythonVariables pythonVariables) {
        Schema.Builder schemaBuilder = new Schema.Builder();
        String[] varNames = pythonVariables.getVariables();
        for (String name : varNames) {
            PythonType pyType = pythonVariables.getType(name);
            switch (pyType.getName()) {
                case INT:
                    schemaBuilder.addColumnLong(name);
                    break;
                case FLOAT:
                    schemaBuilder.addColumnDouble(name);
                    break;
                case STR:
                case DICT:
                case LIST:
                    schemaBuilder.addColumnString(name);
                    break;
                case NDARRAY:
                    INDArray arr = pythonVariables.getNDArrayValue(name);
                    if (arr == null)
                        schemaBuilder.addColumnNDArray(name, new long[]{1, 1});
                    else
                        schemaBuilder.addColumnNDArray(name, arr.shape());
                    break;
                case BOOL:
                    schemaBuilder.addColumnBoolean(name);
                    break;
                default:
                    throw new IllegalStateException("Unable to support type " + pyType.getName().name());
            }
        }

        return schemaBuilder.build();
    }

    @Override
    public void close() { }

    @Override
    public Record[] transform(Record[] input) {
        Record[] ret = new Record[input.length];
        for (int i = 0; i < ret.length; i++) {
            if (pythonTransform.containsKey(pipelineStep.inputNameAt(i))) {
                PythonTransform transformProcess = pythonTransform.get(pipelineStep.inputNameAt(i));
                Preconditions.checkState(input[i].getRecord() != null && !input[i].getRecord().isEmpty(), "Record should not be empty!");
                List<Writable> execute = transformProcess.map(input[i].getRecord());
                ret[i] = new org.datavec.api.records.impl.Record(execute, null);
            } else {
                ret[i] = input[i];
            }
        }

        log.debug("Post python transform execution");
        return ret;
    }

    @Override
    public void processValidWritable(Writable writable, List<Writable> record, int inputIndex, Object... extraArgs) {
        throw new UnsupportedOperationException();
    }
}
