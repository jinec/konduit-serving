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

package ai.konduit.serving.pipeline;

import ai.konduit.serving.config.ParallelInferenceConfig;
import ai.konduit.serving.config.SchemaType;
import ai.konduit.serving.model.ModelConfig;
import ai.konduit.serving.config.ServingConfig;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.datavec.api.transform.TransformProcess;
import org.datavec.api.transform.schema.Schema;

import java.util.HashMap;

@SuperBuilder
@Data
@NoArgsConstructor
public class ModelPipelineStep extends PipelineStep {

    private ModelConfig modelConfig;

    @Builder.Default
    private ParallelInferenceConfig parallelInferenceConfig = ParallelInferenceConfig.defaultConfig();

    private NormalizationConfig normalizationConfig;

    public ModelPipelineStep(ModelConfig modelConfig) {
        this.modelConfig = modelConfig;
    }

    public ModelPipelineStep input(String[] columnNames, SchemaType[] types) throws Exception {
        return (ModelPipelineStep) super.input("default", columnNames, types);
    }

    public ModelPipelineStep output(String[] columnNames, SchemaType[] types) throws Exception {
        return (ModelPipelineStep) super.output("default", columnNames, types);
    }

    @Override
    public ModelPipelineStep input(Schema inputSchema) throws Exception {
        return (ModelPipelineStep) super.input("default", inputSchema);
    }

    @Override
    public ModelPipelineStep output(Schema outputSchema) throws Exception {
        return (ModelPipelineStep) super.output("default", outputSchema);
    }


    public ModelPipelineStep input(String inputName, String[] columnNames, SchemaType[] types) throws Exception {
        return (ModelPipelineStep) super.input(inputName, columnNames, types);
    }

    public ModelPipelineStep output(String outputName, String[] columnNames, SchemaType[] types) throws Exception {
        return (ModelPipelineStep) super.output(outputName, columnNames, types);
    }

    @Override
    public ModelPipelineStep input(String inputName, Schema inputSchema) throws Exception {
        return (ModelPipelineStep) super.input(inputName, inputSchema);
    }

    @Override
    public ModelPipelineStep output(String outputName, Schema outputSchema) throws Exception {
        return (ModelPipelineStep) super.output(outputName, outputSchema);
    }

    @Override
    public String pipelineStepClazz() {
        return "ai.konduit.serving.pipeline.steps.InferenceExecutionerPipelineStepRunner";
    }
}
