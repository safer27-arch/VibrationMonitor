
package com.example.vibrationmonitor;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
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
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private TextView currentText;
    private TextView avgText;
    private TextView maxText;
    private TextView minText;
    private TextView alarmText;

    private EditText thresholdInput;

    private boolean measuring = false;

    private double sum = 0.0;
    private long count = 0;

    private double maxValue = 0.0;
    private double minValue = Double.MAX_VALUE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);
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

        currentText = createValueText("현재값 : 0.000 m/s²");
        avgText = createValueText("평균값 : 0.000 m/s²");
        maxText = createValueText("최대값 : 0.000 m/s²");
        minText = createValueText("최소값 : 0.000 m/s²");

        TextView thresholdLabel = createValueText("SPEC / Threshold");

        thresholdInput = new EditText(this);
        thresholdInput.setText("2.0");
        thresholdInput.setHint("예: 2.0");
        thresholdInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        alarmText = new TextView(this);
        alarmText.setText("상태 : 정상");
        alarmText.setTextSize(22);
        alarmText.setGravity(Gravity.CENTER);
        alarmText.setTextColor(Color.rgb(0, 120, 0));
        alarmText.setPadding(10, 30, 10, 30);

        Button startButton = new Button(this);
        startButton.setText("측정 시작");

        Button stopButton = new Button(this);
        stopButton.setText("측정 중지");

        Button resetButton = new Button(this);
        resetButton.setText("값 초기화");

        startButton.setOnClickListener(v -> startMeasurement());
        stopButton.setOnClickListener(v -> stopMeasurement());
        resetButton.setOnClickListener(v -> resetValues());

        root.addView(title);
        root.addView(sensorStatus);
        root.addView(currentText);
        root.addView(avgText);
        root.addView(maxText);
        root.addView(minText);
        root.addView(thresholdLabel);
        root.addView(thresholdInput);
        root.addView(alarmText);
        root.addView(startButton);
        root.addView(stopButton);
        root.addView(resetButton);

        setContentView(root);
    }

    private TextView createValueText(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(20);
        v.setTextColor(Color.DKGRAY);
        v.setPadding(10, 20, 10, 20);
        return v;
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
        alarmText.setTextColor(Color.rgb(0, 120, 0));
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

        currentText.setText("현재값 : 0.000 m/s²");
        avgText.setText("평균값 : 0.000 m/s²");
        maxText.setText("최대값 : 0.000 m/s²");
        minText.setText("최소값 : 0.000 m/s²");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!measuring) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double magnitude = Math.sqrt(x * x + y * y + z * z);

        // 중력 1g(약 9.80665 m/s²)을 제거한 단순 진동값
        double vibration = Math.abs(magnitude - 9.80665);

        sum += vibration;
        count++;

        if (vibration > maxValue) {
            maxValue = vibration;
        }

        if (vibration < minValue) {
            minValue = vibration;
        }

        double average = sum / count;

        currentText.setText(String.format(
                Locale.US,
                "현재값 : %.3f m/s²",
                vibration
        ));

        avgText.setText(String.format(
                Locale.US,
                "평균값 : %.3f m/s²",
                average
        ));

        maxText.setText(String.format(
                Locale.US,
                "최대값 : %.3f m/s²",
                maxValue
        ));

        minText.setText(String.format(
                Locale.US,
                "최소값 : %.3f m/s²",
                minValue
        ));

        double threshold = getThreshold();

        if (vibration >= threshold) {
            alarmText.setText(
                    String.format(
                            Locale.US,
                            "⚠ SPEC 초과 : %.3f",
                            vibration
                    )
            );
            alarmText.setTextColor(Color.RED);
        } else {
            alarmText.setText("상태 : 정상");
            alarmText.setTextColor(Color.rgb(0, 120, 0));
        }
    }

    private double getThreshold() {
        try {
            return Double.parseDouble(
                    thresholdInput.getText().toString().trim()
            );
        } catch (Exception e) {
            return 2.0;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (measuring) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (measuring && accelerometer != null) {
            sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME
            );
        }
    }
}
