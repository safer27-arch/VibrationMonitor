
package com.example.vibrationmonitor;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private TextView currentText;
    private TextView avgText;
    private TextView maxText;
    private TextView minText;
    private TextView alarmText;
    private TextView eventText;
    private TextView locationText;

    private android.location.LocationManager locationManager;
    private android.location.LocationListener locationListener;

    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private boolean hasLocation = false;

    private double eventLatitude = 0.0;
    private double eventLongitude = 0.0;
    private boolean eventHasLocation = false;

    // 자동 사진 촬영
    private android.hardware.camera2.CameraDevice cameraDevice;
    private android.hardware.camera2.CameraCaptureSession cameraSession;
    private android.media.ImageReader imageReader;
    private android.os.HandlerThread cameraThread;
    private android.os.Handler cameraHandler;
    private String cameraId;
    private boolean cameraReady = false;

    private java.io.File photoCaptureTarget;
    private String eventPhotoFileName = null;

    private EditText thresholdInput;
    private VibrationGraph graph;

    private boolean measuring = false;

    private double sum = 0.0;
    private long count = 0;
    private double maxValue = 0.0;
    private double minValue = Double.MAX_VALUE;

    // 최근 3초 데이터
    private final ArrayDeque<DataPoint> preBuffer = new ArrayDeque<>();

    // SPEC 초과 이벤트 데이터
    private final ArrayList<DataPoint> eventBuffer = new ArrayList<>();

    private boolean eventRecording = false;
    private long lastThresholdExceededTime = 0L;

    private long eventStartMs = 0;
    private int eventCount = 0;

    // 중력 제거용 Low Pass Filter
    private float gravityX = 0;
    private float gravityY = 0;
    private float gravityZ = 0;

    private static final float ALPHA = 0.90f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sensorManager =
                (SensorManager) getSystemService(SENSOR_SERVICE);

        accelerometer =
                sensorManager.getDefaultSensor(
                        Sensor.TYPE_ACCELEROMETER
                );

        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 25, 30, 30);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Vibration Monitor");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);

        TextView sensorStatus = new TextView(this);
        sensorStatus.setText(
                accelerometer != null
                        ? "가속도 센서 준비 완료"
                        : "가속도 센서를 찾을 수 없습니다."
        );
        sensorStatus.setTextSize(16);
        sensorStatus.setGravity(Gravity.CENTER);
        sensorStatus.setPadding(0, 5, 0, 10);

        locationText = new TextView(this);
        locationText.setText("GPS : 위치 확인 대기 중");
        locationText.setTextSize(15);
        locationText.setGravity(Gravity.CENTER);
        locationText.setTextColor(Color.DKGRAY);
        locationText.setPadding(0, 4, 0, 10);

        // 실시간 그래프
        graph = new VibrationGraph(this);

        root.addView(
                graph,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        600
                )
        );

        currentText = createValueText(
                "현재값 : 0.000 m/s²"
        );
        avgText = createValueText(
                "평균값 : 0.000 m/s²"
        );
        maxText = createValueText(
                "최대값 : 0.000 m/s²"
        );
        minText = createValueText(
                "최소값 : 0.000 m/s²"
        );

        TextView thresholdLabel =
                createValueText("SPEC / Threshold");

        thresholdInput = new EditText(this);
        float savedSpec = getSharedPreferences("VibrationSettings", MODE_PRIVATE).getFloat("spec", 2.0f);
        thresholdInput.setText(String.valueOf(savedSpec));
        thresholdInput.setTextSize(20);
        thresholdInput.setHint("예: 2.0");

        thresholdInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        alarmText = new TextView(this);
        alarmText.setText("상태 : 정상");
        alarmText.setTextSize(22);
        alarmText.setGravity(Gravity.CENTER);
        alarmText.setTextColor(Color.rgb(0, 130, 0));
        alarmText.setPadding(10, 20, 10, 20);

        eventText = new TextView(this);
        eventText.setText(
                "이벤트 : 0회\n최근 3초 데이터 대기 중"
        );
        eventText.setTextSize(16);
        eventText.setGravity(Gravity.CENTER);
        eventText.setTextColor(Color.DKGRAY);
        eventText.setPadding(10, 10, 10, 20);

        Button specSaveButton = new Button(this);
        specSaveButton.setText("SPEC 저장 / 적용");
        specSaveButton.setOnClickListener(v -> {
            try {
                double value = Double.parseDouble(thresholdInput.getText().toString().trim());
                if (value <= 0) throw new Exception();
                getSharedPreferences("VibrationSettings", MODE_PRIVATE)
                        .edit().putFloat("spec", (float)value).apply();
                graph.setThreshold(value);
                android.widget.Toast.makeText(
                        this,
                        String.format(Locale.US, "SPEC %.2f m/s² 저장 완료", value),
                        android.widget.Toast.LENGTH_SHORT
                ).show();
            } catch (Exception e) {
                float oldValue = getSharedPreferences("VibrationSettings", MODE_PRIVATE)
                        .getFloat("spec", 2.0f);
                thresholdInput.setText(String.valueOf(oldValue));
                android.widget.Toast.makeText(
                        this,
                        "0보다 큰 숫자를 입력하세요.",
                        android.widget.Toast.LENGTH_SHORT
                ).show();
            }
        });

        Button startButton = new Button(this);
        startButton.setText("측정 시작");

        Button stopButton = new Button(this);
        stopButton.setText("측정 중지");

        Button resetButton = new Button(this);
        resetButton.setText("값 초기화");

        Button csvButton = new Button(this);
        csvButton.setText("저장된 CSV 파일");

        Button historyButton = new Button(this);
        historyButton.setText("진동 이력 관리");
        historyButton.setOnClickListener(v -> {
            android.content.Intent intent =
                    new android.content.Intent(this, HistoryActivity.class);
            intent.putExtra("spec", getThreshold());
            startActivity(intent);
        });
        csvButton.setOnClickListener(v -> {
            java.io.File dir = new java.io.File(getExternalFilesDir(null), "VibrationData");
            java.io.File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                new android.app.AlertDialog.Builder(this).setTitle("저장된 CSV 파일").setMessage("저장된 CSV 파일이 없습니다.").setPositiveButton("확인", null).show();
                return;
            }
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            for (java.io.File f : files) if (f.getName().endsWith(".csv")) names.add(f.getName());
            if (names.isEmpty()) {
                new android.app.AlertDialog.Builder(this).setTitle("저장된 CSV 파일").setMessage("저장된 CSV 파일이 없습니다.").setPositiveButton("확인", null).show();
                return;
            }
            java.util.Collections.sort(names, java.util.Collections.reverseOrder());
            new android.app.AlertDialog.Builder(this).setTitle("저장된 CSV 파일").setItems(names.toArray(new String[0]), (dialog, which) -> showCsvGraph(new java.io.File(dir, names.get(which)))).setNegativeButton("닫기", null).show();
        });

        startButton.setOnClickListener(
                v -> startMeasurement()
        );

        stopButton.setOnClickListener(
                v -> stopMeasurement()
        );

        resetButton.setOnClickListener(
                v -> resetValues()
        );

        root.addView(title);
        root.addView(sensorStatus);
        root.addView(locationText);

        // 그래프가 제목 바로 아래 보이도록 순서 조정
        root.removeView(graph);
        root.addView(
                graph,
                3,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        600
                )
        );

        root.addView(currentText);
        root.addView(avgText);
        root.addView(maxText);
        root.addView(minText);
        root.addView(thresholdLabel);
        root.addView(thresholdInput);
        root.addView(specSaveButton);
        root.addView(alarmText);
        root.addView(eventText);
        root.addView(startButton);
        root.addView(stopButton);
        root.addView(resetButton);
        root.addView(csvButton);
        root.addView(historyButton);
        root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), (int)(100 * getResources().getDisplayMetrics().density));

        scroll.addView(root);
        setContentView(scroll);

        setupLocation();
        setupCamera();
    }

    private void setupCamera() {

        if (
                android.os.Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(
                        android.Manifest.permission.CAMERA
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                    new String[] {
                            android.Manifest.permission.CAMERA
                    },
                    1002
            );
            return;
        }

        openEventCamera();
    }

    private void startCameraThread() {

        if (cameraThread != null) return;

        cameraThread =
                new android.os.HandlerThread(
                        "VibrationCamera"
                );

        cameraThread.start();

        cameraHandler =
                new android.os.Handler(
                        cameraThread.getLooper()
                );
    }

    private void openEventCamera() {

        if (cameraDevice != null) return;

        startCameraThread();

        try {
            android.hardware.camera2.CameraManager manager =
                    (android.hardware.camera2.CameraManager)
                            getSystemService(CAMERA_SERVICE);

            cameraId = null;

            for (String id : manager.getCameraIdList()) {

                android.hardware.camera2.CameraCharacteristics c =
                        manager.getCameraCharacteristics(id);

                Integer facing =
                        c.get(
                                android.hardware.camera2.CameraCharacteristics
                                        .LENS_FACING
                        );

                if (
                        facing != null &&
                        facing ==
                                android.hardware.camera2.CameraCharacteristics
                                        .LENS_FACING_BACK
                ) {
                    cameraId = id;
                    break;
                }
            }

            if (cameraId == null) {
                return;
            }

            android.hardware.camera2.CameraCharacteristics c =
                    manager.getCameraCharacteristics(cameraId);

            android.hardware.camera2.params.StreamConfigurationMap map =
                    c.get(
                            android.hardware.camera2.CameraCharacteristics
                                    .SCALER_STREAM_CONFIGURATION_MAP
                    );

            android.util.Size chosen =
                    new android.util.Size(1280, 720);

            if (map != null) {

                android.util.Size[] sizes =
                        map.getOutputSizes(
                                android.graphics.ImageFormat.JPEG
                        );

                if (sizes != null && sizes.length > 0) {

                    chosen = sizes[0];

                    long targetArea = 1280L * 720L;
                    long bestDiff = Long.MAX_VALUE;

                    for (android.util.Size size : sizes) {

                        long area =
                                (long) size.getWidth() *
                                (long) size.getHeight();

                        long diff =
                                Math.abs(area - targetArea);

                        if (diff < bestDiff) {
                            chosen = size;
                            bestDiff = diff;
                        }
                    }
                }
            }

            if (imageReader != null) {
                imageReader.close();
            }

            imageReader =
                    android.media.ImageReader.newInstance(
                            chosen.getWidth(),
                            chosen.getHeight(),
                            android.graphics.ImageFormat.JPEG,
                            2
                    );

            imageReader.setOnImageAvailableListener(
                    reader -> {

                        android.media.Image image = null;

                        try {
                            image = reader.acquireLatestImage();

                            if (
                                    image == null ||
                                    photoCaptureTarget == null
                            ) {
                                return;
                            }

                            java.nio.ByteBuffer buffer =
                                    image.getPlanes()[0]
                                            .getBuffer();

                            byte[] bytes =
                                    new byte[buffer.remaining()];

                            buffer.get(bytes);

                            try (
                                    java.io.FileOutputStream out =
                                            new java.io.FileOutputStream(
                                                    photoCaptureTarget
                                            )
                            ) {
                                out.write(bytes);
                            }

                        } catch (Exception ignored) {

                        } finally {

                            if (image != null) {
                                image.close();
                            }

                            photoCaptureTarget = null;
                        }
                    },
                    cameraHandler
            );

            if (
                    android.os.Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(
                            android.Manifest.permission.CAMERA
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return;
            }

            manager.openCamera(
                    cameraId,
                    new android.hardware.camera2.CameraDevice.StateCallback() {

                        @Override
                        public void onOpened(
                                android.hardware.camera2.CameraDevice camera
                        ) {
                            cameraDevice = camera;

                            try {
                                java.util.ArrayList<android.view.Surface>
                                        surfaces =
                                        new java.util.ArrayList<>();

                                surfaces.add(
                                        imageReader.getSurface()
                                );

                                camera.createCaptureSession(
                                        surfaces,
                                        new android.hardware.camera2
                                                .CameraCaptureSession
                                                .StateCallback() {

                                            @Override
                                            public void onConfigured(
                                                    android.hardware.camera2
                                                            .CameraCaptureSession
                                                            session
                                            ) {
                                                cameraSession = session;
                                                cameraReady = true;
                                            }

                                            @Override
                                            public void onConfigureFailed(
                                                    android.hardware.camera2
                                                            .CameraCaptureSession
                                                            session
                                            ) {
                                                cameraReady = false;
                                            }
                                        },
                                        cameraHandler
                                );

                            } catch (Exception ignored) {
                                cameraReady = false;
                            }
                        }

                        @Override
                        public void onDisconnected(
                                android.hardware.camera2.CameraDevice camera
                        ) {
                            cameraReady = false;
                            camera.close();
                            cameraDevice = null;
                        }

                        @Override
                        public void onError(
                                android.hardware.camera2.CameraDevice camera,
                                int error
                        ) {
                            cameraReady = false;
                            camera.close();
                            cameraDevice = null;
                        }
                    },
                    cameraHandler
            );

        } catch (Exception ignored) {
            cameraReady = false;
        }
    }

    private void captureEventPhoto() {

        eventPhotoFileName = null;

        if (
                !cameraReady ||
                cameraDevice == null ||
                cameraSession == null ||
                imageReader == null
        ) {
            return;
        }

        try {

            java.io.File dir =
                    new java.io.File(
                            getExternalFilesDir(null),
                            "VibrationData"
                    );

            if (!dir.exists()) {
                dir.mkdirs();
            }

            eventPhotoFileName =
                    "photo_" +
                    new java.text.SimpleDateFormat(
                            "yyyyMMdd_HHmmss_SSS",
                            Locale.US
                    ).format(new java.util.Date()) +
                    ".jpg";

            photoCaptureTarget =
                    new java.io.File(
                            dir,
                            eventPhotoFileName
                    );

            android.hardware.camera2.CaptureRequest.Builder builder =
                    cameraDevice.createCaptureRequest(
                            android.hardware.camera2.CameraDevice
                                    .TEMPLATE_STILL_CAPTURE
                    );

            builder.addTarget(
                    imageReader.getSurface()
            );

            builder.set(
                    android.hardware.camera2.CaptureRequest
                            .CONTROL_AF_MODE,
                    android.hardware.camera2.CaptureRequest
                            .CONTROL_AF_MODE_CONTINUOUS_PICTURE
            );

            cameraSession.capture(
                    builder.build(),
                    null,
                    cameraHandler
            );

        } catch (Exception e) {
            eventPhotoFileName = null;
            photoCaptureTarget = null;
        }
    }

    private void closeEventCamera() {

        cameraReady = false;

        try {
            if (cameraSession != null) {
                cameraSession.close();
            }
        } catch (Exception ignored) {}

        cameraSession = null;

        try {
            if (cameraDevice != null) {
                cameraDevice.close();
            }
        } catch (Exception ignored) {}

        cameraDevice = null;

        try {
            if (imageReader != null) {
                imageReader.close();
            }
        } catch (Exception ignored) {}

        imageReader = null;

        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }
    }

    private void setupLocation() {

        locationManager =
                (android.location.LocationManager)
                        getSystemService(LOCATION_SERVICE);

        locationListener =
                new android.location.LocationListener() {
                    @Override
                    public void onLocationChanged(android.location.Location location) {
                        updateLocation(location);
                    }
                };

        if (
                android.os.Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                    new String[] {
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    1001
            );

            locationText.setText("GPS : 위치 권한 허용 필요");
        } else {
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {

        if (locationManager == null) return;

        if (
                android.os.Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }

        try {
            android.location.Location last = null;

            if (locationManager.isProviderEnabled(
                    android.location.LocationManager.GPS_PROVIDER)) {

                locationManager.requestLocationUpdates(
                        android.location.LocationManager.GPS_PROVIDER,
                        1000L,
                        0.5f,
                        locationListener
                );

                last = locationManager.getLastKnownLocation(
                        android.location.LocationManager.GPS_PROVIDER
                );
            }

            if (locationManager.isProviderEnabled(
                    android.location.LocationManager.NETWORK_PROVIDER)) {

                locationManager.requestLocationUpdates(
                        android.location.LocationManager.NETWORK_PROVIDER,
                        1000L,
                        0.5f,
                        locationListener
                );

                if (last == null) {
                    last = locationManager.getLastKnownLocation(
                            android.location.LocationManager.NETWORK_PROVIDER
                    );
                }
            }

            if (last != null) {
                updateLocation(last);
            } else {
                locationText.setText("GPS : 위치 신호 검색 중...");
            }

        } catch (Exception e) {
            locationText.setText("GPS : 위치 확인 오류");
        }
    }

    private void updateLocation(android.location.Location location) {

        if (location == null) return;

        currentLatitude = location.getLatitude();
        currentLongitude = location.getLongitude();
        hasLocation = true;

        locationText.setText(
                String.format(
                        Locale.US,
                        "GPS : %.6f, %.6f",
                        currentLatitude,
                        currentLongitude
                )
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == 1001) {
            if (
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                startLocationUpdates();
            } else {
                locationText.setText("GPS : 위치 권한 거부됨");
            }
        }

        if (requestCode == 1002) {
            if (
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                openEventCamera();
            } else {
                android.widget.Toast.makeText(
                        this,
                        "사진 자동 촬영을 사용하려면 카메라 권한이 필요합니다.",
                        android.widget.Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void saveEventLocation(java.io.File csvFile) {

        if (csvFile == null) return;

        java.io.File meta =
                new java.io.File(
                        csvFile.getParentFile(),
                        csvFile.getName().replace(".csv", ".gps")
                );

        try (
                java.io.PrintWriter out =
                        new java.io.PrintWriter(
                                new java.io.FileWriter(meta)
                        )
        ) {
            out.println(
                    String.format(
                            Locale.US,
                            "spec=%.3f",
                            getThreshold()
                    )
            );

            if (eventHasLocation) {
                out.println(
                        String.format(
                                Locale.US,
                                "latitude=%.8f",
                                eventLatitude
                        )
                );
                out.println(
                        String.format(
                                Locale.US,
                                "longitude=%.8f",
                                eventLongitude
                        )
                );
            } else {
                out.println("latitude=");
                out.println("longitude=");
            }

            out.println(
                    "photo=" +
                    (eventPhotoFileName == null
                            ? ""
                            : eventPhotoFileName)
            );

        } catch (Exception ignored) {
        }
    }

    private TextView createValueText(String text) {

        TextView view = new TextView(this);

        view.setText(text);
        view.setTextSize(19);
        view.setTextColor(Color.DKGRAY);
        view.setPadding(10, 12, 10, 12);

        return view;
    }

    private void startMeasurement() {

        if (accelerometer == null) {

            alarmText.setText("센서 없음");
            alarmText.setTextColor(Color.RED);

            return;
        }

        measuring = true;

        sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
        );

        alarmText.setText("상태 : 측정 중");
        alarmText.setTextColor(Color.rgb(0, 130, 0));
    }

    private void stopMeasurement() {

        measuring = false;

        sensorManager.unregisterListener(this);

        alarmText.setText("상태 : 정지");
        alarmText.setTextColor(Color.DKGRAY);
    }

    private void resetValues() {

        sum = 0.0;
        count = 0;

        maxValue = 0.0;
        minValue = Double.MAX_VALUE;

        preBuffer.clear();
        eventBuffer.clear();

        eventRecording = false;
        eventCount = 0;

        graph.clear();

        currentText.setText(
                "현재값 : 0.000 m/s²"
        );

        avgText.setText(
                "평균값 : 0.000 m/s²"
        );

        maxText.setText(
                "최대값 : 0.000 m/s²"
        );

        minText.setText(
                "최소값 : 0.000 m/s²"
        );

        alarmText.setText("상태 : 정상");
        alarmText.setTextColor(Color.rgb(0, 130, 0));

        eventText.setText(
                "이벤트 : 0회\n최근 3초 데이터 대기 중"
        );
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (!measuring) {
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        /*
         * Low-pass filter로 중력 성분 추정
         */
        gravityX =
                ALPHA * gravityX +
                (1.0f - ALPHA) * x;

        gravityY =
                ALPHA * gravityY +
                (1.0f - ALPHA) * y;

        gravityZ =
                ALPHA * gravityZ +
                (1.0f - ALPHA) * z;

        /*
         * 중력 성분 제거
         */
        float linearX = x - gravityX;
        float linearY = y - gravityY;
        float linearZ = z - gravityZ;

        /*
         * 3축 합성 진동값
         */
        double vibration =
                Math.sqrt(
                        linearX * linearX +
                        linearY * linearY +
                        linearZ * linearZ
                );

        long now =
                SystemClock.elapsedRealtime();

        DataPoint point =
                new DataPoint(
                        now,
                        vibration,
                        linearX,
                        linearY,
                        linearZ
                );

        /*
         * 최근 3초 버퍼에 저장
         */
        preBuffer.addLast(point);

        while (
                !preBuffer.isEmpty() &&
                now - preBuffer.peekFirst().timeMs > 3000
        ) {
            preBuffer.removeFirst();
        }

        /*
         * 평균/최대/최소
         */
        sum += vibration;
        count++;

        if (vibration > maxValue) {
            maxValue = vibration;
        }

        if (vibration < minValue) {
            minValue = vibration;
        }

        double average = sum / count;

        currentText.setText(
                String.format(
                        Locale.US,
                        "현재값 : %.3f m/s²",
                        vibration
                )
        );

        avgText.setText(
                String.format(
                        Locale.US,
                        "평균값 : %.3f m/s²",
                        average
                )
        );

        maxText.setText(
                String.format(
                        Locale.US,
                        "최대값 : %.3f m/s²",
                        maxValue
                )
        );

        minText.setText(
                String.format(
                        Locale.US,
                        "최소값 : %.3f m/s²",
                        minValue
                )
        );

        double threshold = getThreshold();

        graph.setThreshold(threshold);
        graph.addValue(vibration);

        /*
         * SPEC 초과 이벤트 시작
         */
        if (
                vibration >= threshold &&
                !eventRecording
        ) {

            eventRecording = true;
            eventStartMs = now;
            lastThresholdExceededTime = now;

            eventLatitude = currentLatitude;
            eventLongitude = currentLongitude;
            eventHasLocation = hasLocation;

            // SPEC 최초 초과 순간 자동 사진 촬영
            captureEventPhoto();

            eventBuffer.clear();

            /*
             * 초과 전 최근 3초 데이터 복사
             */
            eventBuffer.addAll(preBuffer);

            eventCount++;

            alarmText.setText(
                    String.format(
                            Locale.US,
                            "⚠ SPEC 초과 : %.3f",
                            vibration
                    )
            );

            alarmText.setTextColor(Color.RED);

            eventText.setText(
                    "이벤트 : " + eventCount +
                    "회\n초과 후 3초 데이터 기록 중..."
            );
        }

        /*
         * SPEC 초과 후 3초 데이터
         */
        if (eventRecording) {

            if (
                    eventBuffer.isEmpty() ||
                    eventBuffer.get(eventBuffer.size() - 1).timeMs
                            != point.timeMs
            ) {
                eventBuffer.add(point);
            }

            if (point.value >= threshold) {
                lastThresholdExceededTime = now;
            }

            long elapsed =
                    now - lastThresholdExceededTime;

            if (elapsed >= 3000) {

                eventRecording = false;

                    java.util.ArrayList<Float> csvValues = new java.util.ArrayList<>();
                    for (DataPoint dp : eventBuffer) csvValues.add((float) dp.value);
                    java.io.File csvFile = CsvSaver.save(this, csvValues);
                    saveEventLocation(csvFile);

                eventText.setText(
                        "이벤트 : " + eventCount +
                        "회\n최근 이벤트 데이터 : " +
                        eventBuffer.size() +
                        "개 확보 완료"
                );

                alarmText.setText(
                        "상태 : 이벤트 기록 완료"
                );

                alarmText.setTextColor(
                        Color.rgb(200, 100, 0)
                );
            }

        } else {

            if (vibration < threshold) {

                alarmText.setText(
                        "상태 : 정상"
                );

                alarmText.setTextColor(
                        Color.rgb(0, 130, 0)
                );
            }
        }
    }

    private double getThreshold() {

        try {

            return Double.parseDouble(
                    thresholdInput
                            .getText()
                            .toString()
                            .trim()
            );

        } catch (Exception e) {

            return getSharedPreferences("VibrationSettings", MODE_PRIVATE)
                    .getFloat("spec", 2.0f);
        }
    }

    @Override
    public void onAccuracyChanged(
            Sensor sensor,
            int accuracy
    ) {
    }

    @Override
    protected void onPause() {

        super.onPause();

        if (measuring) {

            sensorManager.unregisterListener(this);
        }

        closeEventCamera();
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (
                measuring &&
                accelerometer != null
        ) {

            sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME
            );
        }

        if (
                android.os.Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(
                        android.Manifest.permission.CAMERA
                ) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            openEventCamera();
        }
    }

    /*
     * 데이터 1개
     */
    static class DataPoint {

        long timeMs;

        double value;

        float x;
        float y;
        float z;

        DataPoint(
                long timeMs,
                double value,
                float x,
                float y,
                float z
        ) {

            this.timeMs = timeMs;
            this.value = value;

            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /*
     * 실시간 그래프
     */

    private void showCsvGraph(java.io.File file) {
        java.util.ArrayList<Double> data = new java.util.ArrayList<>();

        try {
            java.io.BufferedReader br =
                    new java.io.BufferedReader(new java.io.FileReader(file));

            String line;
            while ((line = br.readLine()) != null) {
                String[] a = line.split(",");
                if (a.length >= 2) {
                    try {
                        data.add(Double.parseDouble(a[1].trim()));
                    } catch (Exception ignored) {}
                }
            }
            br.close();
        } catch (Exception e) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("CSV 읽기 오류")
                    .setMessage(e.getMessage())
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }

        if (data.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle(file.getName())
                    .setMessage("표시할 데이터가 없습니다.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }

        double sum = 0;
        double max = -Double.MAX_VALUE;
        double min = Double.MAX_VALUE;

        for (double v : data) {
            sum += v;
            if (v > max) max = v;
            if (v < min) min = v;
        }

        double avg = sum / data.size();

        android.widget.LinearLayout box =
                new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);

        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        android.widget.TextView info =
                new android.widget.TextView(this);

        info.setText(
                "파일 : " + file.getName() +
                "\n데이터 수 : " + data.size() + "개" +
                String.format(java.util.Locale.US,
                        "\n평균값 : %.3f m/s²", avg) +
                String.format(java.util.Locale.US,
                        "\n최대값 : %.3f m/s²", max) +
                String.format(java.util.Locale.US,
                        "\n최소값 : %.3f m/s²", min) +
                String.format(java.util.Locale.US,
                        "\nSPEC : %.2f m/s²", getThreshold())
        );

        info.setTextSize(17);
        info.setPadding(0, 0, 0, pad);
        box.addView(info);

        VibrationGraph csvGraph = new VibrationGraph(this);
        csvGraph.setThreshold(getThreshold());

        for (double v : data) {
            csvGraph.addValue(v);
        }

        int h = (int)(300 * getResources().getDisplayMetrics().density);

        csvGraph.setLayoutParams(
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        h
                )
        );

        box.addView(csvGraph);

        new android.app.AlertDialog.Builder(this)
                .setTitle("저장 진동 데이터")
                .setView(box)
                .setPositiveButton("닫기", null)
                .show();
    }

    static class VibrationGraph extends View {

        private final ArrayList<Double> values =
                new ArrayList<>();

        private final Paint graphPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint thresholdPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint gridPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint textPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private double threshold = 2.0;

        private static final int MAX_POINTS = 220;

        public VibrationGraph(
                android.content.Context context
        ) {

            super(context);

            graphPaint.setColor(
                    Color.rgb(0, 100, 220)
            );

            graphPaint.setStrokeWidth(4f);

            graphPaint.setStyle(
                    Paint.Style.STROKE
            );

            thresholdPaint.setColor(Color.RED);
            thresholdPaint.setStrokeWidth(3f);

            gridPaint.setColor(
                    Color.rgb(220, 220, 220)
            );

            gridPaint.setStrokeWidth(1f);

            textPaint.setColor(Color.DKGRAY);
            textPaint.setTextSize(30f);

            setBackgroundColor(
                    Color.rgb(248, 248, 248)
            );
        }

        public void addValue(double value) {

            values.add(value);

            if (values.size() > MAX_POINTS) {

                values.remove(0);
            }

            invalidate();
        }

        public void setThreshold(double value) {

            threshold = value;
        }

        public void clear() {

            values.clear();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            int width = getWidth();
            int height = getHeight();

            int left = 50;
            int right = width - 20;
            int top = 40;
            int bottom = height - 45;

            /*
             * 표시 Y 최대값 자동 계산
             */
            double maxY = Math.max(
                    threshold * 1.5,
                    3.0
            );

            for (double v : values) {

                if (v > maxY) {
                    maxY = v * 1.2;
                }
            }

            /*
             * 가로 Grid
             */
            for (int i = 0; i <= 4; i++) {

                float y =
                        top +
                        (bottom - top) *
                        i / 4f;

                canvas.drawLine(
                        left,
                        y,
                        right,
                        y,
                        gridPaint
                );
            }

            /*
             * Threshold 선
             */
            float thresholdY =
                    (float)(
                            bottom -
                            (threshold / maxY) *
                            (bottom - top)
                    );

            canvas.drawLine(
                    left,
                    thresholdY,
                    right,
                    thresholdY,
                    thresholdPaint
            );

            canvas.drawText(
                    String.format(
                            Locale.US,
                            "SPEC %.2f",
                            threshold
                    ),
                    left + 10,
                    thresholdY - 8,
                    textPaint
            );

            /*
             * 그래프
             */
            if (values.size() >= 2) {

                float stepX =
                        (right - left) /
                        (float)(MAX_POINTS - 1);

                int startIndex =
                        MAX_POINTS -
                        values.size();

                float previousX = 0;
                float previousY = 0;

                for (
                        int i = 0;
                        i < values.size();
                        i++
                ) {

                    double value =
                            values.get(i);

                    float x =
                            left +
                            (startIndex + i) *
                            stepX;

                    float y =
                            (float)(
                                    bottom -
                                    (value / maxY) *
                                    (bottom - top)
                            );

                    if (y < top) {
                        y = top;
                    }

                    if (i > 0) {

                        canvas.drawLine(
                                previousX,
                                previousY,
                                x,
                                y,
                                graphPaint
                        );
                    }

                    previousX = x;
                    previousY = y;
                }
            }

            canvas.drawText(
                    String.format(
                            Locale.US,
                            "%.1f",
                            maxY
                    ),
                    3,
                    top + 25,
                    textPaint
            );

            canvas.drawText(
                    "0",
                    15,
                    bottom,
                    textPaint
            );
        }
    }
}
