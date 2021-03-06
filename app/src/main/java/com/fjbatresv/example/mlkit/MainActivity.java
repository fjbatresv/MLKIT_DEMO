// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.fjbatresv.example.mlkit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Trace;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.appindexing.AndroidAppUri;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.custom.FirebaseModelDataType;
import com.google.firebase.ml.custom.FirebaseModelInputOutputOptions;
import com.google.firebase.ml.custom.FirebaseModelInputs;
import com.google.firebase.ml.custom.FirebaseModelInterpreter;
import com.google.firebase.ml.custom.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseModelOptions;
import com.google.firebase.ml.custom.FirebaseModelOutputs;
import com.google.firebase.ml.custom.model.FirebaseCloudModelSource;
import com.google.firebase.ml.custom.model.FirebaseLocalModelSource;
import com.google.firebase.ml.custom.model.FirebaseModelDownloadConditions;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentTextRecognizer;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.google.firebase.perf.FirebasePerformance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import io.fabric.sdk.android.Fabric;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAPTURE_IMAGE = 1000;
    private ImageView mImageView;
    private Button mTextButton;
    private Button mFaceButton;
    private Button mCloudButton;
    private Button mRunCustomModelButton;
    private Bitmap mSelectedImage;
    private GraphicOverlay mGraphicOverlay;
    private Button btnImage;
    // Max width (portrait mode)
    private Integer mImageMaxWidth;
    // Max height (portrait mode)
    private Integer mImageMaxHeight;
    /**
     * Name of the model file hosted with Firebase.
     */
    private static final String HOSTED_MODEL_NAME = "cloud_model_1";
    private static final String LOCAL_MODEL_ASSET = "mobilenet_v1_1.0_224_quant.tflite";
    /**
     * Name of the label file stored in Assets.
     */
    private static final String LABEL_PATH = "labels.txt";
    /**
     * Number of results to show in the UI.
     */
    private static final int RESULTS_TO_SHOW = 3;
    /**
     * Dimensions of inputs.
     */
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;
    private static final int DIM_IMG_SIZE_X = 224;
    private static final int DIM_IMG_SIZE_Y = 224;
    /**
     * Labels corresponding to the output of the vision model.
     */
    private List<String> mLabelList;

    private final PriorityQueue<Map.Entry<String, Float>> sortedLabels =
            new PriorityQueue<>(
                    RESULTS_TO_SHOW,
                    new Comparator<Map.Entry<String, Float>>() {
                        @Override
                        public int compare(Map.Entry<String, Float> o1, Map.Entry<String, Float>
                                o2) {
                            return (o1.getValue()).compareTo(o2.getValue());
                        }
                    });
    /* Preallocated buffers for storing image data. */
    private final int[] intValues = new int[DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y];

    private FirebaseModelInterpreter mInterpreter;
    private FirebaseModelInputOutputOptions mDataOptions;
    FirebaseAnalytics analytics;
    FirebasePerformance performance;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.analytics = FirebaseAnalytics.getInstance(this);
        this.performance = FirebasePerformance.getInstance();
        Fabric.with(this, new Crashlytics());
        mImageView = findViewById(R.id.image_view);
        btnImage = findViewById(R.id.btn_image);
        mTextButton = findViewById(R.id.button_text);
        mFaceButton = findViewById(R.id.button_face);
        mCloudButton = findViewById(R.id.button_cloud_text);
        mRunCustomModelButton = findViewById(R.id.button_run_custom_model);
        btnImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                startActivityForResult(Intent.createChooser(intent, "Elige una foto"), REQUEST_CAPTURE_IMAGE);
            }
        });
        mGraphicOverlay = findViewById(R.id.graphic_overlay);
        mTextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTextRecognition();
            }
        });
        mFaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runFaceContourDetection();
            }
        });
        mCloudButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runCloudTextRecognition();
            }
        });
        mRunCustomModelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runModelInference();
            }
        });
        Spinner dropdown = findViewById(R.id.spinner);
        String[] items = new String[]{"Test Image 1 (Text)", "Test Image 2 (Text)", "Test Image 3" +
                " (Face)", "Test Image 4 (Object)", "Test Image 5 (Object)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout
                .simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);
        dropdown.setOnItemSelectedListener(this);
        initCustomModel();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CAPTURE_IMAGE) {
            mGraphicOverlay.clear();
            if (resultCode == RESULT_OK) {
                if (data != null && data.getExtras() != null) {
                    mImageView.setImageURI(data.getData());
                    Bundle bundle = new Bundle();
                    bundle.putString("URI", data.getData().toString());
                    analytics.logEvent("SELECT_IMAGE", bundle);
                    try {
                        mSelectedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
                        resizeBitmap();
                    } catch (IOException e) {
                        showToast("Selected image not setted");
                        Crashlytics.log("Image not resized");
                        e.printStackTrace();
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void runTextRecognition() {
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(mSelectedImage);
        FirebaseVisionTextRecognizer recognizer = FirebaseVision.getInstance()
                .getOnDeviceTextRecognizer();
        Bundle bundle = new Bundle();
        analytics.logEvent("TEXT_RECOGNITION", bundle);
        final com.google.firebase.perf.metrics.Trace trace = performance.newTrace("LOCAL_TEXT_RECOGNITION");
        trace.start();
        mTextButton.setEnabled(false);
        recognizer.processImage(image)
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText texts) {
                        mTextButton.setEnabled(true);
                        trace.stop();
                        processTextRecognitionResult(texts);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        mTextButton.setEnabled(true);
                        trace.stop();
                        Crashlytics.log("Local text recognition failed");
                        e.printStackTrace();
                    }
                });
    }

    private void processTextRecognitionResult(FirebaseVisionText texts) {
        List<FirebaseVisionText.TextBlock> blocks = texts.getTextBlocks();
        if (blocks.size() == 0) {
            showToast("No text");
            return;
        }
        mGraphicOverlay.clear();
        for (FirebaseVisionText.TextBlock block : blocks) {
            for (FirebaseVisionText.Line line : block.getLines()) {
                for (FirebaseVisionText.Element element : line.getElements()) {
                    mGraphicOverlay.add(new TextGraphic(mGraphicOverlay, element));
                }
            }
        }
    }

    private void runFaceContourDetection() {
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(mSelectedImage);
        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions.Builder()
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .build();
        analytics.logEvent("LOCAL_CONTOUR_DETECTION", new Bundle());
        final com.google.firebase.perf.metrics.Trace trace = performance.newTrace("LOCAL_CONTOUR_DETECTION");
        trace.start();
        mFaceButton.setEnabled(false);
        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance().getVisionFaceDetector(options);
        detector.detectInImage(image)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionFace> faces) {
                        mFaceButton.setEnabled(true);
                        trace.stop();
                        processFaceContourDetectionResult(faces);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        mFaceButton.setEnabled(true);
                        trace.stop();
                        Crashlytics.log("Local face contour detection failed");
                        e.printStackTrace();
                    }
                });
    }

    private void processFaceContourDetectionResult(List<FirebaseVisionFace> faces) {
        if (faces.size() == 0) {
            showToast("No faces");
            return;
        }
        mGraphicOverlay.clear();
        for (FirebaseVisionFace face : faces) {
            FaceContourGraphic graphic = new FaceContourGraphic(mGraphicOverlay);
            mGraphicOverlay.add(graphic);
            graphic.updateFace(face);
        }
    }

    private void initCustomModel() {
        mLabelList = loadLabelList(this);
        int[] inputDims = {DIM_BATCH_SIZE, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, DIM_PIXEL_SIZE};
        int[] outputDims = {DIM_BATCH_SIZE, mLabelList.size()};
        try {
            mDataOptions = new FirebaseModelInputOutputOptions.Builder()
                    .setInputFormat(0, FirebaseModelDataType.BYTE, inputDims)
                    .setOutputFormat(0, FirebaseModelDataType.BYTE, outputDims)
                    .build();
            FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions.Builder()
                    .requireWifi()
                    .build();
            FirebaseLocalModelSource localSource = new FirebaseLocalModelSource.Builder("asset")
                    .setAssetFilePath(LOCAL_MODEL_ASSET)
                    .build();
            FirebaseCloudModelSource cloudSource = new FirebaseCloudModelSource.Builder(HOSTED_MODEL_NAME)
                    .enableModelUpdates(true)
                    .setInitialDownloadConditions(conditions)
                    .setUpdatesDownloadConditions(conditions)
                    .build();
            FirebaseModelManager manager = FirebaseModelManager.getInstance();
            manager.registerLocalModelSource(localSource);
            manager.registerCloudModelSource(cloudSource);
            FirebaseModelOptions modelOptions = new FirebaseModelOptions.Builder()
                    .setCloudModelName(HOSTED_MODEL_NAME)
                    .setLocalModelName("asset")
                    .build();
            mInterpreter = FirebaseModelInterpreter.getInstance(modelOptions);
        } catch (FirebaseMLException ex) {
            showToast("Error setting up model");
            ex.printStackTrace();
        }
    }

    private void runModelInference() {
        if (mInterpreter == null) {
            Log.e(TAG, "Image classifier has not been installed");
            return;
        }
        analytics.logEvent("CUSTOM_MODEL_DETECTION", new Bundle());
        ByteBuffer imgData = convertBitmapToByteBuffer(mSelectedImage, mSelectedImage.getWidth(), mSelectedImage.getHeight());
        try {
            FirebaseModelInputs inputs = new FirebaseModelInputs.Builder()
                    .add(imgData)
                    .build();
            final com.google.firebase.perf.metrics.Trace trace = performance.newTrace("CUSTOM_MODEL_DETECTION");
            trace.start();
            mInterpreter.run(inputs, mDataOptions)
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            trace.stop();
                            Crashlytics.log("CUSTOM_MODEL_FAIL");
                        }
                    })
                    .continueWith(new Continuation<FirebaseModelOutputs, List<String>>() {
                        @Override
                        public List<String> then(@NonNull Task<FirebaseModelOutputs> task) throws Exception {
                            byte[][] labelProbArray = task.getResult().<byte[][]>getOutput(0);
                            List<String> topLabels = getTopLabels(labelProbArray);
                            mGraphicOverlay.clear();
                            trace.stop();
                            mGraphicOverlay.add(new LabelGraphic(mGraphicOverlay, topLabels));
                            return topLabels;
                        }
                    });
        } catch (FirebaseMLException ex) {
            showToast("Error running model inference");
            Crashlytics.log("CUSTOM_MODEL_FAIL");
            ex.printStackTrace();
        }
    }

    private void runCloudTextRecognition() {
        mCloudButton.setEnabled(false);
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(mSelectedImage);
        analytics.logEvent("CLOUD_TEXT_RECOGNITION", new Bundle());
        final com.google.firebase.perf.metrics.Trace trace = performance.newTrace("CLOUD_TEXT_RECOGNITION");
        trace.start();
        FirebaseVisionDocumentTextRecognizer recognizer = FirebaseVision.getInstance()
                .getCloudDocumentTextRecognizer();
        recognizer.processImage(image)
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionDocumentText>() {
                    @Override
                    public void onSuccess(FirebaseVisionDocumentText text) {
                        mCloudButton.setEnabled(true);
                        trace.stop();
                        processCloudTextRecognitionResult(text);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        mCloudButton.setEnabled(true);
                        trace.stop();
                        Crashlytics.log("CLOUD_TEXT_FAILED");
                        e.printStackTrace();
                    }
                });
    }

    private void processCloudTextRecognitionResult(FirebaseVisionDocumentText text) {
        if (text == null) {
            showToast("No text");
            return;
        }
        mGraphicOverlay.clear();
        for (FirebaseVisionDocumentText.Block block : text.getBlocks()) {
            for (FirebaseVisionDocumentText.Paragraph paragraph : block.getParagraphs()) {
                for (FirebaseVisionDocumentText.Word word : paragraph.getWords()) {
                    mGraphicOverlay.add(new CloudTextGraphic(mGraphicOverlay, word));
                }
            }
        }
    }

    /**
     * Gets the top labels in the results.
     */
    private synchronized List<String> getTopLabels(byte[][] labelProbArray) {
        for (int i = 0; i < mLabelList.size(); ++i) {
            sortedLabels.add(
                    new AbstractMap.SimpleEntry<>(mLabelList.get(i), (labelProbArray[0][i] &
                            0xff) / 255.0f));
            if (sortedLabels.size() > RESULTS_TO_SHOW) {
                sortedLabels.poll();
            }
        }
        List<String> result = new ArrayList<>();
        final int size = sortedLabels.size();
        for (int i = 0; i < size; ++i) {
            Map.Entry<String, Float> label = sortedLabels.poll();
            result.add(label.getKey() + ":" + label.getValue());
        }
        Log.d(TAG, "labels: " + result.toString());
        return result;
    }

    /**
     * Reads label list from Assets.
     */
    private List<String> loadLabelList(Activity activity) {
        List<String> labelList = new ArrayList<>();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(activity.getAssets().open
                             (LABEL_PATH)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labelList.add(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read label list.", e);
        }
        return labelList;
    }

    /**
     * Writes Image data into a {@code ByteBuffer}.
     */
    private synchronized ByteBuffer convertBitmapToByteBuffer(
            Bitmap bitmap, int width, int height) {
        ByteBuffer imgData =
                ByteBuffer.allocateDirect(
                        DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y,
                true);
        imgData.rewind();
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0,
                scaledBitmap.getWidth(), scaledBitmap.getHeight());
        // Convert the image to int points.
        int pixel = 0;
        for (int i = 0; i < DIM_IMG_SIZE_X; ++i) {
            for (int j = 0; j < DIM_IMG_SIZE_Y; ++j) {
                final int val = intValues[pixel++];
                imgData.put((byte) ((val >> 16) & 0xFF));
                imgData.put((byte) ((val >> 8) & 0xFF));
                imgData.put((byte) (val & 0xFF));
            }
        }
        return imgData;
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    // Functions for loading images from app assets.

    // Returns max image width, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private Integer getImageMaxWidth() {
        if (mImageMaxWidth == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxWidth = mImageView.getWidth();
        }

        return mImageMaxWidth;
    }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private Integer getImageMaxHeight() {
        if (mImageMaxHeight == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxHeight =
                    mImageView.getHeight();
        }

        return mImageMaxHeight;
    }

    // Gets the targeted width / height.
    private Pair<Integer, Integer> getTargetedWidthHeight() {
        int targetWidth;
        int targetHeight;
        int maxWidthForPortraitMode = getImageMaxWidth();
        int maxHeightForPortraitMode = getImageMaxHeight();
        targetWidth = maxWidthForPortraitMode;
        targetHeight = maxHeightForPortraitMode;
        return new Pair<>(targetWidth, targetHeight);
    }

    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
        mGraphicOverlay.clear();
        switch (position) {
            case 0:
                mSelectedImage = getBitmapFromAsset(this, "Please_walk_on_the_grass.jpg");
                break;
            case 1:
                // Whatever you want to happen when the thrid item gets selected
                mSelectedImage = getBitmapFromAsset(this, "nl2.jpg");
                break;
            case 2:
                // Whatever you want to happen when the thrid item gets selected
                mSelectedImage = getBitmapFromAsset(this, "grace_hopper.jpg");
                break;
            case 3:
                // Whatever you want to happen when the thrid item gets selected
                mSelectedImage = getBitmapFromAsset(this, "tennis.jpg");
                break;
            case 4:
                // Whatever you want to happen when the thrid item gets selected
                mSelectedImage = getBitmapFromAsset(this, "mountain.jpg");
                break;
        }
        if (mSelectedImage != null) {
            resizeBitmap();
        }
    }

    private void resizeBitmap() {
        // Get the dimensions of the View
        Pair<Integer, Integer> targetedSize = getTargetedWidthHeight();

        int targetWidth = targetedSize.first;
        int maxHeight = targetedSize.second;

        // Determine how much to scale down the image
        float scaleFactor =
                Math.max(
                        (float) mSelectedImage.getWidth() / (float) targetWidth,
                        (float) mSelectedImage.getHeight() / (float) maxHeight);

        Bitmap resizedBitmap =
                Bitmap.createScaledBitmap(
                        mSelectedImage,
                        (int) (mSelectedImage.getWidth() / scaleFactor),
                        (int) (mSelectedImage.getHeight() / scaleFactor),
                        true);

        mImageView.setImageBitmap(resizedBitmap);
        mSelectedImage = resizedBitmap;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing
    }

    public static Bitmap getBitmapFromAsset(Context context, String filePath) {
        AssetManager assetManager = context.getAssets();

        InputStream is;
        Bitmap bitmap = null;
        try {
            is = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bitmap;
    }
}
