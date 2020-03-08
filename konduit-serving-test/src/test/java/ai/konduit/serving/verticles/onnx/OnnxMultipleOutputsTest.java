/*
 *
 *  * ******************************************************************************
 *  *  * Copyright (c) 2015-2019 Skymind Inc.
 *  *  * Copyright (c) 2019-2020 Konduit AI.
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

package ai.konduit.serving.verticles.onnx;

import ai.konduit.serving.InferenceConfiguration;
import ai.konduit.serving.config.Output;
import ai.konduit.serving.config.ServingConfig;
import ai.konduit.serving.model.ModelConfig;
import ai.konduit.serving.model.ModelConfigType;
import ai.konduit.serving.model.OnnxConfig;
import ai.konduit.serving.pipeline.step.ModelStep;
import ai.konduit.serving.util.image.NativeImageLoader;
import ai.konduit.serving.verticles.BaseVerticleTest;
import ai.konduit.serving.verticles.inference.InferenceVerticle;
import com.jayway.restassured.response.Response;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.io.ClassPathResource;
import org.nd4j.serde.binary.BinarySerde;
import org.datavec.image.data.Image;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.indexer.FloatIndexer;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static ai.konduit.serving.executioner.PipelineExecutioner.convertBatchOutput;
import static com.jayway.restassured.RestAssured.given;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


@RunWith(VertxUnitRunner.class)
@NotThreadSafe
public class OnnxMultipleOutputsTest extends BaseVerticleTest {

    @Override
    public Class<? extends AbstractVerticle> getVerticalClazz() {
        return InferenceVerticle.class;
    }

    @Override
    public Handler<HttpServerRequest> getRequest() {
        return null;
    }

    @Override
    public JsonObject getConfigObject() throws Exception {
	String modelPath = new ClassPathResource("/inference/onnx/facedetector.onnx").getFile().getAbsolutePath();

        ServingConfig servingConfig = ServingConfig.builder()
                .outputDataFormat(Output.DataFormat.NUMPY)
                .httpPort(port)
                .build();

        OnnxConfig modelConfig = OnnxConfig.builder()
                .modelConfigType(
                        ModelConfigType.builder()
                                .modelType(ModelConfig.ModelType.ONNX)
                                .modelLoadingPath(modelPath)
                                .build()
                ).build();

        ModelStep modelPipelineConfig = ModelStep.builder()
                .modelConfig(modelConfig)
                .inputNames(Arrays.asList("input"))
                .outputNames(Arrays.asList("scores", "boxes"))
                .build();


        InferenceConfiguration inferenceConfiguration = InferenceConfiguration.builder()
                .servingConfig(servingConfig)
                .step(modelPipelineConfig)
                .build();

//        return new JsonObject();
	return new JsonObject(inferenceConfiguration.toJson());
    }

    byte[] toPrimitives(Byte[] oBytes)
{
    byte[] bytes = new byte[oBytes.length];

    for(int i = 0; i < oBytes.length; i++) {
        bytes[i] = oBytes[i];
    }

    return bytes;
}

    @Test
    public void runFaceDetector(TestContext testContext) throws Exception {
        NativeImageLoader nativeImageLoader = new NativeImageLoader(240, 320);
        Image image = nativeImageLoader.asImageMatrix(new ClassPathResource("data/1.jpg").getFile());
       
        INDArray contents = image.getImage();
	
	byte[] npyContents = Nd4j.toNpyByteArray(contents);

	File inputFile = temporary.newFile();
        FileUtils.writeByteArrayToFile(inputFile, npyContents);

       	Response response = given().port(port)
                .multiPart("input", inputFile)
		.body(npyContents)
                .post("nd4j/numpy")
                .andReturn();

        assertEquals("Response failed", 200, response.getStatusCode());
    
	File outputFile = temporary.newFile();
        FileUtils.writeByteArrayToFile(outputFile, response.asByteArray());
	java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(outputFile);

    java.util.zip.ZipInputStream zs = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(response.asByteArray()));	

   
    java.util.zip.ZipEntry entry = zs.getNextEntry();
	
    assertEquals(entry.getName(), "scores");    
    System.out.println(entry.getName());


    java.io.InputStream entryStream = zipFile.getInputStream(entry);
    System.out.println(entryStream);
    byte[] bytes = com.google.common.io.ByteStreams.toByteArray(entryStream);
    //java.io.InputStream entryStream = java.util.zip.ZipFile.getInputStream(entry);

    
    java.util.zip.ZipEntry entry2 = zs.getNextEntry();
	
    assertEquals(entry2.getName(), "boxes");    
    System.out.println(entry2.getName());
   
    java.io.InputStream entryStream2 = zipFile.getInputStream(entry2);
    System.out.println(entryStream2);
    byte[] bytes2 = com.google.common.io.ByteStreams.toByteArray(entryStream2);
 
//        Byte[][] bytes = response.extract().as(Byte[][].class);

	//Byte[] bytes = (Byte[])response.path("scores");

//    byte[] bytes = response.getBody().asByteArray();
        INDArray bodyResult = Nd4j.createNpyFromByteArray(bytes);

	System.out.println("1nd out 1st entry" + bodyResult.getFloat(0));
	assert Math.abs(bodyResult.getFloat(0) - 0.002913665) < 1e-6;	
	
	assertArrayEquals(new long[]{1, 17680}, bodyResult.shape());

        INDArray bodyResult2 = Nd4j.createNpyFromByteArray(bytes2);

	System.out.println("2nd out 1st entry" + bodyResult2.getFloat(0));
	assert Math.abs(bodyResult2.getFloat(0) - 0.9539676) < 1e-6;	
	
	assertArrayEquals(new long[]{1, 8840}, bodyResult2.shape());

	//TODO: Fix bug that flips outputs
    }

    @After
    public void after(TestContext context) {
      super.after(context);   
    }

}
